package com.sanedge.pointofsale.common.outbox;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * Persistence for the shared {@code outbox} table.
 *
 * <p>Lease semantics (same contract as the ecommerce outboxes):
 * <ul>
 *   <li>{@code findDue} returns PENDING rows whose backoff has elapsed and whose
 *       lease has expired, oldest first, capped at {@code limit}.</li>
 *   <li>{@code claim} atomically takes a lease and increments {@code attempts},
 *       returning {@code null} when another publisher won the race.</li>
 *   <li>{@code markSent} only succeeds while this publisher still holds the lease.</li>
 *   <li>{@code markRetry} releases the lease and defers the next attempt.</li>
 * </ul>
 *
 * <p>Every method takes the set of domains ({@code aggregateType}) the calling
 * service owns, so a service never publishes another domain's events even though
 * all services share one database.
 */
@Repository
public class OutboxRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Persists a new PENDING event; call inside the business transaction. */
    @Transactional
    public void enqueue(Outbox event) {
        entityManager.persist(event);
    }

    /**
     * Rows ready to publish, oldest first.
     *
     * @param limit   maximum number of rows
     * @param domains domains this publisher owns; empty means "every domain"
     * @param leaseSeconds how long a claim is held before the row is released
     */
    @Transactional(readOnly = true)
    public List<Outbox> findDue(int limit, Collection<String> domains, long leaseSeconds) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp leaseCutoff = leaseCutoff(leaseSeconds);

        StringBuilder jpql = new StringBuilder("SELECT o FROM Outbox o ")
                .append("WHERE o.status = :status ")
                .append("AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now) ")
                .append("AND (o.claimedAt IS NULL OR o.claimedAt < :leaseCutoff) ");
        if (domains != null && !domains.isEmpty()) {
            jpql.append("AND o.aggregateType IN :domains ");
        }
        jpql.append("ORDER BY o.createdAt");

        var query = entityManager.createQuery(jpql.toString(), Outbox.class)
                .setParameter("status", OutboxStatus.PENDING)
                .setParameter("now", now)
                .setParameter("leaseCutoff", leaseCutoff);
        if (domains != null && !domains.isEmpty()) {
            query.setParameter("domains", domains);
        }
        return query.setMaxResults(Math.max(1, limit)).getResultList();
    }

    /**
     * Atomically leases one row: sets {@code claimedAt}/{@code claimToken} and
     * increments {@code attempts}.
     *
     * <p>The row is locked (pessimistic write) while the lease is taken, so two
     * publishers can never own the same row, and the state is re-read from the
     * database so a stale persistence context cannot hand out a lease twice.
     *
     * @return the claimed row, or {@code null} when another publisher owns it
     */
    @Transactional
    public Outbox claim(Outbox event, Collection<String> domains, long leaseSeconds) {
        Outbox current = entityManager.find(Outbox.class, event.getId());
        if (current == null) {
            return null;
        }
        entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp leaseCutoff = leaseCutoff(leaseSeconds);

        if (current.getStatus() != OutboxStatus.PENDING) {
            return null;
        }
        if (current.getClaimedAt() != null && !current.getClaimedAt().before(leaseCutoff)) {
            return null; // the lease is still held
        }
        if (current.getNextAttemptAt() != null && current.getNextAttemptAt().after(now)) {
            return null; // the backoff has not elapsed yet
        }
        if (domains != null && !domains.isEmpty() && !domains.contains(current.getAggregateType())) {
            return null; // owned by another service
        }

        current.setClaimedAt(now);
        current.setClaimToken(UUID.randomUUID().toString());
        current.setAttempts(current.getAttempts() == null ? 1 : current.getAttempts() + 1);
        entityManager.flush();

        return current;
    }

    /** Marks the event published; only the lease holder may do so. */
    @Transactional
    public void markSent(Outbox event) {
        Outbox current = entityManager.find(Outbox.class, event.getId());
        if (current == null || event.getClaimToken() == null) {
            return;
        }
        entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);

        if (current.getStatus() != OutboxStatus.PENDING
                || !event.getClaimToken().equals(current.getClaimToken())) {
            return; // the lease was lost to another publisher
        }

        current.setStatus(OutboxStatus.PROCESSED);
        current.setProcessedAt(Timestamp.valueOf(LocalDateTime.now()));
        current.setLastError(null);
        current.setClaimedAt(null);
        current.setClaimToken(null);
    }

    /**
     * Records a delivery failure: releases the lease, stores the error and
     * schedules the next attempt. Once {@code maxAttempts} is reached the row is
     * parked as {@code FAILED} (dead letter) instead of being retried forever.
     */
    @Transactional
    public void markRetry(Outbox event, Throwable failure, long delaySeconds, int maxAttempts) {
        Outbox current = entityManager.find(Outbox.class, event.getId());
        if (current == null || event.getClaimToken() == null) {
            return;
        }
        entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);

        if (current.getStatus() != OutboxStatus.PENDING
                || !event.getClaimToken().equals(current.getClaimToken())) {
            return; // the lease was lost to another publisher
        }

        int attempts = current.getAttempts() == null ? 1 : current.getAttempts();
        current.setAttempts(attempts);
        current.setLastError(failure == null ? "unknown failure" : failure.getMessage());
        current.setClaimedAt(null);
        current.setClaimToken(null);

        if (maxAttempts > 0 && attempts >= maxAttempts) {
            current.setStatus(OutboxStatus.FAILED);
            current.setProcessedAt(Timestamp.valueOf(LocalDateTime.now()));
        } else {
            current.setNextAttemptAt(Timestamp.valueOf(LocalDateTime.now().plusSeconds(Math.max(0, delaySeconds))));
        }
    }

    /** Backlog depth for the backlog gauge / tests. */
    @Transactional(readOnly = true)
    public long countPending() {
        return entityManager.createQuery(
                "SELECT COUNT(o) FROM Outbox o WHERE o.status = :status", Long.class)
                .setParameter("status", OutboxStatus.PENDING)
                .getSingleResult();
    }

    /** Backlog depth restricted to the domains a service owns. */
    @Transactional(readOnly = true)
    public long countPending(Collection<String> domains) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(o) FROM Outbox o WHERE o.status = :status ");
        if (domains != null && !domains.isEmpty()) {
            jpql.append("AND o.aggregateType IN :domains ");
        }

        var query = entityManager.createQuery(jpql.toString(), Long.class)
                .setParameter("status", OutboxStatus.PENDING);
        if (domains != null && !domains.isEmpty()) {
            query.setParameter("domains", domains);
        }
        return query.getSingleResult();
    }

    private Timestamp leaseCutoff(long leaseSeconds) {
        return Timestamp.valueOf(LocalDateTime.now().minusSeconds(Math.max(1, leaseSeconds)));
    }
}

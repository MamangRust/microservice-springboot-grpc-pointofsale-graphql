package com.sanedge.pointofsale.order.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.sanedge.pointofsale.common.outbox.Outbox;
import com.sanedge.pointofsale.common.outbox.OutboxRepository;
import com.sanedge.pointofsale.common.outbox.OutboxStatus;
import com.sanedge.pointofsale.order.models.Order;

import jakarta.persistence.EntityManager;

/**
 * Repository level tests for the shared outbox table: domain scoping, lease
 * handling and the retry path.
 *
 * <p>The outbox entity/repository live in {@code common} but the outbox schema is
 * owned by the order service, so the test boots a minimal JPA slice against an
 * in-memory database.
 */
@DataJpaTest
@EntityScan(basePackageClasses = { Outbox.class, Order.class })
@Import(OutboxRepository.class)
@Transactional
class OutboxRepositoryTest {

    private static final Set<String> ORDER_DOMAINS = Set.of("ORDER", "ORDER_ITEM");

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private EntityManager entityManager;

    private Outbox row(String domain, String topic) {
        Outbox event = new Outbox();
        event.setAggregateType(domain);
        event.setAggregateId("1");
        event.setTopic(topic);
        event.setPayload("{}");
        event.setStatus(OutboxStatus.PENDING);
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    @Test
    void findDueOnlyReturnsTheDomainsOwnedByTheService() {
        row("ORDER", "stats.pointofsale.order.event");
        row("ORDER_ITEM", "stats.pointofsale.order_item.event");
        row("TRANSACTION", "stats.transaction.event");

        List<Outbox> due = repository.findDue(10, ORDER_DOMAINS, 300);

        assertThat(due).hasSize(2);
        assertThat(due).extracting(Outbox::getAggregateType)
                .containsExactlyInAnyOrder("ORDER", "ORDER_ITEM");
    }

    @Test
    void findDueSkipsRowsThatAreStillLeased() {
        Outbox event = row("ORDER", "stats.pointofsale.order.event");
        repository.claim(event, ORDER_DOMAINS, 300);

        assertThat(repository.findDue(10, ORDER_DOMAINS, 300)).isEmpty();
    }

    @Test
    void claimTakesTheLeaseAndIncrementsAttempts() {
        Outbox event = row("ORDER", "stats.pointofsale.order.event");

        Outbox claimed = repository.claim(event, ORDER_DOMAINS, 300);

        assertThat(claimed).isNotNull();
        assertThat(claimed.getClaimToken()).isNotBlank();
        assertThat(claimed.getClaimedAt()).isNotNull();
        assertThat(claimed.getAttempts()).isEqualTo(1);
    }

    @Test
    void claimFailsWhenAnotherPublisherHoldsTheLease() {
        Outbox event = row("ORDER", "stats.pointofsale.order.event");
        assertThat(repository.claim(event, ORDER_DOMAINS, 300)).isNotNull();

        assertThat(repository.claim(event, ORDER_DOMAINS, 300)).isNull();
    }

    @Test
    void anExpiredLeaseMakesTheRowClaimableAgain() {
        Outbox event = row("ORDER", "stats.pointofsale.order.event");
        repository.claim(event, ORDER_DOMAINS, 300);

        // simulate a publisher that died: the lease is older than its duration
        event.setClaimedAt(Timestamp.valueOf(LocalDateTime.now().minusSeconds(600)));
        event.setClaimToken("stale-token");
        entityManager.merge(event);
        entityManager.flush();

        assertThat(repository.findDue(10, ORDER_DOMAINS, 300)).hasSize(1);
        assertThat(repository.claim(event, ORDER_DOMAINS, 300)).isNotNull();
    }

    @Test
    void markSentProcessesTheRow() {
        Outbox event = row("ORDER", "stats.pointofsale.order.event");
        Outbox claimed = repository.claim(event, ORDER_DOMAINS, 300);

        repository.markSent(claimed);

        entityManager.flush();
        entityManager.clear();
        Outbox stored = entityManager.find(Outbox.class, event.getId());
        assertThat(stored.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(stored.getProcessedAt()).isNotNull();
        assertThat(stored.getClaimToken()).isNull();
    }

    @Test
    void markRetryReleasesTheLeaseAndSchedulesTheNextAttempt() {
        Outbox event = row("ORDER", "stats.pointofsale.order.event");
        Outbox claimed = repository.claim(event, ORDER_DOMAINS, 300);

        repository.markRetry(claimed, new IllegalStateException("broker down"), 60, 5);

        entityManager.flush();
        entityManager.clear();
        Outbox stored = entityManager.find(Outbox.class, event.getId());
        assertThat(stored.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(stored.getClaimToken()).isNull();
        assertThat(stored.getClaimedAt()).isNull();
        assertThat(stored.getLastError()).isEqualTo("broker down");
        assertThat(stored.getNextAttemptAt()).isAfter(Timestamp.valueOf(LocalDateTime.now()));

        // the row is not due until the backoff elapsed
        assertThat(repository.findDue(10, ORDER_DOMAINS, 300)).isEmpty();
    }

    @Test
    void markRetryParksTheRowWhenTheAttemptsAreExhausted() {
        Outbox event = row("ORDER", "stats.pointofsale.order.event");
        Outbox claimed = repository.claim(event, ORDER_DOMAINS, 300);

        repository.markRetry(claimed, new IllegalStateException("broker down"), 60, 1);

        entityManager.flush();
        entityManager.clear();
        Outbox stored = entityManager.find(Outbox.class, event.getId());
        assertThat(stored.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void countPendingIsScopedToTheOwnedDomains() {
        row("ORDER", "stats.pointofsale.order.event");
        row("TRANSACTION", "stats.transaction.event");

        assertThat(repository.countPending(ORDER_DOMAINS)).isEqualTo(1);
        assertThat(repository.countPending()).isEqualTo(2);
    }
}

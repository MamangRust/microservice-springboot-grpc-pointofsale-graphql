package com.sanedge.pointofsale.common.outbox;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transactional outbox row (shared {@code outbox} table, migration V9).
 *
 * <p>All modules write the same table and are distinguished by
 * {@code aggregateType} (the domain: {@code ORDER}, {@code ORDER_ITEM},
 * {@code TRANSACTION}, {@code USER}, {@code MERCHANT}); the publisher drains only
 * the domains its own service owns, so several services can share one database
 * without publishing each other's events.
 *
 * <p>Rows are published with a lease ({@code claimedAt} + {@code claimToken}):
 * a publisher takes the row before sending it, and the lease is released when it
 * expires, so two replicas of the same service never publish the same event.
 *
 * <p>Does not extend {@code BaseModel}: the V9 table has only {@code created_at}
 * (no {@code updated_at}/{@code deleted_at}), matching the blueprint entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outbox")
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Logical producer, e.g. {@code USER} or {@code TRANSACTION}. */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /** Kafka record key. */
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 200)
    private String topic;

    /** Kafka record key (recipient email for the email flows, aggregate id otherwise). */
    @Column(name = "event_key", length = 200)
    private String eventKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "processed_at")
    private Timestamp processedAt;

    /** Earliest time the row may be published again (exponential backoff). */
    @Column(name = "next_attempt_at")
    private Timestamp nextAttemptAt;

    /** Start of the current lease; the row becomes claimable again once it expires. */
    @Column(name = "claimed_at")
    private Timestamp claimedAt;

    /** Token identifying the publisher that owns the current lease. */
    @Column(name = "claim_token", length = 100)
    private String claimToken;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = new Timestamp(System.currentTimeMillis());
        }
    }
}

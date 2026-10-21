package com.sanedge.pointofsale.common.outbox;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sanedge.pointofsale.common.chaos.ChaosManager;
import com.sanedge.pointofsale.common.chaos.ChaosPolicy;

/**
 * Drains the shared {@code outbox} table into Kafka.
 *
 * <p>Two properties make it safe to run several replicas against the same
 * database:
 * <ul>
 *   <li><b>Lease</b> — a row is claimed (lease token + timestamp) before it is
 *       sent, so only one publisher owns it; if that publisher dies the lease
 *       expires and another one picks the row up.</li>
 *   <li><b>Domain scoping</b> — the publisher only claims the domains listed in
 *       {@code outbox.publisher.domains} (the {@code aggregateType} values the
 *       service owns). Services sharing one database therefore never publish
 *       each other's events.</li>
 * </ul>
 *
 * <p>Failed sends are retried with exponential backoff
 * ({@code min(300s, 1 << (attempts - 1))}); once {@code outbox.publisher.max-attempts}
 * is reached the row is parked as {@code FAILED} (dead letter).
 *
 * <p>Gated on {@link KafkaTemplate} so non-producer modules do not load this bean.
 * Its {@code KafkaTemplate} field would otherwise be introspected by Spring on
 * every scan of this package, failing the whole context with
 * {@code NoClassDefFoundError} in modules that lack spring-kafka.
 */
@Component
@ConditionalOnClass(KafkaTemplate.class)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final long MAX_BACKOFF_SECONDS = 300L;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ChaosManager chaosManager;

    @Value("${outbox.publisher.batch-size:50}")
    private int batchSize;

    @Value("${outbox.publisher.max-attempts:5}")
    private int maxAttempts;

    @Value("${outbox.publisher.lease-seconds:300}")
    private long leaseSeconds;

    /** Domains owned by this service; empty means "claim every domain". */
    @Value("${outbox.publisher.domains:}")
    private String domains;

    private volatile boolean running;

    public OutboxPublisher(OutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            ChaosManager chaosManager) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.chaosManager = chaosManager;
    }

    /** Default interval matches the blueprint's 5000 ms. */
    @Scheduled(fixedDelayString = "${outbox.publisher.interval-ms:5000}",
            initialDelayString = "${outbox.publisher.initial-delay-ms:5000}")
    public void poll() {
        if (running) {
            return; // previous tick still running
        }
        running = true;
        try {
            drain();
        } catch (RuntimeException e) {
            log.error("❌ Outbox publisher tick failed: {}", e.getMessage(), e);
        } finally {
            running = false;
        }
    }

    /** Fetches due rows and publishes them sequentially. */
    void drain() {
        List<Outbox> due = repository.findDue(Math.max(1, batchSize), ownedDomains(), leaseSeconds);
        for (Outbox event : due) {
            publishOne(event);
        }
    }

    private void publishOne(Outbox event) {
        Outbox claimed = repository.claim(event, ownedDomains(), leaseSeconds);
        if (claimed == null) {
            return; // another publisher owns this row
        }

        try {
            send(claimed);
            repository.markSent(claimed);
            log.debug("📤 Outbox published | id={} topic={} key={}",
                    claimed.getId(), claimed.getTopic(), claimed.getAggregateId());
        } catch (RuntimeException error) {
            log.warn("⚠️ Outbox publish failed | id={} topic={} error={}",
                    claimed.getId(), claimed.getTopic(), error.getMessage());
            repository.markRetry(claimed, error, retryDelaySeconds(claimed.getAttempts()), maxAttempts);
        }
    }

    /** {@code min(300s, 1 << (attempts - 1))}. */
    private long retryDelaySeconds(int attempts) {
        return Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(8, Math.max(0, attempts - 1)));
    }

    /** The domains this publisher is allowed to claim. */
    Set<String> ownedDomains() {
        if (domains == null || domains.isBlank()) {
            return Set.of();
        }
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(domains.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(parsed::add);
        return parsed;
    }

    /** Publishes the payload, honouring {@code kafka}-type chaos policies. */
    private void send(Outbox event) {
        ChaosPolicy policy = chaosManager == null ? null : chaosManager.evaluate("kafka", event.getTopic());
        if (policy != null && policy.isEnabled() && Math.random() < policy.getErrorChance()) {
            log.info("Injecting Kafka chaos policy={} topic={}", policy.getName(), event.getTopic());

            if (policy.getLatencyMs() > 0) {
                try {
                    Thread.sleep(policy.getLatencyMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (policy.isDropMessage()) {
                throw new IllegalStateException("Simulated Kafka drop for topic " + event.getTopic());
            }
            if (policy.isRejectMessage()) {
                throw new IllegalStateException(policy.getErrorMessage() != null
                        ? policy.getErrorMessage()
                        : "Simulated Kafka rejection for topic " + event.getTopic());
            }
        }

        kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).join();
    }
}

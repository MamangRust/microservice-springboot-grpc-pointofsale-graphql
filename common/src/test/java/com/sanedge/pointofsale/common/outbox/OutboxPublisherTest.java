package com.sanedge.pointofsale.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.sanedge.pointofsale.common.chaos.ChaosManager;

/**
 * Unit tests for the outbox relay: lease handling, domain scoping and the
 * retry path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxPublisherTest {

    @Mock
    private OutboxRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ChaosManager chaosManager;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, kafkaTemplate, chaosManager);
        ReflectionTestUtils.setField(publisher, "batchSize", 10);
        ReflectionTestUtils.setField(publisher, "leaseSeconds", 300L);
        ReflectionTestUtils.setField(publisher, "maxAttempts", 5);
        ReflectionTestUtils.setField(publisher, "domains", "ORDER,ORDER_ITEM");
    }

    private Outbox pendingRow(long id, String domain) {
        Outbox event = new Outbox();
        event.setId(id);
        event.setAggregateType(domain);
        event.setAggregateId(String.valueOf(id));
        event.setTopic("stats.pointofsale.order.event");
        event.setPayload("{}");
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        return event;
    }

    @Test
    void publishesDueRowsAndMarksThemSent() {
        Outbox due = pendingRow(1L, "ORDER");
        Outbox claimed = pendingRow(1L, "ORDER");
        claimed.setClaimToken("token-1");
        claimed.setAttempts(1);

        when(repository.findDue(eq(10), any(), anyLong())).thenReturn(List.of(due));
        when(repository.claim(eq(due), any(), anyLong())).thenReturn(claimed);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.drain();

        verify(kafkaTemplate).send("stats.pointofsale.order.event", "1", "{}");
        verify(repository).markSent(claimed);
        verify(repository, never()).markRetry(any(), any(), anyLong(), anyInt());
    }

    @Test
    void skipsRowsAnotherPublisherAlreadyClaimed() {
        Outbox due = pendingRow(2L, "ORDER");
        when(repository.findDue(eq(10), any(), anyLong())).thenReturn(List.of(due));
        when(repository.claim(eq(due), any(), anyLong())).thenReturn(null);

        publisher.drain();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(repository, never()).markSent(any());
    }

    @Test
    void retriesWithBackoffWhenKafkaFails() {
        Outbox due = pendingRow(3L, "ORDER");
        Outbox claimed = pendingRow(3L, "ORDER");
        claimed.setClaimToken("token-3");
        claimed.setAttempts(1);

        when(repository.findDue(eq(10), any(), anyLong())).thenReturn(List.of(due));
        when(repository.claim(eq(due), any(), anyLong())).thenReturn(claimed);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("broker down"));

        publisher.drain();

        ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
        verify(repository).markRetry(eq(claimed), failure.capture(), delay.capture(), eq(5));
        assertThat(failure.getValue()).hasMessage("broker down");
        // attempts = 1 -> first backoff is 1 second
        assertThat(delay.getValue()).isEqualTo(1L);
        verify(repository, never()).markSent(any());
    }

    @Test
    void claimsOnlyTheDomainsOwnedByThisService() {
        when(repository.findDue(anyInt(), any(), anyLong())).thenReturn(List.of());

        publisher.drain();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> domains = ArgumentCaptor.forClass(Set.class);
        verify(repository).findDue(anyInt(), domains.capture(), anyLong());
        assertThat(domains.getValue()).containsExactlyInAnyOrder("ORDER", "ORDER_ITEM");
    }

    @Test
    void withoutConfiguredDomainsEveryDomainIsClaimed() {
        ReflectionTestUtils.setField(publisher, "domains", "");
        when(repository.findDue(anyInt(), any(), anyLong())).thenReturn(List.of());

        publisher.drain();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> domains = ArgumentCaptor.forClass(Set.class);
        verify(repository).findDue(anyInt(), domains.capture(), anyLong());
        assertThat(domains.getValue()).isEmpty();
    }
}

package com.sanedge.pointofsale.statswriter.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code stats.*.event} topics and writes each event into ClickHouse.
 *
 * <p>Manual-ack: a poison message is acknowledged (not redelivered) after it is
 * logged, so a single bad payload cannot stall the partition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backfill.enabled", havingValue = "false", matchIfMissing = true)
public class StatsWriterConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StatsClickHouseWriter writer;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            writer.ensureTable();
        } catch (Exception e) {
            log.error("failed to ensure ClickHouse stats_revenue table: {}", e.getMessage());
        }
    }

    @KafkaListener(
            topicPattern = "stats\\..*\\.event",
            containerFactory = "statsListenerContainerFactory",
            groupId = "${app.kafka.stats.group-id:stats-writer-group}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String period = node.path("period").asText(node.path("createdAt").asText("")).length() >= 7
                    ? node.path("period").asText(node.path("createdAt").asText("")).substring(0, 7)
                    : node.path("period").asText("1970-01");
            double revenue = node.path("amount").asDouble(node.path("total").asDouble(0d));
            long merchantId = node.path("merchantId").asLong(node.path("merchant_id").asLong(0L));
            long productId = node.path("productId").asLong(node.path("product_id").asLong(0L));
            writer.write(period, revenue, merchantId, productId);
        } catch (Exception e) {
            log.error("failed to process stats event: {}", e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }
}

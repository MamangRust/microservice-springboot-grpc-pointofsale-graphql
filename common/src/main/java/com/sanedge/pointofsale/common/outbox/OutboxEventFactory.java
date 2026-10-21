package com.sanedge.pointofsale.common.outbox;

import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds the JSON envelope every email event must carry.
 *
 * <p>Identical contract to the ecommerce factory: the consumer
 * ({@code EmailService.hasValidEnvelope}) requires {@code event_id},
 * {@code schema_version == 1}, {@code event_type}, {@code email},
 * {@code subject}, {@code body}, plus {@code occurred_at} for observability.
 */
public final class OutboxEventFactory {

    /** Must match {@code EmailService.EVENT_SCHEMA_VERSION}. */
    public static final int SCHEMA_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OutboxEventFactory() {
    }

    /**
     * Builds the email envelope for {@code topic}.
     *
     * @return the serialized payload plus the generated {@code event_id}
     */
    public static Envelope email(String topic, String email, String subject, String body) {
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("subject", subject);
        payload.put("body", body);
        payload.put("event_id", eventId);
        payload.put("schema_version", SCHEMA_VERSION);
        payload.put("event_type", topic);
        payload.put("occurred_at", Instant.now().toString());

        try {
            return new Envelope(eventId, MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize outbox payload for topic " + topic, e);
        }
    }

    /** The generated event id plus its serialized JSON payload. */
    public record Envelope(String eventId, String payload) {
    }

    /**
     * Builds the analytics envelope published to {@code stats.<domain>.event}.
     *
     * <p>Consumed by {@code stats-writer} ({@code StatsWriterConsumer}) and written
     * into the ClickHouse {@code stats_revenue} table. Fields mirror what the
     * consumer reads: {@code period} (YYYY-MM), {@code amount}, {@code merchantId},
     * {@code productId}.
     *
     * @param topic      destination topic; also used as {@code event_type}
     * @param period     accounting period, formatted {@code YYYY-MM}
     * @param amount     revenue / volume for the period
     * @param merchantId merchant dimension id (0 if not applicable)
     * @param productId  product dimension id (0 if not applicable)
     * @return the serialized payload plus the generated {@code event_id}
     */
    public static Envelope stats(String topic, String period, double amount, long merchantId, long productId) {
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("period", period);
        payload.put("amount", amount);
        payload.put("merchantId", merchantId);
        payload.put("productId", productId);
        payload.put("event_id", eventId);
        payload.put("schema_version", SCHEMA_VERSION);
        payload.put("event_type", topic);
        payload.put("occurred_at", Instant.now().toString());

        try {
            return new Envelope(eventId, MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize stats payload for topic " + topic, e);
        }
    }

    /**
     * Builds the order analytics envelope published to
     * {@code stats.pointofsale.order.event}.
     *
     * <p>Carries the domain fields ({@code order_id}, {@code merchant_id},
     * {@code status}, {@code total_amount}) while still including the
     * compatibility fields ({@code period}, {@code amount}, {@code merchantId},
     * {@code productId}) that {@code StatsWriterConsumer} reads into the
     * ClickHouse {@code stats_revenue} table.
     *
     * @param topic       destination topic; also used as {@code event_type}
     * @param orderId      order primary key
     * @param merchantId   merchant primary key
     * @param status       order status (e.g. "completed")
     * @param totalAmount  order total price
     * @return the serialized payload plus the generated {@code event_id}
     */
    public static Envelope orderStats(String topic, long orderId, long merchantId, String status, double totalAmount) {
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_id", orderId);
        payload.put("merchant_id", merchantId);
        payload.put("status", status != null ? status : "");
        payload.put("total_amount", totalAmount);
        // Compatibility fields read by stats-writer consumer.
        payload.put("period", YearMonth.now().toString());
        payload.put("amount", totalAmount);
        payload.put("merchantId", merchantId);
        payload.put("productId", 0L);
        payload.put("event_id", eventId);
        payload.put("schema_version", SCHEMA_VERSION);
        payload.put("event_type", topic);
        payload.put("occurred_at", Instant.now().toString());

        try {
            return new Envelope(eventId, MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize order stats payload for topic " + topic, e);
        }
    }

    /**
     * Builds the order-item analytics envelope published to
     * {@code stats.pointofsale.order_item.event}.
     *
     * <p>Carries the per-line domain fields ({@code order_item_id},
     * {@code order_id}, {@code merchant_id}, {@code category_id},
     * {@code product_id}, {@code quantity}, {@code unit_price},
     * {@code subtotal}) plus the compatibility fields ({@code period},
     * {@code amount}, {@code merchantId}, {@code productId}) consumed by
     * {@code StatsWriterConsumer}.
     *
     * @param topic       destination topic; also used as {@code event_type}
     * @param orderItemId order item primary key
     * @param orderId     parent order id
     * @param merchantId  merchant primary key
     * @param categoryId  product category id
     * @param productId   product primary key
     * @param quantity    item quantity
     * @param unitPrice   item unit price
     * @param subtotal    item subtotal (quantity * unitPrice)
     * @return the serialized payload plus the generated {@code event_id}
     */
    public static Envelope orderItemStats(String topic, long orderItemId, long orderId, long merchantId,
            long categoryId, long productId, int quantity, double unitPrice, double subtotal) {
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_item_id", orderItemId);
        payload.put("order_id", orderId);
        payload.put("merchant_id", merchantId);
        payload.put("category_id", categoryId);
        payload.put("product_id", productId);
        payload.put("quantity", quantity);
        payload.put("unit_price", unitPrice);
        payload.put("subtotal", subtotal);
        // Compatibility fields read by stats-writer consumer.
        payload.put("period", YearMonth.now().toString());
        payload.put("amount", subtotal);
        payload.put("merchantId", merchantId);
        payload.put("productId", productId);
        payload.put("event_id", eventId);
        payload.put("schema_version", SCHEMA_VERSION);
        payload.put("event_type", topic);
        payload.put("occurred_at", Instant.now().toString());

        try {
            return new Envelope(eventId, MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize order-item stats payload for topic " + topic, e);
        }
    }
}

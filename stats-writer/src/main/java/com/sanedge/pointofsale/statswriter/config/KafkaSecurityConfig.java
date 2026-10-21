package com.sanedge.pointofsale.statswriter.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka client config builder local to the stats writer.
 *
 * <p>Reads the same {@code KAFKA_*} env vars as the other services. Plaintext
 * (no env vars) stays the local-dev default. Copied from the email worker so
 * this module stays self-contained (no gRPC/common dependency).
 */
public final class KafkaSecurityConfig {

    public static final String DEFAULT_ACKS = "all";

    private KafkaSecurityConfig() {
    }

    public static Map<String, Object> producer(String bootstrapServers, String acks, boolean idempotence) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.put("acks", acks);
        config.put("enable.idempotence", String.valueOf(idempotence));
        applySecurity(config);
        return config;
    }

    public static Map<String, Object> consumer(String bootstrapServers, String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("group.id", groupId);
        config.put("auto.offset.reset", "earliest");
        config.put("enable.auto.commit", "false");
        applySecurity(config);
        return config;
    }

    public static void applySecurity(Map<String, Object> config) {
        applySecurity(config, System.getenv());
    }

    public static void applySecurity(Map<String, Object> config, Map<String, String> env) {
        String protocol = env.getOrDefault("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT");
        if (protocol.isBlank() || "PLAINTEXT".equalsIgnoreCase(protocol)) {
            return;
        }
        config.put("security.protocol", protocol);
        if (protocol.toUpperCase().contains("SASL")) {
            config.put("sasl.mechanism", env.getOrDefault("KAFKA_SASL_MECHANISM", "SCRAM-SHA-256"));
            String jaas = env.getOrDefault("KAFKA_SASL_JAAS_CONFIG", "");
            if (!jaas.isBlank()) {
                config.put("sasl.jaas.config", jaas);
            }
        }
        if (protocol.toUpperCase().contains("SSL")) {
            putIfNotBlank(config, "ssl.truststore.location", env.getOrDefault("KAFKA_SSL_TRUSTSTORE_LOCATION", ""));
            putIfNotBlank(config, "ssl.truststore.password", env.getOrDefault("KAFKA_SSL_TRUSTSTORE_PASSWORD", ""));
            putIfNotBlank(config, "ssl.truststore.type", env.getOrDefault("KAFKA_SSL_TRUSTSTORE_TYPE", "JKS"));
            putIfNotBlank(config, "ssl.keystore.location", env.getOrDefault("KAFKA_SSL_KEYSTORE_LOCATION", ""));
            putIfNotBlank(config, "ssl.keystore.password", env.getOrDefault("KAFKA_SSL_KEYSTORE_PASSWORD", ""));
            putIfNotBlank(config, "ssl.key.password", env.getOrDefault("KAFKA_SSL_KEY_PASSWORD", ""));
        }
    }

    private static void putIfNotBlank(Map<String, Object> config, String key, String value) {
        if (value != null && !value.isBlank()) {
            config.put(key, value);
        }
    }
}

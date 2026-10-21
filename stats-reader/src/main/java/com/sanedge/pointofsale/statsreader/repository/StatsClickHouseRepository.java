package com.sanedge.pointofsale.statsreader.repository;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read model for the ClickHouse {@code stats_revenue} table, cached in Redis (TTL 300s).
 *
 * <p>Writers (stats-writer) insert one row per transaction event with
 * {@code metric='txn'}; the reader aggregates on demand.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StatsClickHouseRepository {

    private final JdbcTemplate clickHouseJdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Duration TTL = Duration.ofSeconds(300);

    public record StatRow(String period, long dimensionId, double value) {
    }

    private final RowMapper<StatRow> rowMapper = (rs, i) -> new StatRow(
            rs.getString("period"),
            rs.getLong("dimension_id"),
            rs.getDouble("value"));

    public List<StatRow> monthlyTotalRevenue() {
        return cached("stats:monthly", () -> clickHouseJdbcTemplate.query(
                "SELECT period, 0 AS dimension_id, SUM(revenue) AS value "
                        + "FROM stats_revenue WHERE metric = 'txn' GROUP BY period ORDER BY period",
                rowMapper));
    }

    public List<StatRow> yearlyTotalRevenue() {
        return cached("stats:yearly", () -> clickHouseJdbcTemplate.query(
                "SELECT substring(period, 1, 4) AS period, 0 AS dimension_id, SUM(revenue) AS value "
                        + "FROM stats_revenue WHERE metric = 'txn' GROUP BY substring(period, 1, 4) ORDER BY period",
                rowMapper));
    }

    public List<StatRow> merchantSales() {
        return cached("stats:merchant", () -> clickHouseJdbcTemplate.query(
                "SELECT CAST(merchant_id AS String) AS period, merchant_id AS dimension_id, SUM(revenue) AS value "
                        + "FROM stats_revenue WHERE metric = 'txn' GROUP BY merchant_id ORDER BY merchant_id",
                rowMapper));
    }

    public List<StatRow> productSales() {
        return cached("stats:product", () -> clickHouseJdbcTemplate.query(
                "SELECT CAST(product_id AS String) AS period, product_id AS dimension_id, SUM(revenue) AS value "
                        + "FROM stats_revenue WHERE metric = 'txn' GROUP BY product_id ORDER BY product_id",
                rowMapper));
    }

    private List<StatRow> cached(String key, Query query) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<StatRow>>() {
                });
            }
        } catch (Exception e) {
            log.warn("redis read miss for {}: {}", key, e.getMessage());
        }
        List<StatRow> rows = query.run();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(rows), TTL);
        } catch (Exception e) {
            log.warn("redis write miss for {}: {}", key, e.getMessage());
        }
        return rows;
    }

    @FunctionalInterface
    private interface Query {
        List<StatRow> run();
    }
}

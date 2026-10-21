package com.sanedge.pointofsale.statswriter.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes analytics events into the ClickHouse {@code stats_revenue} table.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StatsClickHouseWriter {

    private final JdbcTemplate clickHouseJdbcTemplate;

    public void ensureTable() {
        clickHouseJdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS stats_revenue ("
                        + "period String, "
                        + "revenue Float64, "
                        + "merchant_id Int64 DEFAULT 0, "
                        + "product_id Int64 DEFAULT 0, "
                        + "metric String DEFAULT 'txn'"
                        + ") ENGINE = MergeTree() ORDER BY (metric, period)");
    }

    public void write(String period, double revenue, long merchantId, long productId) {
        clickHouseJdbcTemplate.update(
                "INSERT INTO stats_revenue (period, revenue, merchant_id, product_id, metric) VALUES (?, ?, ?, ?, 'txn')",
                period, revenue, merchantId, productId);
    }
}

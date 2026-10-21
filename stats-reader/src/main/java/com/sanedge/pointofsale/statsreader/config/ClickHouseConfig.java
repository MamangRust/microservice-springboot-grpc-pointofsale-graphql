package com.sanedge.pointofsale.statsreader.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * ClickHouse read-side DataSource used by the stats reader.
 *
 * <p>Driver: {@code com.clickhouse.jdbc.ClickHouseDriver}. The reader only issues
 * SELECTs, so a small read-only connection pool is sufficient.
 */
@Configuration
public class ClickHouseConfig {

    @Bean
    public DataSource clickHouseDataSource(
            @Value("${clickhouse.url:jdbc:clickhouse://localhost:8123/default}") String url,
            @Value("${clickhouse.username:default}") String username,
            @Value("${clickhouse.password:}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(8);
        ds.setReadOnly(true);
        return ds;
    }

    @Bean
    public JdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
}

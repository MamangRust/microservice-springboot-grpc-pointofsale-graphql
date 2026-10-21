package com.sanedge.pointofsale.statswriter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Source PostgreSQL DataSource used only by the one-shot backfill.
 *
 * <p>Enabled with {@code app.backfill.enabled=true}; points at the per-context
 * transaction/sales DB via {@code backfill.source.*}.
 */
@Configuration
@ConditionalOnProperty(name = "app.backfill.enabled", havingValue = "true")
public class SourceConfig {

    @Bean
    public DataSource sourceDataSource(
            @Value("${backfill.source.url:jdbc:postgresql://localhost:5432/pos_sales}") String url,
            @Value("${backfill.source.user:DRAGON}") String user,
            @Value("${backfill.source.password:DRAGON}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setMaximumPoolSize(4);
        return ds;
    }

    @Bean
    public JdbcTemplate sourceJdbcTemplate(DataSource sourceDataSource) {
        return new JdbcTemplate(sourceDataSource);
    }
}

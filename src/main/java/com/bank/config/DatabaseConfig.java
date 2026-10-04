package com.bank.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Single shared connection pool for the whole application.
 * Reads credentials from environment variables so nothing is hardcoded.
 */
public final class DatabaseConfig {

    private static final HikariDataSource DATA_SOURCE = build();

    private DatabaseConfig() {
    }
    private static HikariDataSource build() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env("DB_URL", "jdbc:mysql://localhost:3306/bankdb?useSSL=false&allowPublicKeyRetrieval=true"));

        config.setUsername(env("DB_USER", "bankuser"));
        config.setPassword(env("DB_PASSWORD", "bankpass"));

        // Pool sizing: enough connections that concurrent threads do not
        // serialise on connection acquisition instead of on the row lock.
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000);
        config.setAutoCommit(true);
        config.setPoolName("bank-pool");
        return new HikariDataSource(config);
    }


    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public static DataSource dataSource() {
        return DATA_SOURCE;
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public static void shutdown() {
        DATA_SOURCE.close();
    }
}

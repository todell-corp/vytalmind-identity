package com.vm.identity.config;

import com.vm.identity.security.DatabaseCredentialProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackages = "com.vm.identity.repository")
@EnableTransactionManagement
public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private final DatabaseCredentialProvider credentialProvider;

    public DatabaseConfig(@Autowired(required = false) DatabaseCredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    @PostConstruct
    public void logConfiguration() {
        log.info("=== Database Configuration ===");
        if (credentialProvider != null) {
            DatabaseCredentialProvider.DatabaseCredentials creds = credentialProvider.getCredentials();
            log.info("Database URL: {}", creds.url());
            log.info("Database Username: {}", creds.username());
            log.info("Credential Provider: {}", credentialProvider.getClass().getSimpleName());
        } else {
            log.warn("No DatabaseCredentialProvider configured");
        }
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariConfig hikariConfig() {
        if (credentialProvider == null) {
            throw new IllegalStateException("DatabaseCredentialProvider not configured");
        }

        DatabaseCredentialProvider.DatabaseCredentials creds = credentialProvider.getCredentials();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(creds.url());
        config.setUsername(creds.username());
        config.setPassword(creds.password());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return config;
    }

    @Bean
    public DataSource dataSource(HikariConfig hikariConfig) {
        return new HikariDataSource(hikariConfig);
    }
}

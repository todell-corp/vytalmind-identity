package com.vm.identity.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Environment-based database credential provider for development and staging environments.
 * Loads database credentials from application configuration (backed by environment variables).
 */
@Component
@ConditionalOnProperty(name = "database.credential-provider", havingValue = "environment", matchIfMissing = true)
@ConfigurationProperties(prefix = "spring.datasource")
public class EnvironmentDatabaseCredentialProvider implements DatabaseCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentDatabaseCredentialProvider.class);

    private String url;
    private String username;
    private String password;

    @PostConstruct
    public void init() {
        log.info("=== EnvironmentDatabaseCredentialProvider Initialization ===");
        log.info("Database URL: {}", url);
        log.info("Database Username: {}", username);

        if (url == null || url.isEmpty()) {
            throw new IllegalStateException("Database URL must be configured");
        }

        if (username == null || username.isEmpty()) {
            throw new IllegalStateException("Database username must be configured");
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("Database password must be configured");
        }

        log.info("Successfully initialized EnvironmentDatabaseCredentialProvider");
    }

    @Override
    public DatabaseCredentials getCredentials() {
        return new DatabaseCredentials(url, username, password);
    }

    // Spring Configuration Properties setters
    public void setUrl(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

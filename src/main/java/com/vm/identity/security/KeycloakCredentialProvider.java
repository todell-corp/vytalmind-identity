package com.vm.identity.security;

public interface KeycloakCredentialProvider {
    record KeycloakCredentials(
            String serverUrl,
            String realm,
            String clientId,
            String clientSecret
    ) {}

    KeycloakCredentials getCredentials();
}

package com.vm.identity.security;

public interface DatabaseCredentialProvider {
    record DatabaseCredentials(String url, String username, String password) {}

    DatabaseCredentials getCredentials();
}

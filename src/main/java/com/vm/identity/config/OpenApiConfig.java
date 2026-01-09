package com.vm.identity.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VytalmindIdentity - Identity Management API")
                        .version("1.0.0")
                        .description("""
                                Identity management workflow orchestration service using Temporal.io.

                                This API provides endpoints for:
                                - User creation and management
                                - User profile management
                                - Identity workflow orchestration
                                - User deletion with soft delete support
                                """)
                        .contact(new Contact()
                                .name("Vytalmind Platform Team")
                                .email("platform@vytalmind.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://identity.vytalmind.com")
                                .description("Production Server")
                ));
    }
}

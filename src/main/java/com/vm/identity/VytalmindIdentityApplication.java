package com.vm.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * VytalmindIdentity - Identity Management Workflow Service
 *
 * This application provides identity workflow orchestration capabilities using Temporal.io
 * for coordinating identity operations in a distributed architecture.
 */
@SpringBootApplication
@EnableAsync
public class VytalmindIdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(VytalmindIdentityApplication.class, args);
    }
}

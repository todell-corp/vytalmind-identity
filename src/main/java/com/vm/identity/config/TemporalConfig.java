package com.vm.identity.config;

import com.vm.identity.activity.UserDatabaseActivity;
import com.vm.identity.activity.UserKeycloakActivity;
import com.vm.identity.security.EncryptionPayloadCodec;
import com.vm.identity.security.KeyProvider;
import com.vm.identity.workflow.UserCreateWorkflowImpl;
import com.vm.identity.workflow.UserUpdateWorkflowImpl;
import com.vm.identity.workflow.UserDeleteWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.CodecDataConverter;
import io.temporal.common.converter.DataConverter;
import io.temporal.payload.codec.PayloadCodec;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Configuration for Temporal.io workflow service.
 * Sets up the workflow client, worker factory, and registers workflows/activities.
 */
@Configuration
public class TemporalConfig {

    private static final Logger log = LoggerFactory.getLogger(TemporalConfig.class);

    @Value("${temporal.connection.target}")
    private String temporalServiceAddress;

    @Value("${temporal.namespace}")
    private String namespace;

    @Value("${temporal.worker.task-queue}")
    private String taskQueue;

    @Value("${temporal.encryption.enabled:false}")
    private boolean encryptionEnabled;

    @Autowired(required = false)
    private KeyProvider keyProvider;

    private WorkflowServiceStubs workflowServiceStubs;
    private WorkerFactory workerFactory;

    @PostConstruct
    public void logConfiguration() {
        log.info("=== Temporal Configuration ===");
        log.info("Service address: {}", temporalServiceAddress);
        log.info("Namespace: {}", namespace);
        log.info("Task queue: {}", taskQueue);
        log.info("Encryption enabled: {}", encryptionEnabled);

        // Log environment variables for debugging
        log.info("=== Environment Variables ===");
        logEnvVar("TEMPORAL_ENCRYPTION_ENABLED");
        logEnvVar("TEMPORAL_ENCRYPTION_KEY_PROVIDER");
        logEnvVar("TEMPORAL_ENCRYPTION_KEY_ID");
        logEnvVar("TEMPORAL_ENCRYPTION_KEY_2025_12");
        logEnvVar("VAULT_URI");
        logEnvVar("VAULT_TOKEN");
        logEnvVar("VAULT_SECRET_PATH");
        logEnvVar("SPRING_PROFILES_ACTIVE");
    }

    private void logEnvVar(String name) {
        String value = System.getenv(name);
        if (value != null) {
            // Mask sensitive values
            if (name.contains("KEY") || name.contains("TOKEN") || name.contains("PASSWORD")) {
                log.info("{}: {}***", name, value.substring(0, Math.min(8, value.length())));
            } else {
                log.info("{}: {}", name, value);
            }
        } else {
            log.debug("{}: <not set>", name);
        }
    }

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        log.info("Connecting to Temporal service at: {}", temporalServiceAddress);

        WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalServiceAddress)
                .build();

        this.workflowServiceStubs = WorkflowServiceStubs.newServiceStubs(options);
        return workflowServiceStubs;
    }

    @Bean
    public DataConverter dataConverter() {
        DataConverter dataConverter;

        if (encryptionEnabled) {
            if (keyProvider == null) {
                log.error("Encryption enabled but KeyProvider is null!");
                throw new IllegalStateException("Encryption enabled but KeyProvider not configured");
            }

            log.info("=== Temporal Encryption Configuration ===");
            log.info("Encryption enabled: true");
            log.info("KeyProvider type: {}", keyProvider.getClass().getSimpleName());
            log.info("Current key ID: {}", keyProvider.getCurrentKeyId());

            // Create encryption codec
            PayloadCodec encryptionCodec = new EncryptionPayloadCodec(keyProvider);

            // Wrap default data converter with encryption codec
            dataConverter = new CodecDataConverter(
                DataConverter.getDefaultInstance(),
                Collections.singletonList(encryptionCodec),
                true // encodeFailureAttributes - encrypt error details too
            );

            log.info("Data converter configured with EncryptionPayloadCodec successfully");
        } else {
            log.info("=== Temporal Encryption Configuration ===");
            log.info("Encryption enabled: false");
            log.info("Using standard data converter without encryption");
            dataConverter = DataConverter.getDefaultInstance();
        }

        return dataConverter;
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs, DataConverter dataConverter) {
        // Build client options (no context propagator needed - JWT passed as workflow parameter)
        WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .setDataConverter(dataConverter)
                .build();

        return WorkflowClient.newInstance(workflowServiceStubs, clientOptions);
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient) {
        this.workerFactory = WorkerFactory.newInstance(workflowClient);
        return workerFactory;
    }

    @Bean
    public Worker worker(
            WorkerFactory workerFactory,
            UserDatabaseActivity userDatabaseActivity,
            UserKeycloakActivity userKeycloakActivity) {

        log.info("Creating Temporal worker for task queue: {}", taskQueue);

        WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(100)
                .setMaxConcurrentWorkflowTaskExecutionSize(50)
                .build();

        Worker worker = workerFactory.newWorker(taskQueue, workerOptions);

        // Register workflow implementations
        worker.registerWorkflowImplementationTypes(
                UserCreateWorkflowImpl.class,
                UserUpdateWorkflowImpl.class,
                UserDeleteWorkflowImpl.class
        );

        // Register activity implementations with dependency injection
        worker.registerActivitiesImplementations(
                userDatabaseActivity,
                userKeycloakActivity
        );

        return worker;
    }

    @PostConstruct
    public void startWorkerFactory() {
        // Worker factory will be started after all beans are created
    }

    @Bean
    public WorkerFactoryStarter workerFactoryStarter(WorkerFactory workerFactory, Worker worker) {
        return new WorkerFactoryStarter(workerFactory);
    }

    /**
     * Helper class to start the worker factory after Spring context is fully initialized
     */
    public static class WorkerFactoryStarter {
        private static final Logger log = LoggerFactory.getLogger(WorkerFactoryStarter.class);
        private final WorkerFactory workerFactory;

        public WorkerFactoryStarter(WorkerFactory workerFactory) {
            this.workerFactory = workerFactory;
            log.info("Starting Temporal worker factory...");
            workerFactory.start();
            log.info("Temporal worker factory started successfully");
        }

        public void shutdown() {
            log.info("Shutting down Temporal worker factory...");
            workerFactory.shutdown();
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up Temporal resources...");
        if (workerFactory != null) {
            workerFactory.shutdown();
        }
        if (workflowServiceStubs != null) {
            workflowServiceStubs.shutdown();
        }
    }
}

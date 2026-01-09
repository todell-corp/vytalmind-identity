# VytalmindIdentity

A minimal Temporal.io workflow orchestration service demonstrating the Saga pattern with end-to-end payload encryption.

## Overview

VytalmindIdentity is a production-ready microservice built with Spring Boot 3.2 and Temporal.io 1.22.3 that showcases:

- **Durable Workflow Orchestration**: Reliable multi-step business processes with automatic retries
- **Saga Pattern Implementation**: Distributed transaction management with automatic compensation
- **End-to-End Encryption**: AES-256-GCM encryption for all workflow payloads
- **Production Observability**: Prometheus metrics, health checks, and distributed tracing

## Tech Stack

- **Java 18**
- **Spring Boot 3.2.0** (Web, Actuator, WebFlux)
- **Temporal.io 1.22.3** - Workflow orchestration
- **Resilience4j** - Circuit breakers and retries
- **Micrometer + Prometheus** - Metrics and monitoring
- **Maven** - Build system

## Quick Start

### Prerequisites

- Java 18+
- Docker and Docker Compose
- Maven 3.8+

### Running Locally

The easiest way to get started is using the provided run script:

```bash
# Start all services (Temporal, PostgreSQL, Prometheus, Grafana + application)
./run.sh

# Clean up and start fresh
./run.sh clean

# Stop all services
./run.sh stop
```

Alternatively, start infrastructure only and run the application locally:

```bash
# Start infrastructure services
docker-compose up -d temporal temporal-ui postgresql prometheus grafana

# Run application locally
mvn spring-boot:run
```

### Accessing Services

| Service | URL | Description |
|---------|-----|-------------|
| Application API | http://localhost:8080 | Main REST API |
| Codec Server | http://localhost:8081 | Payload encryption/decryption |
| Temporal UI | http://localhost:8088 | Workflow visualization |
| Prometheus | http://localhost:9091 | Metrics collection |
| Grafana | http://localhost:3000 | Dashboards (admin/admin) |
| Health Check | http://localhost:8080/actuator/health | Service health |
| Metrics | http://localhost:8080/actuator/prometheus | Prometheus metrics |

## Project Structure

```
vytalmind-identity/
├── src/main/java/com/vm/identity/
│   ├── workflow/         # Temporal workflow definitions
│   ├── activity/         # Activity implementations
│   ├── controller/       # REST API endpoints
│   ├── security/         # Encryption components
│   ├── config/           # Spring configuration
│   └── service/          # Business logic services
├── codec-server/         # Standalone codec service for Temporal UI
├── docs/                 # Comprehensive documentation
├── temporal-config/      # Temporal configuration files
└── docker-compose.yml    # Local development stack
```

## Key Features

### Saga Pattern

The `SagaWorkflow` provides a flexible framework for orchestrating multi-step processes with automatic compensation on failure:

```java
// Each step registers a compensation action
saga.addCompensation(() -> undoActivity.compensate(data));

// On failure, compensations execute in reverse order automatically
```

### End-to-End Encryption

All workflow payloads are encrypted with AES-256-GCM before being sent to Temporal:

- Temporal service never sees plaintext data
- JWT tokens and sensitive business data remain encrypted at rest
- Key rotation supported via multiple concurrent keys
- Codec Server enables viewing encrypted data in Temporal UI

See [docs/ENCRYPTION_QUICKSTART.md](docs/ENCRYPTION_QUICKSTART.md) for setup details.

### Resilience Patterns

- Circuit breakers for external service calls
- Automatic retries with exponential backoff
- Configurable timeouts per activity
- Health checks with dependency status

## Development

### Build Commands

```bash
# Build and run tests
mvn clean install

# Package without tests
mvn clean package -DskipTests

# Run locally
mvn spring-boot:run
```

### Configuration

Configuration is managed through [application.yml](src/main/resources/application.yml) with environment variable overrides:

```bash
# Temporal connection
TEMPORAL_CONNECTION_TARGET=localhost:7233

# Encryption (development)
TEMPORAL_ENCRYPTION_ENABLED=true
TEMPORAL_ENCRYPTION_KEY_ID=key-2025-12
TEMPORAL_ENCRYPTION_KEY_2025_12=<base64-key>

# Service URLs
VYTALMIND_RECIPES_URL=http://localhost:8082
```

See [.env.example](.env.example) for a complete configuration template.

## Documentation

- **[ENCRYPTION_QUICKSTART.md](docs/ENCRYPTION_QUICKSTART.md)** - Quick encryption setup
- **[ENCRYPTION_KEY_GENERATION.md](docs/ENCRYPTION_KEY_GENERATION.md)** - Key generation and Vault setup
- **[DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md)** - Production deployment instructions
- **[ENCRYPTION_IMPLEMENTATION_SUMMARY.md](docs/ENCRYPTION_IMPLEMENTATION_SUMMARY.md)** - Technical implementation details
- **[CLAUDE.md](CLAUDE.md)** - Development guidance for AI assistants

## Example: Ingredient Update Workflow

```bash
# Start an ingredient update workflow
curl -X POST http://localhost:8080/api/v1/ingredients/workflow/start \
  -H "Content-Type: application/json" \
  -d '{
    "ingredientId": "ing-123",
    "data": {"name": "Organic Tomatoes"},
    "jwtToken": "eyJhbGc..."
  }'

# Query workflow status
curl http://localhost:8080/api/v1/workflows/{workflowId}/status

# View in Temporal UI (with encrypted payloads decoded)
# Navigate to http://localhost:8088
```

## Architecture Highlights

### Temporal Worker Registration

Workers are registered in [TemporalConfig.java](src/main/java/com/vm/identity/config/TemporalConfig.java):

- Workflow implementations registered by class
- Activity implementations injected as Spring beans
- Payload encryption configured via `CodecDataConverter`

### Activity Development

Activities can perform non-deterministic operations (I/O, external calls):

```java
@ActivityInterface
public interface IngredientActivity {
    @ActivityMethod
    IngredientUpdateResult updateIngredient(IngredientUpdateRequest request);
}
```

Implementation uses Spring dependency injection:

```java
@Component
public class IngredientActivityImpl implements IngredientActivity {
    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = "recipes-service")
    public IngredientUpdateResult updateIngredient(IngredientUpdateRequest request) {
        // External service call with circuit breaker
    }
}
```

### Workflow Development

Workflows must be deterministic:

```java
@WorkflowInterface
public interface IngredientUpdateWorkflow {
    @WorkflowMethod
    IngredientUpdateResult execute(IngredientUpdateRequest request);
}
```

Use Temporal-provided time/sleep methods:

```java
// ✅ Correct
Workflow.currentTimeMillis()
Workflow.sleep(Duration.ofSeconds(30))

// ❌ Incorrect (non-deterministic)
System.currentTimeMillis()
Thread.sleep(30000)
```

## Observability

### Metrics

Prometheus metrics available at `/actuator/prometheus`:

- Workflow execution duration
- Activity retry counts
- Circuit breaker states
- JVM metrics

### Temporal UI

View workflow execution history, pending activities, and encrypted payloads (via Codec Server integration) at http://localhost:8088.

### Health Checks

Comprehensive health endpoint at `/actuator/health` includes:

- Temporal service connectivity
- Circuit breaker states
- Database connection status

## Contributing

When contributing to this project:

1. Maintain workflow determinism
2. Add compensation actions for side-effect activities
3. Use circuit breakers for external service calls
4. Follow existing patterns in [CLAUDE.md](CLAUDE.md)
5. Update documentation for significant changes

## License

Copyright © 2025 Vytalmind. All rights reserved.

## Support

For questions or issues, please refer to the documentation in the `docs/` directory or consult [CLAUDE.md](CLAUDE.md) for development guidance.

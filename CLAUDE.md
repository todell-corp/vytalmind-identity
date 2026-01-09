# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

VytalmindIdentity is a minimal Temporal.io workflow orchestration service built with Spring Boot 3.2 and Temporal.io 1.22.3. It demonstrates the Saga pattern for orchestrating distributed workflows with end-to-end payload encryption.

**Core Technologies:**

- Java 18
- Spring Boot 3.2.0 with Spring Web, Actuator, and WebFlux
- Temporal.io for workflow orchestration with AES-256-GCM payload encryption
- Resilience4j for circuit breakers and retries
- Micrometer + Prometheus for observability
- Maven build system

**Project Structure:**

- `src/` - Main application with workflows, activities, and encryption
- `codec-server/` - Separate microservice for decrypting payloads in Temporal UI (separate module)
- `docs/` - Comprehensive documentation (encryption, deployment, key generation)

## Development Commands

### Build and Package

```bash
# Build the application
mvn clean install              # Build and install
mvn clean package              # Package the application
mvn clean package -DskipTests  # Skip tests during packaging
```

### Running the Application

**Local development (requires Temporal server):**

```bash
# Start infrastructure with Docker Compose
docker-compose up -d temporal temporal-ui postgresql prometheus grafana

# Run the application locally
mvn spring-boot:run

# Or with specific profile
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

**Using the run script (recommended):**

```bash
./run.sh                       # Start infrastructure + application locally
./run.sh clean                 # Clean up Docker resources and start fresh
./run.sh stop                  # Stop all services
```

**Full Docker environment (application + all services):**

```bash
docker-compose up -d           # Start all services
docker-compose logs -f vytalmind-identity-app  # View application logs
docker-compose down            # Stop all services
docker-compose down -v         # Stop and remove volumes (clean slate)
```

### Accessing Services

- **Application API:** http://localhost:8080
- **Codec Server:** http://localhost:8081 (for payload encryption/decryption)
- **Temporal UI:** http://localhost:8088 (integrated with Codec Server)
- **Prometheus:** http://localhost:9091
- **Grafana:** http://localhost:3000 (admin/admin)
- **Actuator Health:** http://localhost:8080/actuator/health
- **Metrics:** http://localhost:8080/actuator/prometheus

## Architecture

### Temporal Workflow Pattern

The system uses Temporal.io's durable execution model to orchestrate multi-step business processes with automatic retries, compensation, and observability.

**Key architectural components:**

1. **Workflows** (`src/main/java/com/vm/identity/workflow/`): Define business process logic

   - `SagaWorkflow`: Generic dynamic Saga orchestrator for custom multi-step processes
   - `IngredientUpdateWorkflow`: Example workflow with encrypted payloads

2. **Activities** (`src/main/java/com/vm/identity/activity/`): Execute individual steps (interact with external services)

   - `IngredientActivity`: Ingredient operations with encrypted JWT tokens

3. **Controllers** (`src/main/java/com/vm/identity/controller/`): REST API endpoints

   - `WorkflowController`: Generic workflow operations (query, signal, etc.)
   - `IngredientController`: Ingredient workflow orchestration

4. **Encryption** (`src/main/java/com/vm/identity/security/`): End-to-end payload encryption

   - `EncryptionPayloadCodec`: AES-256-GCM encryption for all workflow payloads
   - `KeyProvider`: Abstraction for key management (environment, Vault, AWS KMS)
   - `EnvironmentKeyProvider`: Development/staging key provider from environment variables
   - `VaultKeyProvider`: Production key provider using HashiCorp Vault

5. **Codec Server** (`codec-server/`): Separate microservice for Temporal UI
   - `CodecController`: REST endpoints for encrypting/decrypting payloads
   - Enables viewing encrypted workflow data in Temporal Web UI
   - Access-controlled via CORS and network isolation

### Saga Pattern Implementation

The `SagaWorkflowImpl` demonstrates the Saga pattern for distributed transactions:

1. Each step adds a compensation action to the Saga
2. On failure, Temporal automatically executes compensations in reverse order
3. Workflows support dynamic step execution based on configuration
4. Activities are invoked via Temporal activity stubs (not direct calls)

**Important:** Workflow code must be deterministic. Use `Workflow.currentTimeMillis()` instead of `System.currentTimeMillis()`, and `Workflow.sleep()` instead of `Thread.sleep()`. Activities are called through Temporal stubs, never directly instantiated.

### Configuration Management

- **application.yml**: Main configuration with environment variable overrides
- **Temporal connection**: Configured via `temporal.connection.target`, defaults to `localhost:7233`
- **Task Queue**: Defined in `temporal.worker.task-queue`, defaults to `vytalmind-identity-queue`
- **Microservice URLs**: Configurable via environment variables
- **Resilience4j**: Circuit breaker and retry policies per service in `resilience4j.*` config

### Worker Registration

The `TemporalConfig` class (`src/main/java/com/vm/identity/config/TemporalConfig.java`) handles:

- Temporal client connection
- Worker factory creation and startup
- Registration of workflow implementations
- Registration of activity implementations with Spring dependency injection
- **Payload encryption configuration** via `CodecDataConverter` with `EncryptionPayloadCodec`

**Critical:** Workers must be registered with both workflow implementations (classes) and activity implementations (Spring beans) before starting.

### Payload Encryption (Security)

The application implements **end-to-end encryption** for all Temporal workflow payloads using AES-256-GCM:

**Architecture:**

```
Client → [Encrypt Payload] → Temporal Service (encrypted storage) → [Decrypt Payload] → Worker
                ↓                                                           ↓
         EncryptionPayloadCodec                                  EncryptionPayloadCodec
```

**Key Features:**

- **AES-256-GCM**: Authenticated encryption with 12-byte IV, 128-bit auth tag
- **End-to-end**: Temporal service never sees plaintext (JWT tokens, business data)
- **Key Rotation**: Multiple concurrent keys supported via key-id metadata
- **Backward Compatible**: Unencrypted payloads pass through unchanged
- **Codec Server**: Separate microservice enables viewing encrypted data in Temporal UI

**Configuration:**

Environment variables (development):

```bash
TEMPORAL_ENCRYPTION_ENABLED=true
TEMPORAL_ENCRYPTION_KEY_PROVIDER=environment
TEMPORAL_ENCRYPTION_KEY_ID=key-2025-12
TEMPORAL_ENCRYPTION_KEY_2025_12=<base64-encoded-32-byte-key>
```

HashiCorp Vault (production):

```bash
TEMPORAL_ENCRYPTION_ENABLED=true
TEMPORAL_ENCRYPTION_KEY_PROVIDER=vault
VAULT_URI=https://vault.odell.com
VAULT_TOKEN=<vault-token>
VAULT_SECRET_PATH=secret/data/temporal/encryption
```

**Key Generation:**

```bash
openssl rand -base64 32
```

**Documentation:**

- `docs/ENCRYPTION_QUICKSTART.md` - Quick start guide for encryption setup
- `docs/ENCRYPTION_KEY_GENERATION.md` - Comprehensive key generation and Vault setup guide
- `docs/DEPLOYMENT_GUIDE.md` - Deployment instructions with encryption
- `docs/ENCRYPTION_IMPLEMENTATION_SUMMARY.md` - Complete implementation details
- `.env.example` - Sample environment configuration with pre-generated key

## Important Development Notes

### Code Quality Standards

- **Never add dead code**: Do not add unreachable code, placeholder exceptions, or comments like "Never reached, satisfies compiler". Write clean code where all paths are meaningful and necessary.

### Workflow Development

- Workflows must be deterministic (no random numbers, system time, or I/O)
- Use `Workflow.getLogger()` for logging inside workflows
- Activity timeouts and retry policies are configured per-activity in workflow implementations
- Always add compensation actions to the Saga when executing activities with side effects

### Activity Development

- Activities can contain non-deterministic code (I/O, external calls)
- Use Spring beans for activity implementations to leverage dependency injection
- Activities should be idempotent where possible
- Use circuit breakers (via Resilience4j annotations) for external service calls

### Signals and Queries

- **Signals**: Asynchronous workflow updates
- **Queries**: Synchronous workflow state reads
- Queries must not modify workflow state

### Observability

- Metrics exposed via Prometheus at `/actuator/prometheus`
- Temporal workflows are automatically traced and visible in Temporal UI
- Application-level metrics tagged with `application: vytalmind-identity-service`
- Health checks include Resilience4j circuit breaker states

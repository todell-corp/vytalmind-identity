# GitHub Actions Workflows

This directory contains the CI/CD workflows for VytalmindIdentity.

## Workflows

### `deploy.yml` - Build and Deploy Pipeline

Automatically builds, tests, and deploys the application when code is pushed to the `main` branch.

**Triggers:**
- Push to `main` branch
- Manual workflow dispatch

**Jobs:**
1. **Build and Test** - Compiles the application and runs tests
2. **Docker Build and Push** - Creates Docker image and pushes to GitHub Container Registry
3. **Deploy** - Deploys to production server
4. **Rollback** - Automatically rolls back if deployment fails

## Required GitHub Secrets

Configure these secrets in your repository settings (`Settings` → `Secrets and variables` → `Actions`):

### Maven/Nexus Configuration
- `NEXUS_USERNAME` - Username for Nexus Maven repository (nexus.odell.com)
- `NEXUS_PASSWORD` - Password for Nexus Maven repository
- `ROOT_CA_CERT` - Root CA certificate content (entire PEM file) for nexus.odell.com

### Deployment Configuration
- `SSH_PRIVATE_KEY` - Private SSH key for deployment server access
- `DEPLOY_HOST` - Production server hostname or IP address
- `DEPLOY_USER` - SSH user for deployment (e.g., `deploy` or `ubuntu`)

### Temporal Encryption (Production)
- `TEMPORAL_ENCRYPTION_ENABLED` - Set to `true` to enable encryption
- `TEMPORAL_ENCRYPTION_KEY_PROVIDER` - `environment` or `vault`
- `TEMPORAL_ENCRYPTION_KEY_ID` - Current key ID (e.g., `key-2025-12`)
- `TEMPORAL_ENCRYPTION_KEY` - Base64-encoded 32-byte encryption key
- `VAULT_URI` - HashiCorp Vault URI (if using Vault)
- `VAULT_TOKEN` - Vault authentication token (if using Vault)

## Setup Instructions

### 1. Configure GitHub Container Registry

The workflow automatically uses GitHub Container Registry (GHCR). Ensure:
- Repository visibility is set appropriately
- GitHub Actions has permission to write packages

### 2. Prepare Deployment Server

On your production server:

```bash
# Create application directory
sudo mkdir -p /opt/vytalmind-identity
sudo chown $USER:$USER /opt/vytalmind-identity
cd /opt/vytalmind-identity

# Copy docker-compose.yml from repository
# (or create one specifically for production)
```

### 3. Configure SSH Access

Generate an SSH key pair for deployment:

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f deploy_key
```

- Add the public key (`deploy_key.pub`) to `~/.ssh/authorized_keys` on the server
- Add the private key (`deploy_key`) content as the `SSH_PRIVATE_KEY` secret in GitHub

### 4. Add Required Secrets

In GitHub repository:
1. Go to `Settings` → `Secrets and variables` → `Actions`
2. Click `New repository secret`
3. Add each secret listed above

### 5. Create Production Environment

1. Go to `Settings` → `Environments`
2. Create environment named `production`
3. (Optional) Add protection rules:
   - Required reviewers
   - Wait timer
   - Deployment branches: `main` only

## Docker Image

Built images are pushed to:
```
ghcr.io/<owner>/<repository>:latest
ghcr.io/<owner>/<repository>:main-<sha>
```

To pull manually:
```bash
docker pull ghcr.io/<owner>/vytalmind-identity:latest
```

## Manual Deployment

Trigger the workflow manually:
1. Go to `Actions` tab
2. Select `Build and Deploy VytalmindIdentity`
3. Click `Run workflow`
4. Select branch and click `Run workflow`

## Monitoring Deployment

### View Logs
```bash
ssh user@server
cd /opt/vytalmind-identity
docker-compose logs -f vytalmind-identity-app
```

### Check Health
```bash
curl https://identity.vytalmind.com/actuator/health
```

### View Metrics
```bash
curl https://identity.vytalmind.com/actuator/prometheus
```

## Rollback

If deployment fails, the workflow automatically attempts to rollback to the previous version.

To manually rollback:
```bash
ssh user@server
cd /opt/vytalmind-identity
docker-compose down
docker pull ghcr.io/<owner>/vytalmind-identity:main-<previous-sha>
docker-compose up -d
```

## Troubleshooting

### Build Fails with Certificate Error
- Verify `ROOT_CA_CERT` secret contains the complete certificate (including `-----BEGIN CERTIFICATE-----` and `-----END CERTIFICATE-----`)
- Check that `rootCA.pem` file exists in repository root

### Maven Dependency Download Fails
- Verify `NEXUS_USERNAME` and `NEXUS_PASSWORD` are correct
- Test credentials: `curl -u username:password https://nexus.odell.com/repository/maven2-hosted/`

### SSH Connection Fails
- Verify `SSH_PRIVATE_KEY` has correct format (begins with `-----BEGIN OPENSSH PRIVATE KEY-----`)
- Check server allows SSH key authentication
- Verify `DEPLOY_USER` has permission to access `/opt/vytalmind-identity`

### Docker Pull Fails on Server
- Ensure server is authenticated with GHCR: `docker login ghcr.io`
- Check image visibility settings in GitHub

### Health Check Fails
- Check application logs: `docker-compose logs vytalmind-identity-app`
- Verify all required environment variables are set
- Check Temporal service is running and accessible
- Verify database connectivity

## Security Notes

- **Never commit secrets to the repository**
- Rotate SSH keys and encryption keys regularly
- Use GitHub environment protection rules for production
- Audit deployment logs regularly
- Keep `ROOT_CA_CERT` up to date with certificate renewals

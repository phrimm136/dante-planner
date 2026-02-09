---
paths:
  - ".github/workflows/**/*.yml"
  - "scripts/deploy*.sh"
  - "scripts/*backup*.sh"
---

# Deployment Credentials Management

## Critical Rule: Container Paths

**Environment variables passed to containers MUST use container-internal paths.**

```yaml
# ✓ Correct
JWT_PRIVATE_KEY_PATH=/app/keys/private_key.pem

# ❌ Wrong - host path won't exist inside container
JWT_PRIVATE_KEY_PATH=/opt/danteplanner/jwt-keys/private_key.pem
```

## SSM Pattern (GitHub Actions)

```yaml
# Fetch from SSM and write to EC2 host
aws ssm get-parameter --name "JWT_PRIVATE_KEY" --with-decryption \
  --query "Parameter.Value" --output text > /opt/danteplanner/jwt-keys/private_key.pem

JWT_ENCRYPTION_KEY=$(aws ssm get-parameter --name "JWT_ENCRYPTION_KEY" \
  --with-decryption --query "Parameter.Value" --output text)

# Generate .env - use CONTAINER paths for env vars
cat <<EOF > .env
JWT_KEYS_PATH=/opt/danteplanner/jwt-keys  # Host path (for volume mount)
JWT_PRIVATE_KEY_PATH=/app/keys/private_key.pem  # Container path (for app)
JWT_ENCRYPTION_KEY=$JWT_ENCRYPTION_KEY
EOF
```

## Path Resolution Flow

1. Write to host: `/opt/danteplanner/jwt-keys/private_key.pem`
2. Mount in docker-compose: `${JWT_KEYS_PATH}` → `/app/keys`
3. App reads from: `JWT_PRIVATE_KEY_PATH=/app/keys/private_key.pem`

**Reference:** `.github/workflows/deploy.yml` lines 173-202

#!/bin/bash
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2; exit 20' ERR

DEPLOY_CONFIG="/tmp/deploy-config.env"
DEPLOY_DIR="/opt/danteplanner"

if [ ! -f "$DEPLOY_CONFIG" ]; then
  echo "[ERROR] Deploy config not found: $DEPLOY_CONFIG" >&2
  exit 20
fi

# Load deploy config (GitHub Actions values)
# shellcheck source=/dev/null
source "$DEPLOY_CONFIG"

cd "$DEPLOY_DIR"

# Update source code
if [ ! -d ".git" ]; then
  git clone "https://github.com/${GITHUB_REPOSITORY}.git" .
else
  git fetch origin main
  git reset --hard origin/main
fi

# Save current IMAGE_TAG for rollback before updating
if [ -f .env ]; then
  CURRENT_TAG=$(grep '^IMAGE_TAG=' .env | cut -d'=' -f2 | tr -d '"' | tr -d "'" || echo "")
  if [ -n "$CURRENT_TAG" ]; then
    echo "$CURRENT_TAG" > .previous_image_tag
    echo "Saved previous IMAGE_TAG: $CURRENT_TAG"
  fi
fi

# Fetch secrets from SSM Parameter Store (encrypted)
MYSQL_PASSWORD=$(aws ssm get-parameter --name "MYSQL_PASSWORD" --with-decryption --query "Parameter.Value" --output text --region "$AWS_REGION")
SENTRY_DSN=$(aws ssm get-parameter --name "SENTRY_DSN" --with-decryption --query "Parameter.Value" --output text --region "$AWS_REGION")
GOOGLE_CLIENT_SECRET=$(aws ssm get-parameter --name "GOOGLE_OAUTH_CLIENT_SECRET" --with-decryption --query "Parameter.Value" --output text --region "$AWS_REGION")
INTERNAL_API_KEY=$(aws ssm get-parameter --name "INTERNAL_API_KEY" --with-decryption --query "Parameter.Value" --output text --region "$AWS_REGION")

# Fetch JWT keys from SSM and write to files
mkdir -p "$DEPLOY_DIR/jwt-keys"
chmod 700 "$DEPLOY_DIR/jwt-keys"

aws ssm get-parameter --name "JWT_PRIVATE_KEY" --with-decryption --query "Parameter.Value" --output text --region "$AWS_REGION" > "$DEPLOY_DIR/jwt-keys/private_key.pem"
aws ssm get-parameter --name "JWT_PUBLIC_KEY" --query "Parameter.Value" --output text --region "$AWS_REGION" > "$DEPLOY_DIR/jwt-keys/public_key.pem"
JWT_ENCRYPTION_KEY=$(aws ssm get-parameter --name "JWT_ENCRYPTION_KEY" --with-decryption --query "Parameter.Value" --output text --region "$AWS_REGION")

chmod 600 "$DEPLOY_DIR/jwt-keys/private_key.pem"
chmod 644 "$DEPLOY_DIR/jwt-keys/public_key.pem"

# Generate .env file (non-secrets from deploy config, secrets from SSM)
cat <<ENV_EOF > .env
IMAGE_TAG=$IMAGE_TAG
AWS_ACCOUNT_ID=$AWS_ACCOUNT_ID
AWS_REGION=$AWS_REGION
SPRING_PROFILES_ACTIVE=prod
SSL_CERT_PATH=$SSL_CERT_PATH
MYSQL_USER=$MYSQL_USER
MYSQL_DATABASE=$MYSQL_DATABASE
MYSQL_PASSWORD=$MYSQL_PASSWORD
GOOGLE_OAUTH_CLIENT_ID=$GOOGLE_OAUTH_CLIENT_ID
GOOGLE_OAUTH_CLIENT_SECRET=$GOOGLE_CLIENT_SECRET
JWT_KEYS_PATH=$DEPLOY_DIR/jwt-keys
JWT_PRIVATE_KEY_PATH=/app/keys/private_key.pem
JWT_PUBLIC_KEY_PATH=/app/keys/public_key.pem
JWT_ENCRYPTION_KEY=$JWT_ENCRYPTION_KEY
SENTRY_DSN=$SENTRY_DSN
TRUSTED_PROXY_IPS=172.16.0.0/12
INTERNAL_API_KEY=$INTERNAL_API_KEY
CORS_ALLOWED_ORIGINS=https://dante-planner.com
ENV_EOF

echo "Environment setup complete"

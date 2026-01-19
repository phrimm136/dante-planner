# GitHub Actions AWS Deployment Explained

A line-by-line breakdown of the deploy.yml workflow, explaining what each section does and why.

---

## Workflow Structure Overview

```yaml
name: Deploy to Production          # Workflow name shown in GitHub UI

on:                                 # Trigger conditions
  push: ...                         # Automatic trigger
  workflow_dispatch: ...            # Manual trigger

env:                                # Global variables

jobs:
  test-backend: ...                 # Job 1: Run backend tests
  test-frontend: ...                # Job 2: Run frontend tests
  build-and-push: ...               # Job 3: Build and push Docker images
  deploy: ...                       # Job 4: Deploy to EC2
  health-check: ...                 # Job 5: Verify deployment
  rollback: ...                     # Job 6: Rollback on failure
  setup-cloudwatch: ...             # Job 7: One-time monitoring setup
```

---

## Trigger Section

```yaml
on:
  push:
    branches: [main]
    paths:
      - 'backend/**'
      - 'frontend/**'
      - 'nginx/**'
      - 'docker-compose.yml'
      - '.github/workflows/deploy.yml'
  workflow_dispatch:
    inputs:
      setup_cloudwatch:
        description: 'Run CloudWatch alarms setup (one-time)'
        required: false
        default: 'false'
        type: boolean
```

### What Each Part Does

| Section | Purpose |
|---------|---------|
| `push.branches: [main]` | Only trigger on pushes to main branch |
| `push.paths` | Only trigger if these files changed (saves CI minutes) |
| `workflow_dispatch` | Allows manual trigger from GitHub UI |
| `inputs.setup_cloudwatch` | Checkbox option when manually triggered |

### Why Path Filtering?
Without `paths`, every push triggers deploy:
- README.md change → deploy (wasteful)
- docs/ change → deploy (wasteful)

With `paths`, only code changes trigger deploy.

---

## Concurrency Control

```yaml
concurrency:
  group: production-deploy
  cancel-in-progress: false
```

### What It Does
- `group`: All runs share this group name
- `cancel-in-progress: false`: Don't cancel running deployments

### Why?
Prevents concurrent deployments that could:
- Overwrite each other
- Leave system in inconsistent state
- Cause race conditions

If you push twice quickly:
```
Push 1 → Deploy starts → Deploy completes
Push 2 → Waits ────────→ Deploy starts → Deploy completes
```

---

## Environment Variables

```yaml
env:
  DEPLOY_PATH: /opt/danteplanner
  ECR_REGISTRY: ${{ secrets.AWS_ACCOUNT_ID }}.dkr.ecr.${{ secrets.AWS_REGION }}.amazonaws.com
```

### What It Does
- `DEPLOY_PATH`: Where code lives on EC2
- `ECR_REGISTRY`: Full ECR URL constructed from secrets

### Secrets vs Env
- **Secrets:** Sensitive (AWS keys, passwords) - never logged
- **Env:** Non-sensitive (paths, URLs) - visible in logs

---

## Test Jobs

```yaml
jobs:
  test-backend:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Run backend tests
        working-directory: ./backend
        run: ./mvnw test -q
```

### Step-by-Step Breakdown

| Step | What It Does |
|------|--------------|
| `runs-on: ubuntu-latest` | Use GitHub-hosted Ubuntu runner |
| `timeout-minutes: 10` | Kill job if takes longer (prevents hung jobs) |
| `actions/checkout@v4` | Clone repository to runner |
| `actions/setup-java@v4` | Install JDK 17 |
| `cache: maven` | Cache ~/.m2 directory between runs (faster builds) |
| `./mvnw test -q` | Run Maven tests (`-q` = quiet output) |

### Why Two Test Jobs?
```yaml
test-backend:    # Job 1
test-frontend:   # Job 2 (runs in PARALLEL)
```

Jobs without `needs:` run in parallel. Testing backend and frontend simultaneously saves ~5 minutes.

---

## Build and Push Job

```yaml
build-and-push:
  needs: [test-backend, test-frontend]
  runs-on: ubuntu-latest
  timeout-minutes: 20
  steps:
    - uses: actions/checkout@v4

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v4
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ secrets.AWS_REGION }}

    - name: Login to Amazon ECR
      uses: aws-actions/amazon-ecr-login@v2

    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v3

    - name: Build and Push Backend
      uses: docker/build-push-action@v5
      with:
        context: ./backend
        file: ./backend/Dockerfile
        push: true
        tags: ${{ env.ECR_REGISTRY }}/danteplanner-backend:${{ github.sha }}
        cache-from: type=gha,scope=backend
        cache-to: type=gha,mode=max,scope=backend
```

### Step-by-Step Breakdown

| Step | What It Does |
|------|--------------|
| `needs: [test-backend, test-frontend]` | Wait for both tests to pass first |
| `configure-aws-credentials` | Sets AWS_ACCESS_KEY_ID, etc. as env vars |
| `amazon-ecr-login` | Runs `aws ecr get-login-password \| docker login` |
| `setup-buildx-action` | Installs Docker Buildx for advanced builds |
| `build-push-action` | Builds image and pushes to ECR |

### Build Cache Explained

```yaml
cache-from: type=gha,scope=backend
cache-to: type=gha,mode=max,scope=backend
```

- `type=gha`: Use GitHub Actions cache (not Docker Hub)
- `scope=backend`: Namespace to avoid collision with nginx cache
- `mode=max`: Cache all layers, not just final image

**Result:** Second build reuses cached layers, ~3 minutes → ~30 seconds.

### Why `github.sha` for Tags?

```yaml
tags: ${{ env.ECR_REGISTRY }}/danteplanner-backend:${{ github.sha }}
```

- `github.sha`: Full commit hash (e.g., `abc123def456...`)
- **Traceability:** Image → exact code version
- **Rollback:** Deploy previous SHA to rollback
- **No overwrites:** Each commit = unique tag

Bad alternative: `latest` tag overwrites, lose history.

---

## Deploy Job

```yaml
deploy:
  needs: build-and-push
  runs-on: ubuntu-latest
  steps:
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v4
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ secrets.AWS_REGION }}

    - name: Deploy via SSM
      run: |
        cat <<'EOF' > raw_script.sh
        # ... script content ...
        EOF

        jq -n --rawfile cmd raw_script.sh '{"commands": [$cmd]}' > ssm_params.json

        COMMAND_ID=$(aws ssm send-command \
          --instance-ids "${{ secrets.EC2_INSTANCE_ID }}" \
          --document-name "AWS-RunShellScript" \
          --parameters file://ssm_params.json \
          --query "Command.CommandId" --output text)
```

### SSM Command Flow

```
GitHub Actions Runner
    │
    │ 1. Write script to raw_script.sh
    │ 2. Convert to JSON format
    │ 3. aws ssm send-command
    ▼
AWS SSM Service
    │
    │ 4. Route to EC2 instance
    ▼
SSM Agent on EC2
    │
    │ 5. Execute script
    │ 6. Return output
    ▼
GitHub Actions Runner
    │
    │ 7. aws ssm get-command-invocation
    │ 8. Check status, show logs
    ▼
Success or Failure
```

### Script Structure Inside Heredoc

```bash
cat <<'EOF' > raw_script.sh
cd /opt/danteplanner

# Docker installation check
if ! command -v docker &> /dev/null; then
  # Install Docker
fi

# EC2 user operations
sudo -u ec2-user bash -e <<'INNER_BATCH'
cd /opt/danteplanner

# Git pull latest code
git fetch origin main
git reset --hard origin/main

# Save current image tag for rollback
if [ -f .env ]; then
  CURRENT_TAG=$(grep '^IMAGE_TAG=' .env | cut -d'=' -f2)
  echo "$CURRENT_TAG" > .previous_image_tag
fi

# Fetch secrets from SSM Parameter Store
MYSQL_PASSWORD=$(aws ssm get-parameter --name "MYSQL_PASSWORD" --with-decryption ...)

# Create .env file
cat <<ENV_EOF > .env
IMAGE_TAG=${{ github.sha }}
MYSQL_PASSWORD=$MYSQL_PASSWORD
...
ENV_EOF

# Login to ECR and pull new images
aws ecr get-login-password | docker login ...
docker compose pull

# Rolling update
docker compose up -d --no-deps --force-recreate nginx
sleep 2
docker compose up -d --no-deps --force-recreate backend

# Cleanup old images
docker image prune -f --filter "until=720h"
INNER_BATCH
EOF
```

### Why Nested Heredocs?

```
<<'EOF'           Outer: Create raw_script.sh file
  <<'INNER_BATCH'  Inner: Run as ec2-user
  INNER_BATCH
EOF
```

- Outer heredoc: Written to file on GitHub runner
- Inner heredoc: Executed on EC2 as ec2-user
- `'EOF'` vs `EOF`: Quoted prevents variable expansion until runtime

### Rolling Update Strategy

```bash
docker compose up -d --no-deps --force-recreate nginx
sleep 2
docker compose up -d --no-deps --force-recreate backend
```

| Flag | Purpose |
|------|---------|
| `-d` | Detached mode (don't block) |
| `--no-deps` | Don't recreate dependencies |
| `--force-recreate` | Recreate even if config unchanged |

**Order matters:**
1. Nginx first (can show 503 while backend restarts)
2. Wait 2 seconds for nginx to be ready
3. Backend second (nginx routes traffic to new container)

---

## Health Check Job

```yaml
health-check:
  needs: deploy
  runs-on: ubuntu-latest
  timeout-minutes: 5
  outputs:
    healthy: ${{ steps.check.outputs.healthy }}
  steps:
    - name: Health check via SSM
      id: check
      run: |
        for attempt in $(seq 1 $MAX_ATTEMPTS); do
          COMMAND_ID=$(aws ssm send-command \
            --instance-ids "$INSTANCE_ID" \
            --document-name "AWS-RunShellScript" \
            --parameters 'commands=["curl -sf http://localhost/health && curl -sf http://localhost/actuator/health || exit 1"]' \
            ...)

          # Wait for command completion
          aws ssm wait command-executed --command-id "$COMMAND_ID" ...

          if [ "$STATUS" = "Success" ]; then
            echo "healthy=true" >> $GITHUB_OUTPUT
            exit 0
          fi

          sleep $WAIT_TIME
          WAIT_TIME=$((WAIT_TIME * 2))  # Exponential backoff
        done

        echo "healthy=false" >> $GITHUB_OUTPUT
        exit 1
```

### What It Does

1. SSH (via SSM) into EC2
2. Run `curl` against localhost health endpoints
3. Retry with exponential backoff (5s, 10s, 20s, 40s)
4. Output `healthy=true/false` for use by rollback job

### Why localhost?
```
Internet → Cloudflare → EC2 (may block bots)
EC2 → localhost → nginx → backend (always works)
```

Cloudflare may return 403/521 for automated requests. Localhost bypasses this.

### Exponential Backoff

```
Attempt 1: Check health, wait 5s
Attempt 2: Check health, wait 10s
Attempt 3: Check health, wait 20s
Attempt 4: Check health, wait 40s
Attempt 5: Check health, FAIL
```

Gives backend time to start (JVM warmup, DB connections).

---

## Rollback Job

```yaml
rollback:
  needs: health-check
  if: failure()
  runs-on: ubuntu-latest
  steps:
    - name: Rollback via SSM
      run: |
        cat <<'EOF' > rollback_script.sh
        cd /opt/danteplanner
        sudo -u ec2-user bash -e <<'INNER_BATCH'

        # Restore previous IMAGE_TAG
        if [ -f .previous_image_tag ]; then
          PREV_TAG=$(cat .previous_image_tag)
          sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$PREV_TAG/" .env
        else
          git reset --hard HEAD~1
        fi

        # Redeploy with previous images
        docker compose pull
        docker compose down --remove-orphans
        docker compose up -d --force-recreate
        INNER_BATCH
        EOF
```

### When Does It Run?

```yaml
if: failure()
```

Only runs if health-check job failed.

### Rollback Strategy

1. **Read `.previous_image_tag`** (saved during deploy)
2. **Update `.env`** with previous IMAGE_TAG
3. **Pull previous images** from ECR
4. **Restart containers** with previous version

### Why Not Git Rollback?

```bash
git reset --hard HEAD~1  # Last resort only
```

Git rollback changes code, but Docker images are already built. Better to:
- Keep code at HEAD
- Just change IMAGE_TAG to previous version
- ECR still has previous images

---

## CloudWatch Setup Job

```yaml
setup-cloudwatch:
  needs: health-check
  if: ${{ github.event.inputs.setup_cloudwatch == 'true' }}
  runs-on: ubuntu-latest
  steps:
    - name: Setup CloudWatch alarms via SSM
      run: |
        # ... runs setup-cloudwatch-alarms.sh on EC2 ...
```

### When Does It Run?

```yaml
if: ${{ github.event.inputs.setup_cloudwatch == 'true' }}
```

Only when manually triggered with checkbox checked.

### Why Separate?

CloudWatch alarms are infrastructure (create once), not deployment artifacts (every push). Running every deploy would be wasteful.

---

## Secrets Reference

| Secret | Purpose | Used By |
|--------|---------|---------|
| `AWS_ACCESS_KEY_ID` | IAM user access key | AWS credential config |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret | AWS credential config |
| `AWS_REGION` | Deployment region | All AWS commands |
| `AWS_ACCOUNT_ID` | Account number | ECR registry URL |
| `EC2_INSTANCE_ID` | Target instance | SSM commands |
| `MYSQL_USER` | Database username | .env file |
| `MYSQL_DATABASE` | Database name | .env file |
| `SSL_CERT_PATH` | Certificate location | nginx config |
| `GOOGLE_OAUTH_CLIENT_ID` | OAuth client | .env file |
| `ALERT_EMAIL` | Notification email | CloudWatch alarms |

### Secrets from SSM Parameter Store

These are fetched at runtime from EC2, not GitHub Secrets:
- `MYSQL_PASSWORD`
- `JWT_SECRET`
- `SENTRY_DSN`
- `GOOGLE_CLIENT_SECRET`

**Why SSM?**
- Encrypted at rest (KMS)
- Audit trail in CloudTrail
- No plaintext in GitHub logs
- Rotation without CI/CD changes

---

## Job Dependency Graph

```
test-backend ───┐
                ├──► build-and-push ──► deploy ──► health-check ──┬──► (success)
test-frontend ──┘                                                 │
                                                                  │
                                                  (if failure) ◄──┘
                                                       │
                                                       ▼
                                                   rollback

(manual trigger with checkbox)
                │
                ▼
         setup-cloudwatch
```

---

## Common Issues and Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| `ParameterNotFound` | SSM parameter name mismatch | Verify exact names with `aws ssm describe-parameters` |
| Health check timeout | Backend slow to start | Increase retry count or wait time |
| ECR login failed | IAM permissions | Add `ecr:GetAuthorizationToken` permission |
| SSM command timeout | Script too long | Check for infinite loops, increase timeout |
| Cache not working | Buildx not set up | Ensure `setup-buildx-action` runs before build |
| Rollback not triggered | Health check didn't fail | Check health check endpoints respond correctly |

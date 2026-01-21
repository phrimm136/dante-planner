#!/bin/bash

# Post-Commit Hook: Update Architecture Map
#
# Analyzes the commit and triggers Claude to update docs/architecture-map.md
# with relevant architectural changes.

ARCH_MAP="$CLAUDE_PROJECT_DIR/docs/architecture-map.md"

# Exit silently if architecture map doesn't exist
if [ ! -f "$ARCH_MAP" ]; then
    exit 0
fi

# Get commit metadata
COMMIT_HASH=$(git -C "$CLAUDE_PROJECT_DIR" rev-parse --short HEAD 2>/dev/null)
COMMIT_MSG=$(git -C "$CLAUDE_PROJECT_DIR" log -1 --pretty=format:"%s" 2>/dev/null)
COMMIT_BODY=$(git -C "$CLAUDE_PROJECT_DIR" log -1 --pretty=format:"%b" 2>/dev/null)

# Exit if we can't get commit info
if [ -z "$COMMIT_MSG" ]; then
    exit 0
fi

# Get detailed file changes with stats
CHANGED_FILES=$(git -C "$CLAUDE_PROJECT_DIR" diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null)
FILE_STATS=$(git -C "$CLAUDE_PROJECT_DIR" diff-tree --no-commit-id --numstat -r HEAD 2>/dev/null)

# Analyze changes by web stack layer
FRONTEND_FILES=$(echo "$CHANGED_FILES" | grep "^frontend/" | head -10)
BACKEND_FILES=$(echo "$CHANGED_FILES" | grep "^backend/" | head -10)
NGINX_FILES=$(echo "$CHANGED_FILES" | grep -E "(nginx|conf\.d)" | head -10)
WEB_CONFIG_FILES=$(echo "$CHANGED_FILES" | grep -E "(docker-compose|Dockerfile|\.env|application\.properties|application\.yml|vite\.config|tsconfig\.json|package\.json|pom\.xml)" | head -10)

# Detect architectural impact areas
CONTROLLERS=$(echo "$CHANGED_FILES" | grep -c "Controller\.java" || true)
SERVICES=$(echo "$CHANGED_FILES" | grep -c "Service\.java" || true)
REPOSITORIES=$(echo "$CHANGED_FILES" | grep -c "Repository\.java" || true)
ENTITIES=$(echo "$CHANGED_FILES" | grep -c "entity/.*\.java" || true)
DTOS=$(echo "$CHANGED_FILES" | grep -c "dto/.*\.java" || true)
COMPONENTS=$(echo "$CHANGED_FILES" | grep -c "components/.*\.tsx" || true)
ROUTES=$(echo "$CHANGED_FILES" | grep -c "routes/.*\.tsx" || true)
HOOKS=$(echo "$CHANGED_FILES" | grep -c "hooks/.*\.ts" || true)
SCHEMAS=$(echo "$CHANGED_FILES" | grep -c "schemas/.*\.ts" || true)

# Detect web stack infrastructure changes
NGINX_CHANGE=$(echo "$NGINX_FILES" | grep -c . || true)
DOCKER_CHANGE=$(echo "$CHANGED_FILES" | grep -c "docker" || true)
BUILD_CONFIG_CHANGE=$(echo "$WEB_CONFIG_FILES" | grep -c . || true)
SECURITY_CHANGE=$(echo "$CHANGED_FILES" | grep -ci "security\|auth" || true)
API_CHANGE=$(echo "$CHANGED_FILES" | grep -ci "controller\|endpoint\|api" || true)

# Count total web stack files changed
FRONTEND_COUNT=$(echo "$FRONTEND_FILES" | grep -c . || true)
BACKEND_COUNT=$(echo "$BACKEND_FILES" | grep -c . || true)
TOTAL_WEB_STACK_CHANGES=$((FRONTEND_COUNT + BACKEND_COUNT + NGINX_CHANGE + BUILD_CONFIG_CHANGE))

# Exit silently if no web stack files changed (only docs, scripts, tests, etc.)
if [ $TOTAL_WEB_STACK_CHANGES -eq 0 ]; then
    exit 0
fi

# Build web stack impact summary
IMPACT_SUMMARY=""

# Frontend layer
if [ $COMPONENTS -gt 0 ] || [ $ROUTES -gt 0 ] || [ $HOOKS -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  ⚛️  FRONTEND: Components($COMPONENTS) Routes($ROUTES) Hooks($HOOKS)"
fi

# Backend layer
if [ $CONTROLLERS -gt 0 ] || [ $SERVICES -gt 0 ] || [ $REPOSITORIES -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  ☕ BACKEND: Controllers($CONTROLLERS) Services($SERVICES) Repositories($REPOSITORIES)"
fi

if [ $ENTITIES -gt 0 ] || [ $DTOS -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  💾 DATABASE: Entities($ENTITIES) DTOs($DTOS)"
fi

# Infrastructure layer
if [ $NGINX_CHANGE -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  🌐 NGINX: Configuration changed"
fi

if [ $DOCKER_CHANGE -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  🐳 DOCKER: Container configuration changed"
fi

if [ $BUILD_CONFIG_CHANGE -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  ⚙️  BUILD CONFIG: Project configuration changed"
fi

if [ $SECURITY_CHANGE -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  🔒 SECURITY: Authentication/authorization changed"
fi

if [ $API_CHANGE -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  🔌 API: Endpoints/contracts changed"
fi

# Build compact update request
cat << EOF
━━━ 🚨 MANDATORY: ARCH-MAP UPDATE ━━━
Commit: $COMMIT_HASH | $COMMIT_MSG
$(echo -e "$IMPACT_SUMMARY")
Files: $(echo "$CHANGED_FILES" | head -8 | tr '\n' ' ')

ACTION: Read docs/architecture-map.md → Update "Last Updated" to $(date +%Y-%m-%d) → Update relevant sections (Frontend/Backend/Nginx/Config tables)
Focus on architectural significance, not implementation details.
Confirm: "Arch-map updated: $COMMIT_HASH"
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EOF

exit 0

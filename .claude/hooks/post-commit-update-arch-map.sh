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

# Analyze changes by category
FRONTEND_FILES=$(echo "$CHANGED_FILES" | grep "^frontend/" | head -10)
BACKEND_FILES=$(echo "$CHANGED_FILES" | grep "^backend/" | head -10)
DOCS_FILES=$(echo "$CHANGED_FILES" | grep "^docs/" | head -10)
CONFIG_FILES=$(echo "$CHANGED_FILES" | grep -E "\.(json|yaml|yml|properties|md)$" | grep -v "^frontend/" | grep -v "^backend/" | head -10)

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

# Detect feature domains
IDENTITY_CHANGE=$(echo "$CHANGED_FILES" | grep -ci "identity" || true)
EGO_CHANGE=$(echo "$CHANGED_FILES" | grep -ci "ego" || true)
PLANNER_CHANGE=$(echo "$CHANGED_FILES" | grep -ci "planner" || true)
AUTH_CHANGE=$(echo "$CHANGED_FILES" | grep -ci "auth" || true)
COMMENT_CHANGE=$(echo "$CHANGED_FILES" | grep -ci "comment" || true)
NOTIFICATION_CHANGE=$(echo "$CHANGED_FILES" | grep -ci "notification" || true)

# Build architectural impact summary
IMPACT_SUMMARY=""

if [ $CONTROLLERS -gt 0 ] || [ $SERVICES -gt 0 ] || [ $REPOSITORIES -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  - Backend layer changes: Controllers($CONTROLLERS) Services($SERVICES) Repositories($REPOSITORIES)"
fi

if [ $ENTITIES -gt 0 ] || [ $DTOS -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  - Data model changes: Entities($ENTITIES) DTOs($DTOS)"
fi

if [ $COMPONENTS -gt 0 ] || [ $ROUTES -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  - Frontend changes: Components($COMPONENTS) Routes($ROUTES)"
fi

if [ $HOOKS -gt 0 ] || [ $SCHEMAS -gt 0 ]; then
    IMPACT_SUMMARY="${IMPACT_SUMMARY}\n  - Data layer changes: Hooks($HOOKS) Schemas($SCHEMAS)"
fi

# Build feature domain summary
FEATURE_SUMMARY=""

if [ $PLANNER_CHANGE -gt 0 ]; then
    FEATURE_SUMMARY="${FEATURE_SUMMARY}\n  - Planner feature"
fi

if [ $AUTH_CHANGE -gt 0 ]; then
    FEATURE_SUMMARY="${FEATURE_SUMMARY}\n  - Authentication"
fi

if [ $COMMENT_CHANGE -gt 0 ]; then
    FEATURE_SUMMARY="${FEATURE_SUMMARY}\n  - Comment system"
fi

if [ $NOTIFICATION_CHANGE -gt 0 ]; then
    FEATURE_SUMMARY="${FEATURE_SUMMARY}\n  - Notification system"
fi

if [ $IDENTITY_CHANGE -gt 0 ]; then
    FEATURE_SUMMARY="${FEATURE_SUMMARY}\n  - Identity browser"
fi

if [ $EGO_CHANGE -gt 0 ]; then
    FEATURE_SUMMARY="${FEATURE_SUMMARY}\n  - EGO browser"
fi

# Build the update request
cat << EOF

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📐 ARCHITECTURE MAP UPDATE REQUEST
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📝 Commit: $COMMIT_HASH
💬 Message: $COMMIT_MSG

📊 Changes Summary:
$(echo -e "$IMPACT_SUMMARY")
$(echo -e "$FEATURE_SUMMARY")

📂 Key Files Changed:
$(echo "$CHANGED_FILES" | head -15 | sed 's/^/  - /')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

⚡ ACTION REQUIRED:

Please update docs/architecture-map.md to reflect this commit:

1. **Update "Last Updated" timestamp** to $(date +%Y-%m-%d) with brief description
2. **Review and update affected sections**:
   - Frontend Core Files (if components/routes/hooks changed)
   - Backend Core Files (if controllers/services/entities changed)
   - Cross-Cutting Concerns (if shared utilities/patterns changed)
   - Data Flow Patterns (if new flows or significant changes)
   - Feature Domain Deep Dives (if feature behavior changed)

3. **Add new entries** if this commit introduces:
   - New features or domains
   - New architectural patterns
   - New cross-cutting concerns
   - New data flows

📋 Commit Details:
$COMMIT_BODY

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Use: Read docs/architecture-map.md, then Edit to update relevant sections.
Focus on architectural significance, not implementation details.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

EOF

exit 0

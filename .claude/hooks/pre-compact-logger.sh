#!/bin/bash

# Create logs directory if it doesn't exist
mkdir -p "$CLAUDE_PROJECT_DIR/.claude/logs"

# Log file with timestamp
LOGFILE="$CLAUDE_PROJECT_DIR/.claude/logs/compact-$(date +%Y%m%d-%H%M%S).log"

# Detect trigger type from hook input
TRIGGER_TYPE="${HOOK_INPUT_trigger:-unknown}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee "$LOGFILE"
echo "🗜️  PRE-COMPACT SNAPSHOT" | tee -a "$LOGFILE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOGFILE"
echo "" | tee -a "$LOGFILE"
echo "Timestamp: $(date)" | tee -a "$LOGFILE"
echo "Trigger: $TRIGGER_TYPE" | tee -a "$LOGFILE"
echo "Project: $CLAUDE_PROJECT_DIR" | tee -a "$LOGFILE"
echo "" | tee -a "$LOGFILE"

# Git status (what files are in flight)
echo "=== Git Status ===" | tee -a "$LOGFILE"
git -C "$CLAUDE_PROJECT_DIR" status --short 2>/dev/null | tee -a "$LOGFILE" || echo "No git repo" | tee -a "$LOGFILE"
echo "" | tee -a "$LOGFILE"

# Recent git commits (conversation context)
echo "=== Recent Commits (Last 10) ===" | tee -a "$LOGFILE"
git -C "$CLAUDE_PROJECT_DIR" log --oneline -10 2>/dev/null | tee -a "$LOGFILE" || echo "No git history" | tee -a "$LOGFILE"
echo "" | tee -a "$LOGFILE"

# Files modified in last session (likely in context)
echo "=== Recently Modified Files (Last 30 min) ===" | tee -a "$LOGFILE"
find "$CLAUDE_PROJECT_DIR" -type f \( -name "*.ts" -o -name "*.tsx" -o -name "*.java" -o -name "*.json" -o -name "*.md" \) -mmin -30 2>/dev/null | head -20 | tee -a "$LOGFILE" || echo "None found" | tee -a "$LOGFILE"
echo "" | tee -a "$LOGFILE"

# Check for plan.md files (active tasks)
echo "=== Active Plan Files ===" | tee -a "$LOGFILE"
find "$CLAUDE_PROJECT_DIR" -name "plan.md" -type f 2>/dev/null | while read -r planfile; do
    if [ -f "$planfile" ]; then
        echo ">>> $planfile" | tee -a "$LOGFILE"
        head -20 "$planfile" | tee -a "$LOGFILE"
        echo "" | tee -a "$LOGFILE"
    fi
done
if [ ! -s "$LOGFILE" ] || ! grep -q ">>>" "$LOGFILE"; then
    echo "No active plans" | tee -a "$LOGFILE"
fi
echo "" | tee -a "$LOGFILE"

# Custom instructions if provided (manual compact only)
if [ -n "$HOOK_INPUT_custom_instructions" ]; then
    echo "=== User's Compact Instructions ===" | tee -a "$LOGFILE"
    echo "$HOOK_INPUT_custom_instructions" | tee -a "$LOGFILE"
    echo "" | tee -a "$LOGFILE"
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOGFILE"
echo "📝 Snapshot saved to: $LOGFILE" | tee -a "$LOGFILE"
echo "💡 Review this after compaction to see what was lost" | tee -a "$LOGFILE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOGFILE"

#!/bin/bash

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📚 RELOADING PROJECT CONTEXT POST-COMPACT"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Global CLAUDE.md (user's personal instructions)
if [ -f ~/.claude/CLAUDE.md ]; then
    echo "=== Global CLAUDE.md ==="
    cat ~/.claude/CLAUDE.md
    echo ""
fi

# Project root CLAUDE.md (LimbusPlanner rules)
if [ -f "$CLAUDE_PROJECT_DIR/CLAUDE.md" ]; then
    echo "=== Project CLAUDE.md ==="
    cat "$CLAUDE_PROJECT_DIR/CLAUDE.md"
    echo ""
fi

# Frontend CLAUDE.md (React/TypeScript patterns)
if [ -f "$CLAUDE_PROJECT_DIR/frontend/CLAUDE.md" ]; then
    echo "=== Frontend CLAUDE.md ==="
    cat "$CLAUDE_PROJECT_DIR/frontend/CLAUDE.md"
    echo ""
fi

# Backend CLAUDE.md (Spring Boot patterns)
if [ -f "$CLAUDE_PROJECT_DIR/backend/CLAUDE.md" ]; then
    echo "=== Backend CLAUDE.md ==="
    cat "$CLAUDE_PROJECT_DIR/backend/CLAUDE.md"
    echo ""
fi

# Recent git activity (context for what changed)
echo "=== Recent Git Activity ==="
git -C "$CLAUDE_PROJECT_DIR" log --oneline -5 2>/dev/null || echo "No git history available"
echo ""

echo "✓ Context reloaded successfully"

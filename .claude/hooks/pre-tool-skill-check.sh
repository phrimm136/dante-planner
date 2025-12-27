#!/bin/bash
set -e

cd "$CLAUDE_PROJECT_DIR/.claude/hooks"
cat | npx tsx pre-tool-skill-check.ts

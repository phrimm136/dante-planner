#!/bin/bash
# PreToolUse hook to prevent Claude Code from using bulk git add commands
# Blocks: git add -A, git add --all, git add .
# Based on: https://github.com/anthropics/claude-code/blob/main/examples/hooks/bash_command_validator_example.py

# Read JSON input from stdin
input=$(cat)

# Extract tool_name to ensure this is a Bash command
tool_name=$(echo "$input" | jq -r '.tool_name // empty')

# Only process Bash tool calls
if [[ "$tool_name" != "Bash" ]]; then
    exit 0
fi

# Extract the command field from tool_input
command=$(echo "$input" | jq -r '.tool_input.command // empty')

# Exit if no command
if [[ -z "$command" ]]; then
    exit 0
fi

# Check if this is a git add command with bulk staging flags
if echo "$command" | grep -qE 'git\s+add\s+((-A|--all)(\s|$)|\.(\s|$))'; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
    echo "🚫 BLOCKED: Bulk git add command" >&2
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
    echo "" >&2
    echo "Command blocked: $command" >&2
    echo "" >&2
    echo "Blocked patterns:" >&2
    echo "  • git add -A" >&2
    echo "  • git add --all" >&2
    echo "  • git add ." >&2
    echo "" >&2
    echo "Instead, use specific file paths:" >&2
    echo "  ✓ git add path/to/file.ts" >&2
    echo "  ✓ git add path/to/directory/" >&2
    echo "  ✓ git add 'path/**/*.ts'" >&2
    echo "" >&2
    echo "⚠️  AI GUIDANCE:" >&2
    echo "This block does NOT mean 'add all files individually'." >&2
    echo "Select ONLY files truly related to the current task." >&2
    echo "If unsure which files belong, ask the user." >&2
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2

    # Exit 2 blocks tool execution (official hook convention)
    exit 2
fi

# Allow the command
exit 0

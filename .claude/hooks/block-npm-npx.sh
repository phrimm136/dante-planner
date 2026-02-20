#!/bin/bash
# PreToolUse hook: blocks npm and npx commands (project uses yarn)

input=$(cat)

tool_name=$(echo "$input" | jq -r '.tool_name // empty')
if [[ "$tool_name" != "Bash" ]]; then
    exit 0
fi

command=$(echo "$input" | jq -r '.tool_input.command // empty')
if [[ -z "$command" ]]; then
    exit 0
fi

# Block npm and npx commands (but not yarn which internally calls npm)
if echo "$command" | grep -qE '(^|\s|&&|\||\;)(npm|npx)(\s|$)'; then
    echo "BLOCKED: npm/npx is not allowed. This project uses yarn." >&2
    echo "Use 'yarn' instead. Examples:" >&2
    echo "  npx tsc --noEmit  →  yarn tsc --noEmit" >&2
    echo "  npm install       →  yarn install" >&2
    echo "  npm run build     →  yarn build" >&2
    exit 2
fi

exit 0

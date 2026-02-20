#!/bin/bash
# PreToolUse hook: warns when test/typecheck/build commands don't redirect output to /tmp
# Convention: redirect large output to /tmp and read it for analysis (prevents stdout truncation)

input=$(cat)

tool_name=$(echo "$input" | jq -r '.tool_name // empty')
if [[ "$tool_name" != "Bash" ]]; then
    exit 0
fi

command=$(echo "$input" | jq -r '.tool_input.command // empty')
if [[ -z "$command" ]]; then
    exit 0
fi

# Patterns that indicate test/typecheck/build commands
# Matches: yarn test, yarn typecheck, yarn build, vitest, tsc -b, ./mvnw test, ./mvnw compile, ./gradlew test
if echo "$command" | grep -qE '(yarn\s+(test|typecheck|tsc|build|vitest|lint)|vitest(\s|$)|tsc(\s|$)|\./(mvnw|gradlew)\s+(test|compile|build))'; then

    # Check if output is redirected to /tmp with date suffix
    if echo "$command" | grep -qF '> /tmp/' && echo "$command" | grep -qF '$(date +%Y%m%d-%H%M%S)'; then
        exit 0
    fi

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
    echo "⚠️  OUTPUT REDIRECT MISSING" >&2
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
    echo "" >&2
    echo "Command: $command" >&2
    echo "" >&2
    echo "Test/typecheck/build output should be redirected to /tmp" >&2
    echo "and use grep for analysis." >&2
    echo "" >&2
    echo "Pattern:" >&2
    echo "  yarn test run 2>&1 > /tmp/fe-test-\$(date +%Y%m%d-%H%M%S).txt; echo \"EXIT: $?\"" >&2
    echo "  yarn typecheck 2>&1 > /tmp/fe-typecheck-\$(date +%Y%m%d-%H%M%S).txt; echo \"EXIT: $?\"" >&2
    echo "  yarn build 2>&1 > /tmp/fe-build-\$(date +%Y%m%d-%H%M%S).txt; echo \"EXIT: $?\"" >&2
    echo "" >&2
    echo "WHY: Large output gets truncated in stdout." >&2
    echo "Redirecting to /tmp preserves full output for analysis." >&2
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2

    exit 2
fi

exit 0

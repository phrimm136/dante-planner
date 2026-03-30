#!/bin/bash
# PreToolUse hook: enforces test/build output redirect to /tmp and prevents re-runs
# Convention: redirect output to /tmp, then READ the file — never re-run to gather output

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
# Matches: yarn test, yarn typecheck, yarn build, vitest, tsc -b, ./gradlew test, ./gradlew compile, ./gradlew build
if echo "$command" | grep -qE '(yarn\s+(test|typecheck|tsc|build|vitest|lint)|vitest(\s|$)|tsc(\s|$)|\./gradlew\s+(test|compile|build))'; then

    # Determine the output prefix for this command type
    prefix=""
    if echo "$command" | grep -qE 'yarn\s+typecheck|tsc(\s|$)'; then
        prefix="fe-typecheck"
    elif echo "$command" | grep -qE 'yarn\s+test|vitest(\s|$)'; then
        prefix="fe-test"
    elif echo "$command" | grep -qE 'yarn\s+build'; then
        prefix="fe-build"
    elif echo "$command" | grep -qE 'yarn\s+lint'; then
        prefix="fe-lint"
    elif echo "$command" | grep -qE 'gradlew\s+test'; then
        prefix="be-test"
    elif echo "$command" | grep -qE 'gradlew\s+(build|compile)'; then
        prefix="be-build"
    fi

    # Check if output is redirected to /tmp with date suffix
    has_redirect=false
    if echo "$command" | grep -qF '> /tmp/' && echo "$command" | grep -qF '$EPOCHSECONDS'; then
        # Extract the part before > /tmp/ and check if it ends with a pipe
        before_redirect=$(echo "$command" | sed 's|> /tmp/.*||')
        if echo "$before_redirect" | grep -qE '\|\s*(grep|head|tail|awk|sed|wc)'; then
            has_redirect=false
        else
            has_redirect=true
        fi
    fi

    # Skip "already exists" check if command deletes old files first or only reads results
    if echo "$command" | grep -qE 'rm -f /tmp/'; then
        # Command cleans up before re-running — allow it
        :
    elif echo "$command" | grep -qE '(xargs|grep|tail|head|cat)\s.*/tmp/'; then
        # Command only reads/processes existing output — allow it
        exit 0
    fi

    # Check if a recent output file already exists (within last 1 minute)
    if [[ -n "$prefix" ]] && ! echo "$command" | grep -qE 'rm -f /tmp/'; then
        recent_file=$(find /tmp -name "${prefix}-*.txt" -mmin -1 -print 2>/dev/null | sort | tail -1)
    fi

    # Block re-run if recent output exists — read it instead
    if [[ -n "$recent_file" ]]; then
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
        echo "⚠️  OUTPUT ALREADY EXISTS — READ IT" >&2
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
        echo "" >&2
        echo "Recent output: $recent_file" >&2
        echo "" >&2
        echo "Grep the file for errors instead of re-running the command:" >&2
        echo "  ls /tmp/${prefix}-*.txt | sort | tail -1 | xargs grep -E 'FAIL|ERROR|error TS' | tail -30" >&2
        echo "" >&2
        echo "WHY: Re-running wastes time and context. The output is already captured." >&2
        echo "If you need a fresh run (e.g., after fixing failures), delete the old file first:" >&2
        echo "  rm -f $recent_file" >&2
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
        exit 2
    fi

    # Block if no redirect
    if [[ "$has_redirect" != "true" ]]; then
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
        echo "⚠️  OUTPUT REDIRECT MISSING" >&2
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
        echo "" >&2
        echo "Command: $command" >&2
        echo "" >&2
        echo "Redirect pattern:" >&2
        echo '  yarn vitest run > /tmp/fe-test-$EPOCHSECONDS.txt 2>&1' >&2
        echo '  yarn tsc --noEmit > /tmp/fe-typecheck-$EPOCHSECONDS.txt 2>&1' >&2
        echo '  yarn build > /tmp/fe-build-$EPOCHSECONDS.txt 2>&1' >&2
        echo "" >&2
        echo "Then grep the latest file for errors — do not re-run to see output:" >&2
        echo "  ls /tmp/${prefix:-<prefix>}-*.txt | sort | tail -1 | xargs grep -E 'FAIL|ERROR|error TS' | tail -30" >&2
        echo "" >&2
        echo "WHY: Stdout gets truncated. Redirect preserves full output for analysis." >&2
        echo "Do NOT pipe through grep/tail before redirecting — capture the full output first." >&2
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
        exit 2
    fi

    exit 0
fi

exit 0

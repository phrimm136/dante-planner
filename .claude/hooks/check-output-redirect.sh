#!/bin/bash
# PreToolUse hook: enforces test/build output redirect to .claude/reports/logs/ and prevents re-runs
# Convention: redirect output to .claude/reports/logs/, then READ the file — never re-run to gather output

input=$(cat)

tool_name=$(echo "$input" | jq -r '.tool_name // empty')
if [[ "$tool_name" != "Bash" ]]; then
    exit 0
fi

command=$(echo "$input" | jq -r '.tool_input.command // empty')
if [[ -z "$command" ]]; then
    exit 0
fi

# Build a normalized "scan" copy of the command for tool detection only, so a
# tool name passed as DATA is never mistaken for an INVOCATION. Three passes,
# in order (heredoc first, since its body may hold unbalanced quotes):
#   1. drop heredoc bodies      — `git commit -m "$(cat <<'EOF' ... tsc ... EOF)"`
#   2. drop quoted spans        — `meme add "... yarn lint ..."`, `git commit -m "fix yarn test"`
#   3. drop --cwd/-C <dir> flags — so `yarn --cwd frontend test` reads as `yarn test`
# Redirect detection below still uses the raw $command (the `> .claude/reports/logs/...` is unquoted).
unwrapped=$(printf '%s' "$command" | perl -0777 -pe 's/\bflock\s[^\n]*?\s-c\s+(?:\x27(.*?)\x27(?=\s|$)|\x22(.*?)\x22(?=\s|$))/$1$2/s' 2>/dev/null || printf '%s' "$command")
scan=$(printf '%s' "$unwrapped" | perl -0777 -pe "s/<<-?\x27?(\w+)\x27?.*?\n\1//gs; s/\x27[^\x27]*\x27//g; s/\x22[^\x22]*\x22//g; s/\s--cwd\s+\S+//g; s/\s-C\s+\S+//g" 2>/dev/null || printf '%s' "$unwrapped")

# Patterns that indicate test/typecheck/build commands
# Matches: yarn test, yarn typecheck, yarn build, vitest, tsc -b, ./gradlew test, ./gradlew compile, ./gradlew build
if echo "$scan" | grep -qE '(yarn\s+(test|typecheck|tsc|build|vitest|lint)|vitest(\s|$)|tsc(\s|$)|\./gradlew\s+(test|compile|build))'; then

    # Determine the output prefix for this command type
    prefix=""
    if echo "$scan" | grep -qE 'yarn\s+typecheck|tsc(\s|$)'; then
        prefix="fe-typecheck"
    elif echo "$scan" | grep -qE 'yarn\s+test|vitest(\s|$)'; then
        prefix="fe-test"
    elif echo "$scan" | grep -qE 'yarn\s+build'; then
        prefix="fe-build"
    elif echo "$scan" | grep -qE 'yarn\s+lint'; then
        prefix="fe-lint"
    elif echo "$scan" | grep -qE 'gradlew\s+test'; then
        prefix="be-test"
    elif echo "$scan" | grep -qE 'gradlew\s+(build|compile)'; then
        prefix="be-build"
    fi

    # Check if output is redirected to .claude/reports/logs/
    # Also reject piped redirects (e.g., yarn test | grep FAIL > .claude/reports/logs/...) — full output must be captured
    has_redirect=false
    if echo "$command" | grep -qP '> (\S*/)?\.claude/reports/logs/\S+\.(txt|log)'; then
        # Extract the part before the log redirect and check if it ends with a pipe
        before_redirect=$(echo "$command" | sed -E 's#> ([^ ]*/)?\.claude/reports/logs/.*##')
        if echo "$before_redirect" | grep -qE '\|\s*(grep|head|tail|awk|sed|wc)'; then
            has_redirect=false
        else
            has_redirect=true
        fi
    fi

    # Skip "already exists" check if command deletes old files first or only reads results
    if echo "$command" | grep -qE 'rm -f (\S*/)?\.claude/reports/logs/'; then
        # Command cleans up before re-running — allow it
        :
    elif echo "$command" | grep -qE '(xargs|grep|tail|head|cat)\s.*\.claude/reports/logs/'; then
        # Command only reads/processes existing output — allow it
        exit 0
    fi

    # Enforce working-directory targeting: FE commands must set --cwd to the
    # frontend root, BE commands must set the gradle project dir to backend.
    # A bare vitest/tsc/gradlew run at the repo root litters it with caches
    # (node_modules/.vite) and resolves the wrong config. Raw $command is
    # checked (not $scan — scan strips --cwd for tool detection).
    dir_ok=true
    dir_fix=""
    case "$prefix" in
        fe-*)
            if ! echo "$command" | grep -qE -- '--cwd[= ]+\S*frontend(/|[" ]|$)'; then
                dir_ok=false
                dir_fix='yarn --cwd frontend <command>'
            fi
            ;;
        be-*)
            if ! echo "$command" | grep -qE -- '(-p|--project-dir)[= ]+\S*backend(/|[" ]|$)|backend/gradlew'; then
                dir_ok=false
                dir_fix='./gradlew -p backend <task>'
            fi
            ;;
    esac
    if [[ "$dir_ok" != "true" ]]; then
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
        echo "⚠️  PROJECT DIR NOT TARGETED" >&2
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
        echo "" >&2
        echo "Command: $command" >&2
        echo "" >&2
        echo "Tests/builds must run against the FE or BE project root, never the repo root:" >&2
        echo "  $dir_fix" >&2
        echo "" >&2
        echo "WHY: a repo-root run creates stray caches (node_modules/.vite) and picks up" >&2
        echo "the wrong (or no) config. Use --cwd (yarn) / -p (gradlew) — do NOT cd." >&2
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
        echo "Redirect pattern (append && echo PASS / || echo FAIL so the verdict lives in the log):" >&2
        echo '  yarn --cwd frontend vitest run > .claude/reports/logs/fe-test-<session-id>-<suffix>.log 2>&1 && echo PASS >> .claude/reports/logs/fe-test-<session-id>-<suffix>.log || echo FAIL >> .claude/reports/logs/fe-test-<session-id>-<suffix>.log' >&2
        echo '  yarn --cwd frontend tsc --noEmit > .claude/reports/logs/fe-typecheck-<session-id>-<suffix>.log 2>&1 && echo PASS >> .claude/reports/logs/fe-typecheck-<session-id>-<suffix>.log || echo FAIL >> .claude/reports/logs/fe-typecheck-<session-id>-<suffix>.log' >&2
        echo '  yarn --cwd frontend build > .claude/reports/logs/fe-build-<session-id>-<suffix>.log 2>&1 && echo PASS >> .claude/reports/logs/fe-build-<session-id>-<suffix>.log || echo FAIL >> .claude/reports/logs/fe-build-<session-id>-<suffix>.log' >&2
        echo '  ./gradlew -p backend test > .claude/reports/logs/be-test-<session-id>-<suffix>.log 2>&1 && echo PASS >> .claude/reports/logs/be-test-<session-id>-<suffix>.log || echo FAIL >> .claude/reports/logs/be-test-<session-id>-<suffix>.log' >&2
        echo "" >&2
        echo "Then grep the latest file for errors — do not re-run to see output:" >&2
        echo "  ls .claude/reports/logs/${prefix:-<prefix>}-*.log | sort | tail -1 | xargs grep -E 'FAIL|ERROR|error TS' | tail -30" >&2
        echo "" >&2
        echo "WHY: Stdout gets truncated. Redirect preserves full output for analysis." >&2
        echo "Use --cwd (yarn) or -p (gradlew) to set the working directory — do NOT cd into the directory." >&2
        echo "Do NOT pipe through grep/tail before redirecting — capture the full output first." >&2
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
        exit 2
    fi

    exit 0
fi

exit 0

#!/bin/bash

# Every name a Python file binds must be made of at least two words.
#
# A one-word name is what shadowing is made of: `rules_of(cfunc, follow, depth)`
# took a follow depth and `walk(node, when, depth)` took a tree depth, the
# inner shadowed the outer, and the walk looked correct while doing nothing.
# Two words is the whole rule, because a name with two words is a name somebody
# chose.
#
# Reports only what the edit **added**, because the backlog is 2,894 names
# across `decompile2` alone and a hook that prints all of them prints nothing.

CLAUDE_PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$HOME/github/LimbusPlanner}"
HOOK_INPUT=$(cat)

TOOL_INPUT=$(echo "$HOOK_INPUT" | jq -r '.tool_input // {}')
FILE_PATH=$(echo "$TOOL_INPUT" | jq -r '.file_path // ""')

[[ "$FILE_PATH" == *.py ]] || exit 0
[[ -f "$FILE_PATH" ]] || exit 0

LINTER="$CLAUDE_PROJECT_DIR/static/scripts/lintNames.py"
[[ -f "$LINTER" ]] || exit 0

PYTHON="$CLAUDE_PROJECT_DIR/static/.venv/bin/python3"
[[ -x "$PYTHON" ]] || PYTHON=python3

# The names this file held before the edit, so only new ones are reported.
BASELINE="$HOME/.claude/py-name-cache/$(echo "$FILE_PATH" | md5sum | cut -d' ' -f1)"
mkdir -p "$(dirname "$BASELINE")"

CURRENT=$("$PYTHON" "$LINTER" "$FILE_PATH" 2>/dev/null | grep " is one word " | sed 's/^[^:]*:[0-9]*: //' | sort -u)

if [[ -f "$BASELINE" ]]; then
    ADDED=$(comm -13 "$BASELINE" <(echo "$CURRENT"))
else
    ADDED=""
fi
echo "$CURRENT" > "$BASELINE"

if [[ -n "$ADDED" ]]; then
    COUNT=$(echo "$ADDED" | grep -c .)
    echo "  name check: $COUNT one-word name(s) added in $(basename "$FILE_PATH")" >&2
    echo "$ADDED" | head -10 | sed 's/^/    /' >&2
    echo "    every name must be two words — see static/scripts/lintNames.py" >&2
fi

# Inline imports, reported the same way: only the ones this edit added.
IMPORT_LINTER="$CLAUDE_PROJECT_DIR/static/scripts/lintImports.py"
if [[ -f "$IMPORT_LINTER" ]]; then
    IMPORT_BASELINE="$BASELINE.imports"
    IMPORT_CURRENT=$("$PYTHON" "$IMPORT_LINTER" "$FILE_PATH" 2>/dev/null | grep ": inline import: " | sed 's/^[^:]*:[0-9]*: //' | sort -u)
    if [[ -f "$IMPORT_BASELINE" ]]; then
        IMPORT_ADDED=$(comm -13 "$IMPORT_BASELINE" <(echo "$IMPORT_CURRENT"))
    else
        IMPORT_ADDED=""
    fi
    echo "$IMPORT_CURRENT" > "$IMPORT_BASELINE"
    if [[ -n "$IMPORT_ADDED" ]]; then
        IMPORT_COUNT=$(echo "$IMPORT_ADDED" | grep -c .)
        echo "  import check: $IMPORT_COUNT inline import(s) added in $(basename "$FILE_PATH")" >&2
        echo "$IMPORT_ADDED" | head -10 | sed 's/^/    /' >&2
        echo "    every import is module level, or marked '# inline-import: <reason>' — see static/scripts/lintImports.py" >&2
    fi
fi

exit 0

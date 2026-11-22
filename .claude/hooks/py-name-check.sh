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

LINTER="$CLAUDE_PROJECT_DIR/static/src/limbus_static/lintNames.py"
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
    echo "    every name must be two words — see static/src/limbus_static/lintNames.py" >&2
fi

# Inline imports, reported the same way: only the ones this edit added.
IMPORT_LINTER="$CLAUDE_PROJECT_DIR/static/src/limbus_static/lintImports.py"
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
        echo "    every import is module level, or marked '# inline-import: <reason>' — see static/src/limbus_static/lintImports.py" >&2
    fi
fi

# Terminal output in pipeline-step code that bypasses shared/stepOutput, reported
# the same way: only the sites this edit added.
OUTPUT_LINTER="$CLAUDE_PROJECT_DIR/static/src/limbus_static/lintOutput.py"
if [[ -f "$OUTPUT_LINTER" ]]; then
    OUTPUT_BASELINE="$BASELINE.output"
    OUTPUT_CURRENT=$("$PYTHON" "$OUTPUT_LINTER" "$FILE_PATH" 2>/dev/null | grep ": output site: " | sed 's/^[^:]*:[0-9]*: //' | sort -u)
    if [[ -f "$OUTPUT_BASELINE" ]]; then
        OUTPUT_ADDED=$(comm -13 "$OUTPUT_BASELINE" <(echo "$OUTPUT_CURRENT"))
    else
        OUTPUT_ADDED=""
    fi
    echo "$OUTPUT_CURRENT" > "$OUTPUT_BASELINE"
    if [[ -n "$OUTPUT_ADDED" ]]; then
        OUTPUT_COUNT=$(echo "$OUTPUT_ADDED" | grep -c .)
        echo "  output check: $OUTPUT_COUNT output site(s) added in $(basename "$FILE_PATH")" >&2
        echo "$OUTPUT_ADDED" | head -10 | sed 's/^/    /' >&2
        echo "    pipeline steps write through shared/stepOutput.py (say_line, warn_line) — see static/src/limbus_static/lintOutput.py" >&2
    fi
fi

# Layout sites (lintLayout rules R1 to R7), reported the same way: only the sites this
# edit added.
LAYOUT_LINTER="$CLAUDE_PROJECT_DIR/static/src/limbus_static/lintLayout.py"
if [[ -f "$LAYOUT_LINTER" ]]; then
    LAYOUT_BASELINE="$BASELINE.layout"
    LAYOUT_CURRENT=$("$PYTHON" -m limbus_static.lintLayout "$FILE_PATH" 2>/dev/null | grep ": layout R" | sed 's/^[^:]*:[0-9]*: //' | sort -u)
    if [[ -f "$LAYOUT_BASELINE" ]]; then
        LAYOUT_ADDED=$(comm -13 "$LAYOUT_BASELINE" <(echo "$LAYOUT_CURRENT"))
    else
        LAYOUT_ADDED=""
    fi
    echo "$LAYOUT_CURRENT" > "$LAYOUT_BASELINE"
    if [[ -n "$LAYOUT_ADDED" ]]; then
        LAYOUT_COUNT=$(echo "$LAYOUT_ADDED" | grep -c .)
        echo "  layout check: $LAYOUT_COUNT layout site(s) added in $(basename "$FILE_PATH")" >&2
        echo "$LAYOUT_ADDED" | head -10 | sed 's/^/    /' >&2
        echo "    see static/src/limbus_static/lintLayout.py for the layer rules R1 to R7" >&2
    fi
fi

exit 0

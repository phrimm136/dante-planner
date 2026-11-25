#!/usr/bin/env bash
# Scenarios for .claude/hooks/cli-source-guard.sh, run against a throwaway checkout under $TMPDIR.
set -uo pipefail

HOOK=$(realpath "$(dirname "$0")/../cli-source-guard.sh")
TMP=$(mktemp -d "${TMPDIR:-/tmp}/cliguard.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
OPEN=$TMP/open
SHUT=$TMP/shut
mkdir -p "$OPEN/.claude/skills/checks/bin" "$SHUT/.claude/skills/checks/bin"
LIB=.claude/skills/ops/lib/opscli
touch "$OPEN/.claude/.cli-edit"
fail=0

check() {  # check <name> <want exit> <tool> <field> <value> [cwd]
  local out rc
  out=$(jq -cn --arg t "$3" --arg f "$4" --arg v "$5" --arg c "${6:-$SHUT}" \
    '{tool_name:$t, cwd:$c, tool_input:{($f):$v}}' | "$HOOK" 2>&1)
  rc=$?
  if [ "$rc" = "$2" ]; then printf 'ok    %s\n' "$1"; else printf 'FAIL  %s (exit %s) %s\n' "$1" "$rc" "$out"; fail=1; fi
}

CLI=$SHUT/.claude/skills/checks/bin/checks
check 'Read of a CLI source: refused'              2 Read file_path "$CLI"
check 'Read of a CLI test: passes'                 0 Read file_path "$SHUT/.claude/skills/checks/tests/test_checks.py"
check 'Read of a SKILL.md: passes'                 0 Read file_path "$SHUT/.claude/skills/checks/SKILL.md"
check 'cat of a CLI source: refused'               2 Bash command "cat $CLI"
check 'sed -n over a CLI source: refused'          2 Bash command "sed -n 1,80p $CLI | head"
check 'rg in a CLI source: refused'                2 Bash command "x=1; rg -n lock $CLI"
check 'relative path from the checkout: refused'   2 Bash command "cat .claude/skills/checks/bin/checks"
check 'Read of a CLI module: refused'              2 Read file_path "$SHUT/$LIB/hook.py"
check 'cat of a CLI module: refused'               2 Bash command "cat $SHUT/$LIB/hook.py"
check 'rg over the module directory: refused'      2 Bash command "rg -n redact $SHUT/.claude/skills/ops/lib"
check 'relative module glob: refused'              2 Bash command "cat $LIB/*.py"
check 'another lib directory: passes'              0 Bash command "cat scripts/ops/lib/common.sh"
check 'marker opens Read of a CLI module'          0 Read file_path "$OPEN/$LIB/hook.py"
check 'marker opens a relative module cat by cwd'  0 Bash command "cat $LIB/hook.py" "$OPEN"
check 'running the CLI: passes'                    0 Bash command "$CLI fe typecheck"
check 'running the CLI help: passes'               0 Bash command "$CLI help | head -40"
check 'rg that only names the CLI in a pattern'    0 Bash command "rg -n 'skills/checks/bin/checks' docs/"
check 'another tool: passes'                       0 Edit file_path "$CLI"
check 'marker opens Read in that checkout'         0 Read file_path "$OPEN/.claude/skills/checks/bin/checks"
check 'marker opens a relative cat by cwd'         0 Bash command "cat .claude/skills/checks/bin/checks" "$OPEN"
out=$(jq -cn --arg v "$CLI" '{tool_name:"Read", tool_input:{file_path:$v}}' | "$HOOK" 2>&1)
case $out in *"$CLI help"*) printf 'ok    %s\n' 'the refusal names the help command' ;; *) printf 'FAIL  message: %s\n' "$out"; fail=1 ;; esac

out=$(jq -cn --arg v "$SHUT/$LIB/hook.py" '{tool_name:"Read", tool_input:{file_path:$v}}' | "$HOOK" 2>&1)
case $out in *"$SHUT/.claude/skills/ops/bin/ops help"*) printf 'ok    %s\n' 'a module refusal names the CLI help' ;; *) printf 'FAIL  message: %s\n' "$out"; fail=1 ;; esac

[ "$fail" = 0 ] && echo "PASS cli-source-guard scenarios" || echo "FAIL cli-source-guard scenarios"
exit $fail

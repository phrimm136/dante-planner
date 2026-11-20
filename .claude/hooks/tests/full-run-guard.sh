#!/usr/bin/env bash
# Scenarios for .claude/hooks/full-run-guard.sh.
set -uo pipefail

HOOK=$(realpath "$(dirname "$0")/../full-run-guard.sh")
fail=0

check() {  # check <name> <want exit> <tool> <field> <value>
  local out rc
  out=$(jq -cn --arg t "$3" --arg f "$4" --arg v "$5" '{tool_name:$t, tool_input:{($f):$v}}' | "$HOOK" 2>&1)
  rc=$?
  if [ "$rc" = "$2" ]; then printf 'ok    %s\n' "$1"; else printf 'FAIL  %s (exit %s) %s\n' "$1" "$rc" "$out"; fail=1; fi
}

CLI=/w/.claude/skills/checks/bin/checks
PLANTED=/w/scripts/consistency/planted-bugs.sh
check 'checks --full before the lane: refused'        2 Bash command "$CLI --full \"the merge touched every page\" be check"
check 'checks --full after the lane: refused'         2 Bash command "$CLI be pit --full \"rebase\""
check 'checks --full after --root: refused'           2 Bash command "$CLI --root /x --full r fe test"
check 'relative checks path with --full: refused'     2 Bash command ".claude/skills/checks/bin/checks --full r e2e run"
check 'checks --full under flock: refused'            2 Bash command "flock /l/.test.lock $CLI --full r be test"
check 'checks --full under setsid nohup: refused'     2 Bash command "setsid nohup $CLI --full r fe gate > /tmp/x.log 2>&1"
check 'checks --full in a second command: refused'    2 Bash command "echo start; $CLI --full r be check"
check 'planted-bugs.sh all: refused'                  2 Bash command "$PLANTED all --seeds 20"
check 'planted-bugs.sh all under flock: refused'      2 Bash command "flock /l/.test.lock scripts/consistency/planted-bugs.sh all"
check 'double-quoted checks path: refused'           2 Bash command "\"$CLI\" --full r be check"
check 'single-quoted checks path: refused'           2 Bash command "'$CLI' be pit --full \"rebase; then | merge\""
check 'separators inside a quoted reason: refused'   2 Bash command "$CLI --full \"a reason with ; and | inside\" be check"
check 'double-quoted planted-bugs.sh all: refused'   2 Bash command "\"$PLANTED\" all --seeds 20"
check 'quoted message naming a run: passes'          0 Bash command "git commit -m \"ran $CLI --full r be check by mistake\""
check 'echo of a quoted run: passes'                 0 Bash command "echo \"x; $CLI --full r\""
check 'flag text inside a quoted argument: passes'   0 Bash command "$CLI be test --tests \"*Foo --full*\""
check 'here-string naming a run: passes'             0 Bash command "cat <<< $CLI --full r be check"
check 'checks changed: passes'                      0 Bash command "$CLI changed"
check 'checks be test --tests: passes'                0 Bash command "$CLI be test --tests '*FreshnessReadIT'"
check 'checks fe test with a file: passes'            0 Bash command "$CLI fe test src/a.test.ts"
check 'checks --print --full: passes'                 0 Bash command "$CLI --print --full r be check"
check '--full in another simple command: passes'      0 Bash command "$CLI be check; echo --full"
check 'quoted mention in a commit message: passes'    0 Bash command 'git commit -m "checks be check --full was dropped"'
check 'quoted rg pattern: passes'                     0 Bash command "rg -n '\\-\\-full' .claude/skills/checks/SKILL.md"
check 'heredoc commit message mention: passes'        0 Bash command "git commit -F - <<'EOF'
chore: stop running $CLI --full r be check
EOF"
check 'run after a heredoc: refused'                  2 Bash command "cat <<EOF > /tmp/x
text
EOF
$CLI --full r be check"
check 'planted-bugs.sh check: passes'                 0 Bash command "$PLANTED check"
check 'planted-bugs.sh one variant: passes'           0 Bash command "$PLANTED thread-read-unscoped --seeds 20"
check 'planted-bugs.sh clean: passes'                 0 Bash command "$PLANTED clean --seeds 20"
check 'another tool: passes'                          0 Read file_path "/w/notes/--full/$CLI"
check 'missing command: passes'                       0 Bash description "no command"
out=$(jq -cn --arg v "$CLI --full \"the merge touched every page\" be check" '{tool_name:"Bash", tool_input:{command:$v}}' | "$HOOK" 2>&1)
case $out in *"$CLI changed"*) printf 'ok    %s\n' 'the checks refusal names checks changed' ;; *) printf 'FAIL  message: %s\n' "$out"; fail=1 ;; esac
out=$(jq -cn --arg v "$PLANTED all --seeds 20" '{tool_name:"Bash", tool_input:{command:$v}}' | "$HOOK" 2>&1)
case $out in *"planted-bugs.sh <variant>"*) printf 'ok    %s\n' 'the planted refusal names a variant run' ;; *) printf 'FAIL  message: %s\n' "$out"; fail=1 ;; esac
out=$(printf 'not json' | "$HOOK" 2>&1)
rc=$?
if [ "$rc" = 0 ]; then printf 'ok    %s\n' 'bad JSON: passes'; else printf 'FAIL  bad JSON (exit %s) %s\n' "$rc" "$out"; fail=1; fi

[ "$fail" = 0 ] && echo "PASS full-run-guard scenarios" || echo "FAIL full-run-guard scenarios"
exit $fail

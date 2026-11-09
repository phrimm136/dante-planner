#!/usr/bin/env bash
# Scenarios for the account-id check in .githooks/pre-commit, run in a throwaway repo under $TMPDIR.
set -uo pipefail

HOOK=$(realpath "$(dirname "$0")/../pre-commit")
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t

TMP=$(mktemp -d "${TMPDIR:-/tmp}/precommit.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
R=$TMP/repo
ERR=$TMP/stderr
CONFIG=$TMP/aws-config
FAKE_ID=444455556666
fail=0

g() { git -C "$R" "$@"; }
check() {  # check <name> <condition...>
  local name=$1; shift
  if "$@"; then printf 'ok    %s\n' "$name"; else printf 'FAIL  %s\n' "$name"; sed 's/^/        /' "$ERR"; fail=1; fi
}
has() { grep -qF -- "$1" "$ERR"; }
lacks() { ! grep -qF -- "$1" "$ERR"; }
eq() { [ "$1" = "$2" ]; }
run_hook() {  # run_hook <config path>; sets rc
  (cd "$R" && HOME=$TMP/home AWS_CONFIG_FILE=$1 bash "$HOOK") 2>"$ERR"; rc=$?
}

git init -q -b main "$R"
mkdir -p "$TMP/home"
printf '[profile prod]\nsso_session = s\nsso_account_id = %s\n' "$FAKE_ID" > "$CONFIG"
printf 'first\n' > "$R/notes.txt"
g add notes.txt
g commit -q -m 'chore: root' --no-verify

printf 'owner %s\n' "$FAKE_ID" >> "$R/notes.txt"
g add notes.txt
run_hook "$CONFIG"
check 'staged id: refused' [ "$rc" -ne 0 ]
check 'staged id: names file and line' has '  notes.txt:2'
check 'staged id: never prints the id' lacks "$FAKE_ID"
check 'staged id: names the fix' has 'Replace it with <account:PROFILE>'

run_hook "$TMP/absent-config"
check 'no config file: passes' eq "$rc" 0
check 'no config file: silent' eq "$(wc -c < "$ERR" | tr -d ' ')" 0

g reset -q
printf 'first\nowner <account:prod>\n' > "$R/notes.txt"
g add notes.txt
run_hook "$CONFIG"
check 'same file without the id: passes' eq "$rc" 0

g commit -q -m 'chore: redacted owner' --no-verify
printf 'owner %s\n' "$FAKE_ID" > "$R/legacy.txt"
g add legacy.txt
g commit -q -m 'chore: legacy line' --no-verify
g rm -q legacy.txt
run_hook "$CONFIG"
check 'deleting a line that holds the id: passes' eq "$rc" 0

mkdir -p "$R/docs/adr"
printf '# 001 x\n\n## Decisions\n- @x account 999988887777 here\n\n## Takeaway\n- takeaway: x\n' > "$R/docs/adr/001-x.md"
g add docs/adr/001-x.md
run_hook "$TMP/absent-config"
check 'ADR 12-digit check still refuses' eq "$((rc != 0))" 1
check 'ADR 12-digit check names the rule' has 'account id'

[ "$fail" = 0 ] && echo "PASS pre-commit account-id scenarios" || echo "FAIL pre-commit account-id scenarios"
exit $fail

#!/usr/bin/env bash
# Scenarios for the ADR Re-check kind check in .githooks/pre-commit, run in a throwaway repo under $TMPDIR.
set -uo pipefail

SOURCE_ROOT=$(realpath "$(dirname "$0")/../..")
HOOK=$SOURCE_ROOT/.githooks/pre-commit
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t

TMP=$(mktemp -d "${TMPDIR:-/tmp}/precommit-adr.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
R=$TMP/repo
ERR=$TMP/stderr
fail=0

g() { git -C "$R" "$@"; }
check() {  # check <name> <condition...>
  local name=$1; shift
  if "$@"; then printf 'ok    %s\n' "$name"; else printf 'FAIL  %s\n' "$name"; sed 's/^/        /' "$ERR"; fail=1; fi
}
has() { grep -qF -- "$1" "$ERR"; }
eq() { [ "$1" = "$2" ]; }
run_hook() {  # sets rc
  (cd "$R" && HOME=$TMP/home TMPDIR=$TMP/hook-tmp AWS_CONFIG_FILE=$TMP/absent-config bash "$HOOK") 2>"$ERR"; rc=$?
}
write_adr() {  # write_adr <re-check text>
  printf '# 001 x\n\n## Decisions\n- @x — A decision. %s\n\n## Takeaway\n- takeaway: x\n' "$1" > "$R/docs/adr/001-x.md"
  g add docs/adr/001-x.md
}

git init -q -b main "$R"
mkdir -p "$TMP/home" "$TMP/hook-tmp" "$R/docs/adr" "$R/.claude/skills/debt-ledger/bin"
cp "$SOURCE_ROOT/.claude/skills/debt-ledger/bin/debt" "$R/.claude/skills/debt-ledger/bin/debt"
printf 'first\n' > "$R/notes.txt"
g add notes.txt .claude
g commit -q -m 'chore: root' --no-verify

write_adr 'Re-check: `true`.'
run_hook
check 'unmarked Re-check: refused' [ "$rc" -ne 0 ]
check 'unmarked Re-check: names the line' has 'unmarked: 001:4'
check 'unmarked Re-check: names the fix' has 'Re-check [<kind>]:'
check 'refused run: leaves no temp directory' eq "$(ls -A "$TMP/hook-tmp" | wc -l | tr -d ' ')" 0

write_adr 'Re-check [assert]: `true` and `false` both hold.'
run_hook
check 'assert with a second command: refused' [ "$rc" -ne 0 ]
check 'assert with a second command: names the fix' has 'make the command the whole check'

write_adr 'Re-check [assert]: `ls docs`.'
run_hook
check 'assert outside the allowlist: refused' [ "$rc" -ne 0 ]
check 'assert outside the allowlist: names the line' has 'malformed: 001:4'

write_adr 'Re-check [assert]: `false`.'
run_hook
check 'marked Re-check: passes without running the command' eq "$rc" 0

write_adr 'Re-check [manual]: a build shows it.'
run_hook
check 'marked manual Re-check: passes' eq "$rc" 0
check 'passing run: leaves no temp directory' eq "$(ls -A "$TMP/hook-tmp" | wc -l | tr -d ' ')" 0

[ "$fail" = 0 ] && echo "PASS pre-commit ADR Re-check scenarios" || echo "FAIL pre-commit ADR Re-check scenarios"
exit $fail

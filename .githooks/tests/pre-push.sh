#!/usr/bin/env bash
# Scenarios for .githooks/pre-push, run in a throwaway repo and bare remote under $TMPDIR.
set -uo pipefail

HOOK=$(realpath "$(dirname "$0")/../pre-push")
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t

TMP=$(mktemp -d "${TMPDIR:-/tmp}/prepush.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
R=$TMP/repo
WT=$TMP/wt
REMOTE=$TMP/remote.git
ERR=$TMP/stderr
REVIEWS=$R/.claude/reports/reviews
fail=0

g() { git -C "$R" "$@"; }
tip() { git -C "$R" rev-parse "$1"; }
remote_tip() { git -C "$REMOTE" rev-parse -q --verify "refs/heads/$1" || echo none; }
pid() { g diff-tree -p "$1" | g patch-id --stable | cut -d' ' -f1; }
subject() { g log -1 --format=%s "$1"; }
short() { g log -1 --format=%h "$1"; }
change() { echo "$2" >> "$R/$1"; g add "$1"; g commit -q -m "$3"; }
check() {  # check <name> <condition...>
  local name=$1; shift
  if "$@"; then printf 'ok    %s\n' "$name"; else printf 'FAIL  %s\n' "$name"; sed 's/^/        /' "$ERR"; fail=1; fi
}
has() { grep -qF -- "$1" "$ERR"; }
eq() { [ "$1" = "$2" ]; }
record() {  # record <file> <section> <sha>...
  local file=$REVIEWS/$1 section=$2; shift 2
  [ -e "$file" ] || printf 'branch: x\npatch-ids:\n' > "$file"
  [ "$section" = delta ] && printf 'delta:\n' >> "$file"
  for c in "$@"; do printf '  - %s %s\n' "$(pid "$c")" "$(subject "$c")" >> "$file"; done
}
push() { g push -q origin "$@" 2>"$ERR"; }

git init -q --bare -b main "$REMOTE"
git init -q -b main "$R"
mkdir -p "$R/.githooks" "$REVIEWS"
cp "$HOOK" "$R/.githooks/pre-push"
printf '.githooks/\n.claude/\n' >> "$R/.git/info/exclude"
g remote add origin "$REMOTE"
change a.txt a 'feat: root'
change a.txt b 'feat: second'
g push -q -u origin main 2>"$ERR"
g config core.hooksPath "$R/.githooks"

g switch -q -c feat/x
change x.txt x 'feat(x): add x'
B=$(tip feat/x)
push feat/x; rc=$?
check 'unreviewed branch: exit non-zero' [ "$rc" -ne 0 ]
check 'unreviewed branch: nothing reaches the remote' eq "$(remote_tip feat/x)" none
check 'unreviewed branch: count line' has 'push: 1 commit(s) without a review record'
check 'unreviewed branch: names the commit' has "  $(short "$B") feat(x): add x"
check 'unreviewed branch: names the next action' has 'have the reviewer agent review the range, or run it yourself: git -c core.hooksPath= push <your arguments>'

record feat__x.md patch-ids "$B"
push feat/x; rc=$?
check 'reviewed branch: pushed' eq "$(remote_tip feat/x):$rc" "$B:0"

g switch -q main
change m.txt m 'feat: main moves'
record main.md patch-ids main
push main
g switch -q feat/x
g rebase -q main 2>"$ERR"
push --force feat/x; rc=$?
check 'rebased branch: the pre-rebase record still admits it' eq "$(remote_tip feat/x):$rc" "$(tip feat/x):0"

change x.txt fix 'fix(x): review finding'
F=$(tip feat/x)
push feat/x; rc=$?
check 'fix commit without a delta: refused' eq "$(remote_tip feat/x):$((rc != 0))" "$(tip feat/x^):1"
check 'fix commit without a delta: names only the fix' eq "$(grep -c 'add x' "$ERR"):$(grep -c 'review finding' "$ERR")" "0:1"
record feat__x.md delta "$F"
push feat/x; rc=$?
check 'fix commit with a delta: pushed' eq "$(remote_tip feat/x):$rc" "$F:0"

g switch -q main
g merge -q --no-ff feat/x -m 'merge feat/x'
push main; rc=$?
check 'merge commit of reviewed commits: pushed' eq "$(remote_tip main):$rc" "$(tip main):0"

g switch -q -c feat/empty
g commit -q --allow-empty -m 'chore: empty'
push feat/empty; rc=$?
check 'empty commit: exempt' eq "$(remote_tip feat/empty):$rc" "$(tip feat/empty):0"

push --delete feat/empty; rc=$?
check 'branch deletion: exempt' eq "$(remote_tip feat/empty):$rc" "none:0"

g switch -q -c feat/tagged main
change t.txt t 'feat(t): unreviewed'
g tag v1
push v1; rc=$?
check 'tag: exempt' eq "$(git -C "$REMOTE" rev-parse -q --verify refs/tags/v1):$rc" "$(tip v1):0"
push feat/tagged; rc=$?
check 'the same commit as a branch: refused' eq "$(remote_tip feat/tagged):$((rc != 0))" "none:1"

g worktree add -q "$WT" -b feat/wt main 2>"$ERR"
echo w > "$WT/w.txt"; git -C "$WT" add w.txt; git -C "$WT" commit -q -m 'feat(w): from a worktree'
git -C "$WT" push -q origin feat/wt 2>"$ERR"; rc=$?
check 'push from a worktree without a record: refused' eq "$(remote_tip feat/wt):$((rc != 0))" "none:1"
record feat__wt.md patch-ids feat/wt
git -C "$WT" push -q origin feat/wt 2>"$ERR"; rc=$?
check 'push from a worktree reads the main checkout records' eq "$(remote_tip feat/wt):$rc" "$(tip feat/wt):0"

rm -rf "$REVIEWS"
g switch -q -c feat/norec main
change n.txt n 'feat(n): no records at all'
push feat/norec; rc=$?
check 'absent reviews directory: treated as empty' eq "$(remote_tip feat/norec):$((rc != 0))" "none:1"

g update-ref -d refs/remotes/origin/main
g switch -q -c feat/noorigin main
push feat/noorigin; rc=$?
check 'no origin/main ref: commits on another remote branch pass' eq "$(remote_tip feat/noorigin):$rc" "$(tip feat/noorigin):0"

[ "$fail" = 0 ] && echo "PASS pre-push scenarios" || echo "FAIL pre-push scenarios"
exit $fail

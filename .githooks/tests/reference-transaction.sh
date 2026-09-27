#!/usr/bin/env bash
# Scenarios for .githooks/reference-transaction, run in a throwaway repo under $TMPDIR.
set -uo pipefail

HOOK=$(realpath "$(dirname "$0")/../reference-transaction")
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t

TMP=$(mktemp -d "${TMPDIR:-/tmp}/reftx.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
R=$TMP/repo
WT=$TMP/wt
ERR=$TMP/stderr
REVIEWS=$R/.claude/reports/reviews
fail=0

g() { git -C "$R" "$@"; }
tip() { git -C "$R" rev-parse "$1"; }
pid() { g diff-tree -p "$1" | g patch-id --stable | cut -d' ' -f1; }
subject() { g log -1 --format=%s "$1"; }
short() { g log -1 --format=%h "$1"; }
change() { echo "$2" >> "$R/$1"; g add "$1"; g commit -q -m "$3"; }
check() {  # check <name> <condition...>
  local name=$1; shift
  if "$@"; then printf 'ok    %s\n' "$name"; else printf 'FAIL  %s\n' "$name"; sed 's/^/        /' "$ERR"; fail=1; fi
}
has() { rg -qF -- "$1" "$ERR"; }
lacks() { ! rg -qF -- "$1" "$ERR"; }
eq() { [ "$1" = "$2" ]; }
record() {  # record <file> <section> <sha>...
  local file=$REVIEWS/$1 section=$2; shift 2
  [ -e "$file" ] || printf 'branch: x\npatch-ids:\n' > "$file"
  [ "$section" = delta ] && printf 'delta:\n' >> "$file"
  for c in "$@"; do printf '  - %s %s\n' "$(pid "$c")" "$(subject "$c")" >> "$file"; done
}

git init -q -b dev "$R"
mkdir -p "$R/.githooks" "$REVIEWS"
cp "$HOOK" "$R/.githooks/reference-transaction"
printf '.githooks/\n.claude/\n' >> "$R/.git/info/exclude"
g config core.hooksPath "$R/.githooks"

change a.txt a 'feat: root'
check 'creating dev (zero old sha) is exempt' eq "$(subject dev)" 'feat: root'
A=$(tip dev)

g switch -q -c feat/x
change x.txt x 'feat(x): add x'
B=$(tip feat/x)
g switch -q dev
g merge -q --ff-only feat/x 2>"$ERR"; rc=$?
check 'unreviewed ff: exit non-zero' [ "$rc" -ne 0 ]
check 'unreviewed ff: ref stays' eq "$(tip dev)" "$A"
check 'unreviewed ff: count line' has 'landing: 1 commit(s) without a review record'
check 'unreviewed ff: names the commit' has "  $(short "$B") feat(x): add x"
check 'unreviewed ff: bypass line' has 'run it yourself: git -c core.hooksPath= <your command>'

record feat__x.md patch-ids "$B"
g merge -q --ff-only feat/x 2>"$ERR"; rc=$?
check 'reviewed ff: exit 0' eq "$rc" 0
check 'reviewed ff: ref moves' eq "$(tip dev)" "$B"

g switch -q -c feat/y "$A"
change y.txt y 'feat(y): add y'
D=$(tip feat/y)
record feat__y.md patch-ids "$D"
g rebase -q dev 2>"$ERR"
D2=$(tip feat/y)
check 'rebase: new sha' [ "$D" != "$D2" ]
check 'rebase: same patch-id' eq "$(pid "$D")" "$(pid "$D2")"
g switch -q dev
g merge -q --ff-only feat/y 2>"$ERR"; rc=$?
check 'rebased branch lands on the pre-rebase record' eq "$(tip dev):$rc" "$D2:0"

g switch -q -c feat/z
change z.txt z 'feat(z): add z'
E=$(tip feat/z)
record feat__z.md patch-ids "$E"
change z.txt z2 'fix(z): address review'
F=$(tip feat/z)
g switch -q dev
before=$(tip dev)
g merge -q --ff-only feat/z 2>"$ERR"; rc=$?
check 'fix commit without delta: refused' eq "$(tip dev):$((rc != 0))" "$before:1"
check 'fix commit without delta: names the fix' has "  $(short "$F") fix(z): address review"
check 'fix commit without delta: reviewed commit not named' lacks 'feat(z): add z'
check 'fix commit without delta: counts one' has 'landing: 1 commit(s)'
record feat__z.md delta "$F"
g merge -q --ff-only feat/z 2>"$ERR"; rc=$?
check 'fix commit with delta: lands' eq "$(tip dev):$rc" "$F:0"

before=$(tip dev)
echo direct >> "$R/a.txt"; g add a.txt
g commit -q -m 'docs: direct on dev' 2>"$ERR"; rc=$?
check 'direct commit on dev: refused' eq "$(tip dev):$((rc != 0))" "$before:1"
check 'direct commit on dev: names the commit' has 'docs: direct on dev'
g reset -q --hard

g reset -q --hard dev^ 2>"$ERR"; rc=$?
check 'rollback: exempt' eq "$(tip dev):$rc" "$(tip "$F^"):0"
g reset -q --hard "$F" 2>"$ERR"; rc=$?
check 'roll forward to reviewed tip: lands' eq "$(tip dev):$rc" "$F:0"

g switch -q -c main "$A"
change m.txt m1 'fix: hotfix one'
change m.txt m2 'fix: hotfix two'
g update-ref refs/remotes/origin/main main
g switch -q dev
g merge -q --no-ff origin/main -m 'chore: sync main' 2>"$ERR"; rc=$?
check 'release sync: exempt' eq "$rc:$(tip dev^2)" "0:$(tip main)"

g switch -q -c feat/m
change n.txt n 'feat(m): add n'
G=$(tip feat/m)
g switch -q dev
before=$(tip dev)
g merge -q --no-ff feat/m -m 'merge feat/m' 2>"$ERR"; rc=$?
check 'non-ff merge of unreviewed branch: refused' eq "$(tip dev):$((rc != 0))" "$before:1"
check 'non-ff merge: merge commit not counted' has 'landing: 1 commit(s)'
g merge --abort 2>/dev/null; g reset -q --hard
record feat__m.md patch-ids "$G"
g merge -q --no-ff feat/m -m 'merge feat/m' 2>"$ERR"; rc=$?
check 'non-ff merge of reviewed branch: lands' eq "$rc:$(tip dev^2)" "0:$G"

g switch -q -c feat/e
change e.txt e 'feat(e): add e'
g commit -q --allow-empty -m 'chore(e): empty marker'
g switch -q dev
g merge -q --ff-only feat/e 2>"$ERR"
check 'empty commit: counted before its sibling is reviewed' has 'landing: 1 commit(s)'
record feat__e.md patch-ids "feat/e^"
g merge -q --ff-only feat/e 2>"$ERR"; rc=$?
check 'empty commit: lands without a record entry' eq "$(tip dev):$rc" "$(tip feat/e):0"

g switch -q -c feat/w
change w.txt w 'feat(w): add w'
H=$(tip feat/w)
record feat__w.md patch-ids "$H"
g switch -q --detach
g worktree add -q "$WT" dev
before=$(tip dev)
git -C "$WT" merge -q --ff-only feat/w 2>"$ERR"; rc=$?
check 'worktree without the record: refused' eq "$(tip dev):$((rc != 0))" "$before:1"
check 'worktree without the record: names the commit' has 'feat(w): add w'
mkdir -p "$WT/.claude/reports/reviews"
cp "$REVIEWS/feat__w.md" "$WT/.claude/reports/reviews/"
git -C "$WT" merge -q --ff-only feat/w 2>"$ERR"; rc=$?
check 'worktree with the record: lands' eq "$(tip dev):$rc" "$H:0"
g worktree remove --force "$WT"

g switch -q -c unreviewed "$A"
change u.txt u 'feat: never reviewed'
U=$(tip unreviewed)
g branch -q -D dev 2>"$ERR"; rc=$?
check 'deleting dev (zero new sha): exempt' [ "$rc" -eq 0 ] 
g branch -q dev "$U" 2>"$ERR"; rc=$?
check 'creating dev at an unreviewed commit: exempt' eq "$(tip dev):$rc" "$U:0"

g switch -q feat/x
change x.txt x2 'feat(x): unreviewed follow-up'
check 'other ref refs/heads/feat/x: untouched' eq "$(subject feat/x)" 'feat(x): unreviewed follow-up'
g update-ref refs/remotes/origin/dev "$A"
g update-ref refs/remotes/origin/dev "$(tip feat/x)" "$A" 2>"$ERR"; rc=$?
check 'other ref refs/remotes/origin/dev: untouched' eq "$(tip origin/dev):$rc" "$(tip feat/x):0"

g update-ref refs/heads/dev "$A" "$U"
g update-ref refs/heads/dev "$U" 2>"$ERR"; rc=$?
check 'update-ref without old value to an unreviewed commit: refused' eq "$(tip dev):$((rc != 0))" "$A:1"
check 'update-ref without old value: names the commit' has 'feat: never reviewed'
g branch -f dev "$U" 2>"$ERR"; rc=$?
check 'branch -f to an unreviewed commit: refused' eq "$(tip dev):$((rc != 0))" "$A:1"
g checkout -q -B dev "$U" 2>"$ERR"; rc=$?
check 'checkout -B to an unreviewed commit: refused' eq "$(tip dev):$((rc != 0))" "$A:1"
g switch -q -f feat/x
g branch -f dev "$B" 2>"$ERR"; rc=$?
check 'branch -f to a reviewed commit: lands' eq "$(tip dev):$rc" "$B:0"

R2=$TMP/fresh
git init -q -b main "$R2"
mkdir -p "$R2/.githooks"
cp "$HOOK" "$R2/.githooks/reference-transaction"
git -C "$R2" config core.hooksPath "$R2/.githooks"
echo f > "$R2/f.txt"; git -C "$R2" add f.txt; git -C "$R2" commit -q -m 'feat: fresh'
echo g >> "$R2/f.txt"; git -C "$R2" commit -q -am 'feat: fresh two'
git -C "$R2" branch dev 2>"$ERR"; rc=$?
check 'real create of dev in a repo without it: exempt' eq "$(git -C "$R2" rev-parse dev):$rc" "$(git -C "$R2" rev-parse main):0"

rm -rf "$REVIEWS"
g switch -q dev
g reset -q --hard "$A" 2>"$ERR"
g merge -q --ff-only unreviewed 2>"$ERR"; rc=$?
check 'absent reviews directory: treated as empty' eq "$(tip dev):$((rc != 0))" "$A:1"

[ "$fail" = 0 ] && echo "PASS reference-transaction scenarios" || echo "FAIL reference-transaction scenarios"
exit $fail

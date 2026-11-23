#!/usr/bin/env bash
set -uo pipefail

here=$(cd "$(dirname "$0")" && pwd)
repo=$(git -C "$here" rev-parse --show-toplevel)
script="$here/../bootstrap-state-to-s3.sh"
runner="$repo/scripts/ops/terraform-run.sh"

command -v terraform >/dev/null || {
  echo "bootstrap-state-to-s3 tests: no terraform binary on PATH" >&2
  exit 1
}

work=$(mktemp -d "${TMPDIR:-/tmp}/bootstrap-state-test.XXXXXX")
trap 'rm -rf "$work"' EXIT
export XDG_STATE_HOME="$work/xdg"
export TF_IN_AUTOMATION=1

# shellcheck source-path=SCRIPTDIR source=../bootstrap-state-to-s3.sh
source "$script"
set +e

passed=0
pass() {
  echo "PASS $1"
  passed=$((passed + 1))
}
fail() {
  echo "FAIL $1: $2"
  [ -n "${out:-}" ] && printf '%s\n' "$out" | sed 's/^/    /'
  exit 1
}

tfw() { TF_WORKSPACE=default "$runner" "$@"; }

write_config() {
  mkdir -p "$1"
  cat >"$1/main.tf" <<'EOF'
variable "v" {
  type    = string
  default = "a"
}

resource "terraform_data" "x" {
  count = 2
  input = var.v
}

resource "terraform_data" "y" {
  input = "fixed"
}
EOF
}

add_local_backend() {
  printf 'terraform {\n  backend "local" {}\n}\n' >"$1/backend.tf"
}

target() {
  write_config "$1"
  add_local_backend "$1"
}

tfvars() {
  printf 'v = "%s"\n' "$2" >"$1"
}

apply_in() {
  local dir=$1 ws=$2
  shift 2
  if [ "$ws" != default ]; then
    "$runner" -chdir="$dir" workspace select -or-create "$ws" >/dev/null || return 1
  fi
  local v
  for v in "$@"; do
    "$runner" -chdir="$dir" apply -auto-approve -input=false -no-color -var "v=$v" >/dev/null || return 1
  done
  "$runner" -chdir="$dir" workspace select default >/dev/null
}

producer() {
  write_config "$1"
  "$runner" -chdir="$1" init -input=false -no-color >/dev/null || return 1
}

remote_count() { tfw -chdir="$1" state list 2>/dev/null | awk 'NF' | wc -l | tr -d ' '; }
remote_lineage() { tfw -chdir="$1" state pull 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin)["lineage"])'; }
mode_of() { python3 -c 'import os,stat,sys; print(oct(stat.S_IMODE(os.stat(sys.argv[1]).st_mode))[2:])' "$1"; }

pa="$work/producer-a"
producer "$pa" && apply_in "$pa" default a && apply_in "$pa" ws1 x ws1 && apply_in "$pa" ws2 ws2 ||
  {
    echo "FAIL setup: producer-a apply"
    exit 1
  }
tfvars "$work/ws1.tfvars" ws1
tfvars "$work/a.tfvars" a
tfvars "$work/other.tfvars" other

# Scenario: both bootstrap stacks declare the S3 backend
out=$(python3 - "$repo" <<'EOF'
import re, sys
repo = sys.argv[1]
def block(path):
    text = open(path).read()
    m = re.search(r'backend "s3" \{.*?\n\s*\}', text, re.S)
    return m.group(0) if m else None
ref = block(f"{repo}/terraform/rds/main.tf")
for stack in ("state-backend", "iam-bootstrap"):
    got = block(f"{repo}/terraform/{stack}/versions.tf")
    want = ref.replace('"rds/terraform.tfstate"', f'"{stack}/terraform.tfstate"')
    if got != want:
        print(f"{stack}: backend block differs from terraform/rds's\n{got}")
        sys.exit(1)
EOF
) || fail "both bootstrap stacks declare the S3 backend" "block mismatch"
pass "both bootstrap stacks declare the S3 backend"

# Scenario: a workspace file is pushed and verified
s2="$work/s2"
target "$s2"
out=$( (push_state state-backend "$s2" acct-s2 remote-s2 "$work/ws1.tfvars" \
  "$pa/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s2.remote.tfstate") </dev/null 2>&1)
rc=$?
name="a workspace file is pushed and verified"
[ "$rc" -eq 0 ] || fail "$name" "exit $rc"
printf '%s\n' "$out" | grep -qx 'verified: state-backend remote-s2 3 resources, plan shows no change' ||
  fail "$name" "no verified line"
[ "$(remote_count "$s2")" = 3 ] || fail "$name" "remote holds $(remote_count "$s2") resources"
[ "$(mode_of "$XDG_STATE_HOME/danteplanner/tf-state-backup/state-backend.acct-s2.tfstate")" = 600 ] ||
  fail "$name" "backup is not mode 600"
[ "$(mode_of "$XDG_STATE_HOME/danteplanner/tf-state-backup")" = 700 ] || fail "$name" "backup dir is not mode 700"
pass "$name"

# Scenario: the default-workspace file is moved aside before init
s3="$work/s3"
producer "$s3" && apply_in "$s3" default b a || fail "default-workspace setup" "apply"
add_local_backend "$s3"
out=$( (push_state iam-bootstrap "$s3" acct-s3 remote-s3 "$work/a.tfvars" \
  "$s3/terraform.tfstate" -backend-config=path="$work/s3.remote.tfstate") </dev/null 2>&1)
rc=$?
name="the default-workspace file is moved aside before init"
[ "$rc" -eq 0 ] || fail "$name" "exit $rc"
[ ! -e "$s3/terraform.tfstate" ] || fail "$name" "the default file is still in the stack directory"
[ -s "$XDG_STATE_HOME/danteplanner/tf-state-backup/iam-bootstrap.acct-s3.tfstate" ] || fail "$name" "no backup"
[ "$(remote_count "$s3")" = 3 ] || fail "$name" "remote holds $(remote_count "$s3") resources"
pass "$name"

sb="$work/sb"
producer "$sb" && apply_in "$sb" default a && apply_in "$sb" ws1 ws1 && apply_in "$sb" ws2 ws2 ||
  fail "workspaces boundary setup" "apply"
add_local_backend "$sb"
out=$( (push_state iam-bootstrap "$sb" acct-sb remote-sb "$work/a.tfvars" \
  "$sb/terraform.tfstate" -backend-config=path="$work/sb.remote.tfstate") </dev/null 2>&1)
rc=$?
name="a stack directory holding workspaces is refused before init"
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
[ -s "$sb/terraform.tfstate" ] || fail "$name" "the default file was moved"
[ ! -e "$work/sb.remote.tfstate" ] && [ ! -e "$work/terraform.tfstate.d" ] || fail "$name" "the remote was written"
pass "$name"

sc="$work/sc"
target "$sc"
out=$( (push_state iam-bootstrap "$sc" acct-sc remote-sc "$work/a.tfvars" \
  "$sb/terraform.tfstate" -backend-config=path="$work/sc.remote.tfstate") </dev/null 2>&1)
rc=$?
name="from a clean stack directory the remote holds only the pushed default state"
[ "$rc" -eq 0 ] || fail "$name" "exit $rc"
[ "$("$runner" -chdir="$sc" workspace list 2>/dev/null | awk '/^[ *] /' | wc -l | tr -d ' ')" = 1 ] ||
  fail "$name" "the remote has more than the default workspace"
[ "$(remote_lineage "$sc")" = "$(file_lineage "$sb/terraform.tfstate")" ] || fail "$name" "the remote is not the default state"
[ -s "$sb/terraform.tfstate.d/ws1/terraform.tfstate" ] && [ -s "$sb/terraform.tfstate.d/ws2/terraform.tfstate" ] ||
  fail "$name" "a workspace file was touched"
pass "$name"

# Scenario: a rerun after the push resumes at verification
name="a rerun after the push resumes at verification"
before=$(remote_lineage "$s2")
out=$( (push_state state-backend "$s2" acct-s2 remote-s2 "$work/ws1.tfvars" \
  "$pa/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s2.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "exit $rc"
printf '%s\n' "$out" | grep -qx 'push: already done; state-backend/terraform.tfstate in remote-s2 holds the backup'"'"'s state (same lineage and serial)' ||
  fail "$name" "does not say the push was already done"
printf '%s\n' "$out" | grep -qx 'count: remote 3, file 3' || fail "$name" "no count line"
printf '%s\n' "$out" | grep -qx 'verified: state-backend remote-s2 3 resources, plan shows no change' ||
  fail "$name" "no verified line"
pass "$name"

# Scenario: a second run does not overwrite
name="a second run does not overwrite"
out=$( (push_state state-backend "$s2" acct-s2b remote-s2 "$work/ws1.tfvars" \
  "$pa/terraform.tfstate.d/ws2/terraform.tfstate" -backend-config=path="$work/s2.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "other file, exit 0"
printf '%s\n' "$out" | grep -q 'state-backend/terraform.tfstate in remote-s2 already holds' ||
  fail "$name" "the refusal for another file does not name the remote key"
[ "$(remote_lineage "$s2")" = "$before" ] || fail "$name" "the remote changed"
pass "$name"

# Scenario: the script runs for a second account in the same worktree with no manual step
name="the script runs for a second account in the same worktree with no manual step"
p7="$work/producer-7"
producer "$p7" && apply_in "$p7" ws1 ws1 || fail "$name" "setup apply"
s2_before=$(file_lineage "$work/s2.remote.tfstate")
out=$( (push_state state-backend "$s2" acct-s7 remote-s7 "$work/ws1.tfvars" \
  "$p7/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s7.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "exit $rc"
printf '%s\n' "$out" | grep -qx 'verified: state-backend remote-s7 3 resources, plan shows no change' ||
  fail "$name" "no verified line"
[ "$(remote_lineage "$s2")" = "$(file_lineage "$p7/terraform.tfstate.d/ws1/terraform.tfstate")" ] ||
  fail "$name" "the stack directory does not point at the second remote"
[ "$(file_lineage "$work/s2.remote.tfstate")" = "$s2_before" ] || fail "$name" "the first remote changed"
pass "$name"

# Scenario: files of different accounts are refused before the push
name="files of different accounts are refused before the push"
acct="$work/acct"
mkdir -p "$acct"
printf 'bucket = "danteplanner-tfstate-111111111111"\nregion = "us-west-2"\n' >"$acct/backend.one.hcl"
printf 'aws_account_id = "222222222222"\n' >"$acct/two.tfvars"
printf 'aws_account_id="111111111111"   # the account this stack applies into\n' >"$acct/one.tfvars"
printf 'region = "us-west-2"\n' >"$acct/none.tfvars"
p8="$work/producer-8"
producer "$p8" && apply_in "$p8" ws1 "arn:aws:iam::333333333333:role/a arn:aws:iam::111111111111:role/b" ||
  fail "$name" "setup apply"
no_ids() { ! printf '%s\n' "$out" | grep -qE '111111111111|222222222222|333333333333'; }

mkdir -p "$work/stub"
printf '#!/bin/sh\necho "stub terraform reached: $*"\nexit 97\n' >"$work/stub/terraform"
chmod +x "$work/stub/terraform"
out=$(PATH="$work/stub:$PATH" bash "$script" state-backend "$acct/backend.one.hcl" "$acct/two.tfvars" \
  "$p8/terraform.tfstate.d/ws1/terraform.tfstate" </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "tfvars of another account, exit 0"
printf '%s\n' "$out" | grep -q "$acct/backend.one.hcl and $acct/two.tfvars name different accounts" ||
  fail "$name" "does not name both files"
no_ids || fail "$name" "printed an account id"
[ ! -e "$XDG_STATE_HOME/danteplanner/tf-plan-logs/state-backend.one.init.log" ] || fail "$name" "it ran init"

out=$( (check_accounts "$acct/backend.one.hcl" "$acct/one.tfvars" "$pa/terraform.tfstate.d/ws2/terraform.tfstate") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "state of another account, exit 0"
printf '%s\n' "$out" | grep -q "$pa/terraform.tfstate.d/ws2/terraform.tfstate never mentions the account $acct/backend.one.hcl names" ||
  fail "$name" "does not name the state file and the backend file"
no_ids || fail "$name" "printed an account id"

out=$( (check_accounts "$acct/backend.one.hcl" "$acct/none.tfvars" "$p8/terraform.tfstate.d/ws1/terraform.tfstate") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "tfvars without aws_account_id, exit 0"
printf '%s\n' "$out" | grep -q "$acct/none.tfvars sets no aws_account_id" || fail "$name" "does not name the tfvars file"

out=$( (check_accounts "$acct/backend.one.hcl" "$acct/one.tfvars" "$p8/terraform.tfstate.d/ws1/terraform.tfstate") 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "matching files, exit $rc"
no_ids || fail "$name" "printed an account id"
pass "$name"

# Scenario: a drifted plan stops before anything is called verified
s5="$work/s5"
p5="$work/producer-5"
producer "$p5" && apply_in "$p5" ws1 ws1 || fail "drift setup" "apply"
target "$s5"
out=$( (push_state iam-bootstrap "$s5" acct-s5 remote-s5 "$work/other.tfvars" \
  "$p5/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s5.remote.tfstate") </dev/null 2>&1)
rc=$?
name="a drifted plan stops before anything is called verified"
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q 'pushed but not verified' || fail "$name" "does not say pushed but not verified"
printf '%s\n' "$out" | grep -q "plan -var-file=$work/other.tfvars" || fail "$name" "does not name the plan command"
printf '%s\n' "$out" | grep -q '^verified:' && fail "$name" "printed a verified line"
pass "$name"

# Scenario: local removal needs a verified move
name="local removal needs a verified move"
bk="$XDG_STATE_HOME/danteplanner/tf-state-backup"
p5_ws1="$p5/terraform.tfstate.d/ws1/terraform.tfstate"
[ -s "$bk/iam-bootstrap.acct-s5.tfstate" ] && [ ! -e "$bk/iam-bootstrap.acct-s5.tfstate.verified" ] ||
  fail "$name" "setup: the drifted move left a marker or no backup"
out=$( (remove_local iam-bootstrap "$p5_ws1") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "before verify, exit 0"
printf '%s\n' "$out" | grep -q "Run the move first: bootstrap-state-to-s3.sh iam-bootstrap <path>/backend.acct-s5.hcl <tfvars file> $p5_ws1" ||
  fail "$name" "does not name the move command"
[ -s "$p5_ws1" ] || fail "$name" "removed an unverified file"
[ -s "$bk/iam-bootstrap.acct-s3.tfstate.verified" ] || fail "$name" "setup: the verified move wrote no marker"
command cp -f "$bk/iam-bootstrap.acct-s3.tfstate.verified" "$bk/iam-bootstrap.acct-s5.tfstate.verified"
out=$( (remove_local iam-bootstrap "$p5_ws1") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "marker of another state, exit 0"
[ -s "$p5_ws1" ] || fail "$name" "removed a file whose marker belongs to another state"
pass "$name"

# Scenario: local removal needs the backup
name="local removal needs the backup"
ws2="$pa/terraform.tfstate.d/ws2/terraform.tfstate"
out=$( (remove_local state-backend "$pa/terraform.tfstate") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "no backup, exit 0"
[ -s "$pa/terraform.tfstate" ] || fail "$name" "removed a file with no backup"

p6="$work/producer-6"
s6="$work/s6"
producer "$p6" && apply_in "$p6" ws1 ws1 || fail "$name" "setup apply"
target "$s6"
out=$( (push_state state-backend "$s6" acct-s6 remote-s6 "$work/ws1.tfvars" \
  "$p6/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s6.remote.tfstate") </dev/null 2>&1) ||
  fail "$name" "setup push"
printf '\nresource "terraform_data" "z" {\n  input = "added"\n}\n' >>"$p6/main.tf"
apply_in "$p6" ws1 ws1 || fail "$name" "setup second apply"
out=$( (remove_local state-backend "$p6/terraform.tfstate.d/ws1/terraform.tfstate") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "count differs, exit 0"
[ -s "$p6/terraform.tfstate.d/ws1/terraform.tfstate" ] || fail "$name" "removed a file whose count differs"

ws1="$pa/terraform.tfstate.d/ws1/terraform.tfstate"
[ -s "$ws1.backup" ] || fail "$name" "setup: no .backup sibling"
out=$( (remove_local state-backend "$ws1") 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "matching backup, exit $rc"
printf '%s\n' "$out" | grep -qx "removed: $ws1" || fail "$name" "does not print the file"
printf '%s\n' "$out" | grep -qx "removed: $ws1.backup" || fail "$name" "does not print the .backup sibling"
marker="$XDG_STATE_HOME/danteplanner/tf-state-backup/state-backend.acct-s2.tfstate.verified"
printf '%s\n' "$out" | grep -qx "removed: $marker" && [ ! -e "$marker" ] || fail "$name" "the marker was not removed"
[ ! -e "$ws1" ] && [ ! -e "$ws1.backup" ] && [ ! -e "$pa/terraform.tfstate.d/ws1" ] ||
  fail "$name" "file, sibling or empty workspace directory left"
[ -s "$ws2" ] || fail "$name" "another workspace was touched"

out=$( (remove_local iam-bootstrap "$s3/terraform.tfstate") 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "moved default file, exit $rc"
printf '%s\n' "$out" | grep -qx "removed: $s3/terraform.tfstate.backup" || fail "$name" "moved default file: sibling not removed"
pass "$name"

echo "bootstrap-state-to-s3 tests: $passed passed"

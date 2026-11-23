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

track() {
  git -C "$1" init -q && git -C "$1" add -- '*.tf'
}

target() {
  write_config "$1"
  add_local_backend "$1"
  track "$1"
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
printf '%s\n' "$out" | grep -qx 'verified: state-backend remote-s2 3 resources, the bucket holds the backup'"'"'s state' ||
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
track "$s3"
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
printf '%s\n' "$out" | grep -qx 'push: already done; state-backend/terraform.tfstate in remote-s2 holds the backup'"'"'s resources and outputs (the bucket'"'"'s lineage and serial are not compared)' ||
  fail "$name" "does not say the push was already done"
printf '%s\n' "$out" | grep -qx 'count: remote 3, file 3' || fail "$name" "no count line"
printf '%s\n' "$out" | grep -qx 'verified: state-backend remote-s2 3 resources, the bucket holds the backup'"'"'s state' ||
  fail "$name" "no verified line"
pass "$name"

# Scenario: a second run does not overwrite
name="a second run does not overwrite"
out=$( (push_state state-backend "$s2" acct-s2b remote-s2 "$work/ws1.tfvars" \
  "$pa/terraform.tfstate.d/ws2/terraform.tfstate" -backend-config=path="$work/s2.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "other file, exit 0"
printf '%s\n' "$out" | grep -q 'state-backend/terraform.tfstate in remote-s2 holds 3 resources with other resources or outputs than the backup, under another lineage; nothing pushed.' ||
  fail "$name" "the refusal for another file does not name the remote key and the other content"
printf '%s\n' "$out" | grep -q '^push:' && fail "$name" "it pushed"
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
printf '%s\n' "$out" | grep -qx 'verified: state-backend remote-s7 3 resources, the bucket holds the backup'"'"'s state' ||
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
printf 'bucket = "danteplanner-tfstate-333333333333"\n' >"$acct/backend.three.hcl"
printf 'aws_account_id = "333333333333"\n' >"$acct/three.tfvars"
p8="$work/producer-8"
producer "$p8" && apply_in "$p8" ws1 "arn:aws:iam::111111111111:role/trusted-admin" ||
  fail "$name" "setup apply"
applied_in_three="$acct/applied-in-333.tfstate"
python3 - "$p8/terraform.tfstate.d/ws1/terraform.tfstate" "$applied_in_three" 333333333333 <<'EOF'
import json, sys
state = json.load(open(sys.argv[1]))
account = sys.argv[3]
state["resources"].append({
    "mode": "data", "type": "aws_caller_identity", "name": "current",
    "provider": 'provider["registry.terraform.io/hashicorp/aws"]',
    "instances": [{"schema_version": 0, "sensitive_attributes": [],
                   "attributes": {"account_id": account, "id": account,
                                  "arn": f"arn:aws:iam::{account}:user/applier", "user_id": "AIDAEXAMPLE"}}]})
json.dump(state, open(sys.argv[2], "w"))
EOF
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

out=$( (check_accounts "$acct/backend.one.hcl" "$acct/one.tfvars" "$applied_in_three") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "a state that only names the account in an ARN, exit 0"
printf '%s\n' "$out" | grep -q "$applied_in_three was applied in another account than the one $acct/backend.one.hcl names" ||
  fail "$name" "does not name the state file and the backend file"
no_ids || fail "$name" "printed an account id"

out=$( (check_accounts "$acct/backend.one.hcl" "$acct/one.tfvars" "$pa/terraform.tfstate.d/ws2/terraform.tfstate") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "a state without the caller identity, exit 0"
printf '%s\n' "$out" | grep -q "$pa/terraform.tfstate.d/ws2/terraform.tfstate holds no data.aws_caller_identity.current account_id" ||
  fail "$name" "does not name what the state lacks"

out=$( (check_accounts "$acct/backend.one.hcl" "$acct/none.tfvars" "$applied_in_three") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "tfvars without aws_account_id, exit 0"
printf '%s\n' "$out" | grep -q "$acct/none.tfvars sets no aws_account_id" || fail "$name" "does not name the tfvars file"

out=$( (check_accounts "$acct/backend.three.hcl" "$acct/three.tfvars" "$applied_in_three") 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "the account the state was applied in, exit $rc"
no_ids || fail "$name" "printed an account id"
pass "$name"

s5="$work/s5"
p5="$work/producer-5"
producer "$p5" && apply_in "$p5" ws1 ws1 || fail "plan failure setup" "apply"
target "$s5"
out=$( (push_state iam-bootstrap "$s5" acct-s5 remote-s5 "$work/missing.tfvars" \
  "$p5/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s5.remote.tfstate") </dev/null 2>&1)
rc=$?
name="a plan that fails stops before anything is called verified"
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q '^state: .* holds the backup'"'"'s resources and outputs' || fail "$name" "the state comparison did not run first"
printf '%s\n' "$out" | grep -q 'cannot be planned from the bucket. Pushed but not verified' || fail "$name" "does not say pushed but not verified"
printf '%s\n' "$out" | grep -q "plan -var-file=$work/missing.tfvars" || fail "$name" "does not name the plan command"
printf '%s\n' "$out" | grep -q '^verified:' && fail "$name" "printed a verified line"
pass "$name"

name="drift between the code and the account does not stop the move"
s12="$work/s12"
p12="$work/producer-12"
producer "$p12" && apply_in "$p12" ws1 ws1 || fail "$name" "setup apply"
target "$s12"
printf 'variable "v" {\n  type    = string\n  default = "a"\n}\n\nresource "terraform_data" "x" {\n  count            = 2\n  input            = var.v\n  triggers_replace = var.v\n}\n\nresource "terraform_data" "y" {\n  input = "fixed"\n}\n' >"$s12/main.tf"
out=$( (push_state iam-bootstrap "$s12" acct-s12 remote-s12 "$work/other.tfvars" \
  "$p12/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s12.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "exit $rc"
printf '%s\n' "$out" | grep -q '^plan: 2 to add, 0 to change, 2 to destroy; this is a difference between the code and the account that the move did not cause. Read ' ||
  fail "$name" "no drift line"
printf '%s\n' "$out" | grep -q '^WARNING: the plan destroys or replaces 2 resources. Read ' || fail "$name" "no destroy warning"
printf '%s\n' "$out" | grep -qx "verified: iam-bootstrap remote-s12 3 resources, the bucket holds the backup's state" ||
  fail "$name" "no verified line"
printf '%s\n' "$out" | grep -q 'fixed' && fail "$name" "printed a resource attribute"
pass "$name"

name="a bucket state that differs from the backup at the same lineage and serial is refused"
p13="$work/producer-13"
producer "$p13" && apply_in "$p13" ws1 ws1 || fail "$name" "setup apply"
command cp -R "$p13" "$work/producer-13b"
apply_in "$p13" ws1 left && apply_in "$work/producer-13b" ws1 right || fail "$name" "setup diverging applies"
[ "$(state_facts "$p13/terraform.tfstate.d/ws1/terraform.tfstate" | cut -d' ' -f2-)" = \
  "$(state_facts "$work/producer-13b/terraform.tfstate.d/ws1/terraform.tfstate" | cut -d' ' -f2-)" ] ||
  fail "$name" "setup: the copies do not share lineage and serial"
s13="$work/s13"
target "$s13"
printf 'v = "left"\n' >"$work/left.tfvars"
out=$( (push_state iam-bootstrap "$s13" acct-s13 remote-s13 "$work/left.tfvars" \
  "$p13/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s13.remote.tfstate") </dev/null 2>&1) ||
  fail "$name" "setup move"
copy_b="$work/producer-13b/terraform.tfstate.d/ws1/terraform.tfstate"
backup_13="$XDG_STATE_HOME/danteplanner/tf-state-backup/iam-bootstrap.acct-s13.tfstate"
out=$( (push_state iam-bootstrap "$s13" acct-s13 remote-s13 "$work/left.tfvars" \
  "$copy_b" -backend-config=path="$work/s13.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "same account, exit 0"
printf '%s\n' "$out" | grep -q "$copy_b and its backup $backup_13 are two different states at one serial" ||
  fail "$name" "does not refuse at the backup"
printf '%s\n' "$out" | grep -qE '^(backup|init|push):' && fail "$name" "it wrote before refusing"
[ "$(state_digest "$backup_13")" = "$(state_digest "$p13/terraform.tfstate.d/ws1/terraform.tfstate")" ] ||
  fail "$name" "the backup no longer holds copy A"
out=$( (remove_local iam-bootstrap "$copy_b") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "--remove-local on copy B, exit 0"
printf '%s\n' "$out" | grep -q "$copy_b has the verified lineage and serial but other resources or outputs" ||
  fail "$name" "--remove-local does not name the content difference"
[ -s "$copy_b" ] || fail "$name" "--remove-local deleted copy B"
out=$( (push_state iam-bootstrap "$s13" acct-s13-b remote-s13 "$work/left.tfvars" \
  "$copy_b" -backend-config=path="$work/s13.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "fresh backup, exit 0"
printf '%s\n' "$out" | grep -q 'holds the same lineage and serial as .* but different resources or outputs; nothing pushed.' ||
  fail "$name" "the bucket check does not name the content difference"
printf '%s\n' "$out" | grep -qE 'left|right' && fail "$name" "printed state content"
printf '%s\n' "$out" | grep -q '^verified:' && fail "$name" "printed a verified line"
pass "$name"

name="local removal refuses a local file with the verified lineage and serial but other content"
p15="$work/producer-15"
producer "$p15" && apply_in "$p15" ws1 ws1 || fail "$name" "setup apply"
command cp -R "$p15" "$work/producer-15b"
apply_in "$p15" ws1 left && apply_in "$work/producer-15b" ws1 right || fail "$name" "setup diverging applies"
s15="$work/s15"
target "$s15"
out=$( (push_state iam-bootstrap "$s15" acct-s15 remote-s15 "$work/left.tfvars" \
  "$p15/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s15.remote.tfstate") </dev/null 2>&1) ||
  fail "$name" "setup move"
copy_15b="$work/producer-15b/terraform.tfstate.d/ws1/terraform.tfstate"
out=$( (remove_local iam-bootstrap "$copy_15b") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q "Run the move again: bootstrap-state-to-s3.sh iam-bootstrap <path>/backend.acct-s15.hcl" ||
  fail "$name" "does not name the move command"
[ -s "$copy_15b" ] || fail "$name" "deleted the file"
pass "$name"

name="a marker in the old lineage-and-serial format is treated as unverified"
marker_15="$XDG_STATE_HOME/danteplanner/tf-state-backup/iam-bootstrap.acct-s15.tfstate.verified"
[ "$(wc -w <"$marker_15" | tr -d ' ')" = 3 ] || fail "$name" "setup: the verified move wrote no three-field marker"
read -r m_lineage m_serial _ <"$marker_15"
printf '%s %s\n' "$m_lineage" "$m_serial" >"$marker_15"
out=$( (remove_local iam-bootstrap "$p15/terraform.tfstate.d/ws1/terraform.tfstate") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q 'was never verified' || fail "$name" "does not call it unverified"
[ -s "$p15/terraform.tfstate.d/ws1/terraform.tfstate" ] || fail "$name" "deleted the file"
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

name="a failed push prints the script's message and no bucket name"
real_terraform=$(command -v terraform)
mkdir -p "$work/pushfail"
cat >"$work/pushfail/terraform" <<EOF
#!/bin/sh
case " \$* " in
  *" push "*)
    echo "Lock Info: Path: danteplanner-tfstate-999999999999/state-backend/terraform.tfstate"
    echo "Error acquiring the state lock: s3://danteplanner-tfstate-999999999999" >&2
    exit 1 ;;
esac
exec "$real_terraform" "\$@"
EOF
chmod +x "$work/pushfail/terraform"
p10="$work/producer-10"
s10="$work/s10"
producer "$p10" && apply_in "$p10" ws1 ws1 || fail "$name" "setup apply"
target "$s10"
out=$( (PATH="$work/pushfail:$PATH" push_state state-backend "$s10" acct-s10 remote-s10 "$work/ws1.tfvars" \
  "$p10/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s10.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q '^push: failed; the remote may be unchanged. Read ' || fail "$name" "no push failure line"
printf '%s\n' "$out" | grep -q '999999999999' && fail "$name" "the bucket name reached the output"
pass "$name"

p11="$work/producer-11"
s11="$work/s11"
p11_ws1="$p11/terraform.tfstate.d/ws1/terraform.tfstate"
producer "$p11" && apply_in "$p11" ws1 ws1 || fail "p11 setup" "apply"
target "$s11"
out=$( (push_state iam-bootstrap "$s11" acct-s11 remote-s11 "$work/ws1.tfvars" "$p11_ws1" \
  -backend-config=path="$work/s11.remote.tfstate") </dev/null 2>&1) || fail "p11 setup" "first move"
apply_in "$p11" ws1 other || fail "p11 setup" "second apply"

name="local removal refuses a local file that moved on at the same count"
[ "$(file_count "$p11_ws1")" = 3 ] || fail "$name" "setup: the count changed"
out=$( (remove_local iam-bootstrap "$p11_ws1") 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q "$p11_ws1 is newer than the verified copy" || fail "$name" "does not say the local file is newer"
printf '%s\n' "$out" | grep -q "Run the move again: bootstrap-state-to-s3.sh iam-bootstrap <path>/backend.acct-s11.hcl" ||
  fail "$name" "does not name the move command"
[ -s "$p11_ws1" ] || fail "$name" "removed the newer file"
pass "$name"

name="a rerun with a local file newer than the bucket pushes it as a move"
out=$( (push_state iam-bootstrap "$s11" acct-s11 remote-s11 "$work/other.tfvars" "$p11_ws1" \
  -backend-config=path="$work/s11.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "exit $rc"
printf '%s\n' "$out" | grep -q 'holds an older serial of the same state; pushing the newer local state over it' ||
  fail "$name" "does not name the newer-local case"
printf '%s\n' "$out" | grep -qx 'verified: iam-bootstrap remote-s11 3 resources, the bucket holds the backup'"'"'s state' || fail "$name" "no verified line"
[ "$(remote_lineage "$s11")" = "$(file_lineage "$p11_ws1")" ] || fail "$name" "the remote is not the local state"
out=$( (push_state iam-bootstrap "$s11" acct-s11 remote-s11 "$work/other.tfvars" "$p11_ws1" \
  -backend-config=path="$work/s11.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "rerun after the push over an older serial, exit $rc"
printf '%s\n' "$out" | grep -q '^push: already done' || fail "$name" "the rerun did not resume"
pass "$name"

name="a bucket state that differs from the backup after the push is not verified"
mkdir -p "$work/tamper"
cat >"$work/tamper/terraform" <<EOF
#!/bin/sh
case " \$* " in
  *" push "*)
    "$real_terraform" "\$@"; rc=\$?; touch "$work/tamper/pushed"; exit \$rc ;;
  *" pull "*)
    if [ -e "$work/tamper/pushed" ]; then
      "$real_terraform" "\$@" | python3 -c 'import json, sys; d = json.load(sys.stdin); d["resources"][0]["instances"][0]["attributes"]["input"] = "tampered"; json.dump(d, sys.stdout)'
      exit 0
    fi ;;
esac
exec "$real_terraform" "\$@"
EOF
chmod +x "$work/tamper/terraform"
p14="$work/producer-14"
s14="$work/s14"
producer "$p14" && apply_in "$p14" ws1 ws1 || fail "$name" "setup apply"
target "$s14"
out=$( (PATH="$work/tamper:$PATH" push_state iam-bootstrap "$s14" acct-s14 remote-s14 "$work/ws1.tfvars" \
  "$p14/terraform.tfstate.d/ws1/terraform.tfstate" -backend-config=path="$work/s14.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q '^push: ' || fail "$name" "setup: did not push"
printf '%s\n' "$out" | grep -q 'differs from the backup in content (resources or outputs). Pushed but not verified.' ||
  fail "$name" "does not name the content difference"
printf '%s\n' "$out" | grep -q 'tampered' && fail "$name" "printed state content"
[ ! -e "$XDG_STATE_HOME/danteplanner/tf-state-backup/iam-bootstrap.acct-s14.tfstate.verified" ] || fail "$name" "wrote a marker"
pass "$name"

name="a rerun with a local file older than the bucket is refused as older"
out=$( (push_state iam-bootstrap "$s11" acct-s11-old remote-s11 "$work/ws1.tfvars" "$p11_ws1.backup" \
  -backend-config=path="$work/s11.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q 'holds a newer serial of the same state than' || fail "$name" "does not name the older-local case"
pass "$name"

name="a local file behind its own backup does not replace it"
s11_backup="$XDG_STATE_HOME/danteplanner/tf-state-backup/iam-bootstrap.acct-s11.tfstate"
command cp -f "$s11_backup" "$work/s11-backup-before"
out=$( (push_state iam-bootstrap "$s11" acct-s11 remote-s11 "$work/ws1.tfvars" "$p11_ws1.backup" \
  -backend-config=path="$work/s11.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q "$p11_ws1.backup is behind its own backup $s11_backup" || fail "$name" "does not say the file is behind"
printf '%s\n' "$out" | grep -qE '^(backup|init|push):' && fail "$name" "it wrote before refusing"
cmp -s "$s11_backup" "$work/s11-backup-before" || fail "$name" "the backup was replaced"
pass "$name"

name="a bucket that stores the push under a new lineage at serial 1 still verifies, resumes and allows removal"
mkdir -p "$work/s3like"
cat >"$work/s3like/terraform" <<EOF
#!/bin/sh
case " \$* " in
  *" pull "*)
    "$real_terraform" "\$@" | python3 -c '
import json, sys
raw = sys.stdin.read()
if raw.strip():
    state = json.loads(raw)
    state["lineage"] = "00000000-0000-4000-8000-000000000000"
    state["serial"] = 1
    json.dump(state, sys.stdout)
'
    exit 0 ;;
esac
exec "$real_terraform" "\$@"
EOF
chmod +x "$work/s3like/terraform"
p16="$work/producer-16"
s16="$work/s16"
p16_ws1="$p16/terraform.tfstate.d/ws1/terraform.tfstate"
producer "$p16" && apply_in "$p16" ws1 x ws1 || fail "$name" "setup apply"
target "$s16"
out=$( (PATH="$work/s3like:$PATH" push_state iam-bootstrap "$s16" acct-s16 remote-s16 "$work/ws1.tfvars" "$p16_ws1" \
  -backend-config=path="$work/s16.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "first run, exit $rc"
printf '%s\n' "$out" | grep -q "^state: .*(content only; bucket lineage other, serial 1)$" || fail "$name" "first run: no content-only state line"
printf '%s\n' "$out" | grep -qx "verified: iam-bootstrap remote-s16 3 resources, the bucket holds the backup's state" ||
  fail "$name" "first run: no verified line"
out=$( (PATH="$work/s3like:$PATH" push_state iam-bootstrap "$s16" acct-s16 remote-s16 "$work/ws1.tfvars" "$p16_ws1" \
  -backend-config=path="$work/s16.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "rerun, exit $rc"
printf '%s\n' "$out" | grep -q '^push: already done' || fail "$name" "the rerun did not resume"
printf '%s\n' "$out" | grep -qx "verified: iam-bootstrap remote-s16 3 resources, the bucket holds the backup's state" ||
  fail "$name" "rerun: no verified line"
out=$( (remove_local iam-bootstrap "$p16_ws1") 2>&1)
rc=$?
[ "$rc" -eq 0 ] || fail "$name" "--remove-local, exit $rc"
[ ! -e "$p16_ws1" ] && [ ! -e "$p16_ws1.backup" ] || fail "$name" "--remove-local left the file or its sibling"
pass "$name"

p17="$work/producer-17"
p17_ws1="$p17/terraform.tfstate.d/ws1/terraform.tfstate"
producer "$p17" && apply_in "$p17" ws1 ws1 || fail "declaration setup" "apply"

name="a state of another stack is refused before backup, init and push"
s17="$work/s17"
mkdir -p "$s17"
printf 'resource "terraform_data" "unrelated" {\n  input = "x"\n}\n' >"$s17/main.tf"
add_local_backend "$s17"
track "$s17"
out=$( (push_state iam-bootstrap "$s17" acct-s17 remote-s17 "$work/ws1.tfvars" "$p17_ws1" \
  -backend-config=path="$work/s17.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q "holds resources iam-bootstrap does not declare, so it is another stack's state or the code dropped a resource: terraform_data.x, terraform_data.y; nothing done." ||
  fail "$name" "does not list the undeclared addresses"
printf '%s\n' "$out" | grep -qE '^(backup|init|push):' && fail "$name" "it wrote before refusing"
[ ! -e "$XDG_STATE_HOME/danteplanner/tf-state-backup/iam-bootstrap.acct-s17.tfstate" ] || fail "$name" "wrote a backup"
[ ! -e "$work/s17.remote.tfstate" ] || fail "$name" "the bucket was written"
pass "$name"

name="a state holding a resource the code no longer declares is refused and names it"
s18="$work/s18"
mkdir -p "$s18"
printf 'variable "v" {\n  type    = string\n  default = "a"\n}\n\nresource "terraform_data" "x" {\n  count = 2\n  input = var.v\n}\n' >"$s18/main.tf"
add_local_backend "$s18"
track "$s18"
out=$( (push_state iam-bootstrap "$s18" acct-s18 remote-s18 "$work/ws1.tfvars" "$p17_ws1" \
  -backend-config=path="$work/s18.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q 'or the code dropped a resource: terraform_data.y; nothing done.' || fail "$name" "does not name terraform_data.y alone"
[ ! -e "$work/s18.remote.tfstate" ] || fail "$name" "the bucket was written"
pass "$name"

name="a state whose instances the stack declares in another form is refused"
s19="$work/s19"
mkdir -p "$s19"
printf 'resource "terraform_data" "x" {\n  input = "a"\n}\n\nresource "terraform_data" "y" {\n  input = "fixed"\n}\n' >"$s19/main.tf"
add_local_backend "$s19"
track "$s19"
out=$( (push_state iam-bootstrap "$s19" acct-s19 remote-s19 "$work/ws1.tfvars" "$p17_ws1" \
  -backend-config=path="$work/s19.remote.tfstate") </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q 'declares in another form (count or for_each against a single instance), so it is another stack'"'"'s state: terraform_data.x; nothing done.' ||
  fail "$name" "does not name terraform_data.x"
[ ! -e "$work/s19.remote.tfstate" ] || fail "$name" "the bucket was written"
pass "$name"

name="the real state-backend stack refuses a state with resources it does not declare"
out=$(PATH="$work/stub:$PATH" bash "$script" state-backend "$acct/backend.three.hcl" "$acct/three.tfvars" \
  "$applied_in_three" </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q 'holds resources state-backend does not declare, so it is another stack'"'"'s state or the code dropped a resource: terraform_data.x, terraform_data.y; nothing done.' ||
  fail "$name" "does not name the undeclared addresses"
printf '%s\n' "$out" | grep -q 'stub terraform reached' && fail "$name" "it ran terraform"
pass "$name"

name="the real iam-bootstrap stack refuses a state-backend state as undeclared"
state_backend_like="$acct/state-backend-like.tfstate"
iam_bootstrap_like="$acct/iam-bootstrap-like.tfstate"
python3 - "$applied_in_three" "$state_backend_like" "$iam_bootstrap_like" <<'EOF'
import json, sys
state = json.load(open(sys.argv[1]))
caller = [r for r in state["resources"] if r["type"] == "aws_caller_identity"]
def managed(resource_type, name, index=None):
    instance = {"schema_version": 0, "sensitive_attributes": [], "attributes": {"id": "fixture"}}
    if index is not None:
        instance["index_key"] = index
    return {"mode": "managed", "type": resource_type, "name": name,
            "provider": 'provider["registry.terraform.io/hashicorp/aws"]', "instances": [instance]}
state["resources"] = caller + [managed(t, "tf_state") for t in (
    "aws_s3_bucket", "aws_s3_bucket_lifecycle_configuration", "aws_s3_bucket_public_access_block",
    "aws_s3_bucket_server_side_encryption_configuration", "aws_s3_bucket_versioning")]
json.dump(state, open(sys.argv[2], "w"))
state["resources"] = caller + [
    managed("aws_iam_openid_connect_provider", "github", 0),
    managed("aws_iam_policy", "deploy_surge"), managed("aws_iam_policy", "provisioning"),
    managed("aws_iam_role", "provisioner"),
    managed("aws_iam_role_policy_attachment", "deploy_surge"),
    managed("aws_iam_role_policy_attachment", "provisioning")]
json.dump(state, open(sys.argv[3], "w"))
EOF
out=$(PATH="$work/stub:$PATH" bash "$script" iam-bootstrap "$acct/backend.three.hcl" "$acct/three.tfvars" \
  "$state_backend_like" </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q "holds resources iam-bootstrap does not declare, so it is another stack's state or the code dropped a resource: aws_s3_bucket.tf_state, aws_s3_bucket_lifecycle_configuration.tf_state, aws_s3_bucket_public_access_block.tf_state, aws_s3_bucket_server_side_encryption_configuration.tf_state, aws_s3_bucket_versioning.tf_state; nothing done." ||
  fail "$name" "does not name the five bucket addresses"
printf '%s\n' "$out" | grep -qE '^(backup|init|push):' && fail "$name" "it wrote before refusing"
printf '%s\n' "$out" | grep -q 'stub terraform reached' && fail "$name" "it ran terraform"
[ ! -e "$XDG_STATE_HOME/danteplanner/tf-state-backup/iam-bootstrap.three.tfstate" ] || fail "$name" "wrote a backup"
pass "$name"

name="the real state-backend stack refuses an iam-bootstrap state as undeclared"
out=$(PATH="$work/stub:$PATH" bash "$script" state-backend "$acct/backend.three.hcl" "$acct/three.tfvars" \
  "$iam_bootstrap_like" </dev/null 2>&1)
rc=$?
[ "$rc" -ne 0 ] || fail "$name" "exit 0"
printf '%s\n' "$out" | grep -q "holds resources state-backend does not declare, so it is another stack's state or the code dropped a resource: aws_iam_openid_connect_provider.github, aws_iam_policy.deploy_surge, aws_iam_policy.provisioning, aws_iam_role.provisioner, aws_iam_role_policy_attachment.deploy_surge, aws_iam_role_policy_attachment.provisioning; nothing done." ||
  fail "$name" "does not name the six identity addresses"
printf '%s\n' "$out" | grep -qE '^(backup|init|push):' && fail "$name" "it wrote before refusing"
printf '%s\n' "$out" | grep -q 'stub terraform reached' && fail "$name" "it ran terraform"
[ ! -e "$XDG_STATE_HOME/danteplanner/tf-state-backup/state-backend.three.tfstate" ] || fail "$name" "wrote a backup"
pass "$name"

echo "bootstrap-state-to-s3 tests: $passed passed"

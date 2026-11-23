#!/usr/bin/env bash
# Move one bootstrap stack's local state for one account into that account's state bucket.
#
#   bootstrap-state-to-s3.sh <stack> <backend file> <tfvars file> <local state file>
#   bootstrap-state-to-s3.sh --remove-local <stack> <local state file>
#
# <stack> is state-backend or iam-bootstrap. Before anything is written, the first form requires
# the three files to name one account and every resource in the state to be declared, in the same
# single or count/for_each form, by the tracked .tf files of terraform/<stack>. It then backs the
# local file up outside the repository, initializes terraform/<stack> against the backend file,
# pushes the backup as the remote state, and pulls it back: the move is verified when the bucket's
# resources and outputs equal the backup's. The bucket's lineage and serial cannot identify the
# state: terraform's remote state manager stores a push into an empty S3 key under a new lineage
# at serial 1. A plan with the tfvars file then runs as information; its changes are drift the
# move did not cause, but a plan that fails stops the move. Verification writes <backup>.verified
# (lineage, serial and a sha256 of the resources and outputs; no account id). The second form
# deletes the local file only when that marker matches both its backup and the local file.
#
# Output names the backend file, never the bucket it holds: a bucket name carries the account id,
# and every line this prints is meant to be safe to paste into a public place.
set -euo pipefail

repo_root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)
TF_RUN="$repo_root/scripts/ops/terraform-run.sh"
state_home="${XDG_STATE_HOME:-$HOME/.local/state}/danteplanner"
backup_dir="$state_home/tf-state-backup"
log_dir="$state_home/tf-plan-logs"

die() {
  echo "$*" >&2
  exit 1
}

usage() {
  {
    echo "usage: bootstrap-state-to-s3.sh <stack> <backend file> <tfvars file> <local state file>"
    echo "       bootstrap-state-to-s3.sh --remove-local <stack> <local state file>"
    echo "  <stack> is state-backend or iam-bootstrap; the backend file is terraform/backend.<account>.hcl"
  } >&2
  exit 2
}

tf() {
  TF_WORKSPACE=default "$TF_RUN" "$@"
}

abs_path() {
  printf '%s/%s\n' "$(cd "$(dirname "$1")" && pwd)" "$(basename "$1")"
}

check_stack() {
  case "$1" in
    state-backend | iam-bootstrap) ;;
    *)
      echo "unknown stack: $1" >&2
      usage
      ;;
  esac
}

state_facts() {
  python3 -c '
import json, sys
raw = (sys.stdin if sys.argv[1] == "-" else open(sys.argv[1])).read()
if not raw.strip():
    print(0, "-", 0)
else:
    d = json.loads(raw)
    print(sum(len(r.get("instances", [])) for r in d.get("resources", [])), d.get("lineage") or "-", d.get("serial", 0))
' "$1"
}

state_difference() {
  python3 -c '
import json, sys
backup = json.load(open(sys.argv[1]))
raw = sys.stdin.read()
remote = json.loads(raw) if raw.strip() else {}
same = all(backup.get(field, empty) == remote.get(field, empty) for field, empty in (("resources", []), ("outputs", {})))
print("equal" if same else "content (resources or outputs)")
' "$1"
}

check_declared() {
  local stack=$1 stack_dir=$2 state_file=$3 verdict
  verdict=$(python3 -c '
import json, re, subprocess, sys
stack_dir, state_file = sys.argv[1:3]
listing = subprocess.run(["git", "-C", stack_dir, "ls-files", "--", "*.tf"], capture_output=True, text=True)
if listing.returncode != 0:
    print("no-tracked-files"); sys.exit()
block_start = re.compile(r"^(resource|data)\s+\"([^\"]+)\"\s+\"([^\"]+)\"\s*\{", re.M)
declared = {}
for tracked in listing.stdout.split():
    if "/" in tracked:
        continue
    text = open(f"{stack_dir}/{tracked}").read()
    for start in block_start.finditer(text):
        depth, repeated = 1, False
        for line in text[start.end():].splitlines():
            if depth == 1 and re.match(r"\s*(count|for_each)\s*=", line):
                repeated = True
            depth += line.count("{") - line.count("}")
            if depth <= 0:
                break
        mode = "managed" if start.group(1) == "resource" else "data"
        declared[(mode, start.group(2), start.group(3))] = repeated
undeclared, reshaped = [], []
for resource in json.load(open(state_file)).get("resources", []):
    key = (resource.get("mode"), resource.get("type"), resource.get("name"))
    address = ("data." if key[0] == "data" else "") + f"{key[1]}.{key[2]}"
    if resource.get("module"):
        undeclared.append(resource["module"] + "." + address)
    elif key not in declared:
        undeclared.append(address)
    elif any(("index_key" in instance) != declared[key] for instance in resource.get("instances", [])):
        reshaped.append(address)
if undeclared:
    print("undeclared " + ", ".join(sorted(set(undeclared))))
elif reshaped:
    print("reshaped " + ", ".join(sorted(set(reshaped))))
else:
    print("ok")
' "$stack_dir" "$state_file")
  case "$verdict" in
    ok) echo "stack: every resource in $(basename "$state_file") is declared by $stack" ;;
    no-tracked-files) die "stack: cannot list the tracked .tf files of $stack_dir; nothing done" ;;
    undeclared\ *) die "stack: $state_file holds resources $stack does not declare, so it is another stack's state or the code dropped a resource: ${verdict#undeclared }; nothing done." ;;
    reshaped\ *) die "stack: $state_file holds resources whose instances $stack declares in another form (count or for_each against a single instance), so it is another stack's state: ${verdict#reshaped }; nothing done." ;;
    *) die "stack: could not compare $state_file with the declarations of $stack; nothing done" ;;
  esac
}

state_digest() {
  python3 -c '
import hashlib, json, sys
state = json.load(open(sys.argv[1]))
canonical = json.dumps({"resources": state.get("resources", []), "outputs": state.get("outputs", {})},
                       sort_keys=True, separators=(",", ":"))
print(hashlib.sha256(canonical.encode()).hexdigest())
' "$1"
}

file_count() {
  local n
  read -r n _ _ <<<"$(state_facts "$1")"
  printf '%s\n' "$n"
}

file_lineage() {
  local lineage
  read -r _ lineage _ <<<"$(state_facts "$1")"
  printf '%s\n' "$lineage"
}

check_accounts() {
  local backend_file=$1 tfvars=$2 state_file=$3 verdict
  verdict=$(python3 -c '
import json, re, sys
backend, tfvars, state = (open(path).read() for path in sys.argv[1:4])
bucket = re.search(r"^\s*bucket\s*=\s*\"[^\"]*?(\d{12})\"", backend, re.M)
if not bucket:
    print("no-bucket-id"); sys.exit()
declared = re.search(r"^\s*aws_account_id\s*=\s*\"?(\d{12})\"?\s*(?:(?:#|//).*)?$", tfvars, re.M)
if not declared:
    print("no-tfvars-id"); sys.exit()
if declared.group(1) != bucket.group(1):
    print("tfvars-differs"); sys.exit()
applied = [instance.get("attributes", {}).get("account_id")
           for resource in json.loads(state).get("resources", [])
           if (resource.get("mode"), resource.get("type"), resource.get("name"), resource.get("module"))
           == ("data", "aws_caller_identity", "current", None)
           for instance in resource.get("instances", [])]
if len(applied) != 1 or not applied[0]:
    print("no-caller-identity"); sys.exit()
print("ok" if applied[0] == bucket.group(1) else "state-differs")
' "$backend_file" "$tfvars" "$state_file")
  case "$verdict" in
    ok) echo "accounts: $(basename "$backend_file"), $(basename "$tfvars") and $(basename "$state_file") name the same account" ;;
    no-bucket-id) die "accounts: the bucket in $backend_file does not end in an account id; nothing done" ;;
    no-tfvars-id) die "accounts: $tfvars sets no aws_account_id; nothing done" ;;
    tfvars-differs) die "accounts: $backend_file and $tfvars name different accounts; nothing done" ;;
    no-caller-identity) die "accounts: $state_file holds no data.aws_caller_identity.current account_id, so the account it was applied in is unknown; nothing done" ;;
    state-differs) die "accounts: $state_file was applied in another account than the one $backend_file names; nothing done" ;;
    *) die "accounts: could not compare $backend_file, $tfvars and $state_file; nothing done" ;;
  esac
}

is_default_file_of() {
  [[ "$1" == "$2/terraform.tfstate" ]]
}

push_state() {
  local stack=$1 stack_dir=$2 account=$3 label=$4 tfvars=$5 local_file=$6
  shift 6
  local key="$stack/terraform.tfstate in $label"
  local backup="$backup_dir/$stack.$account.tfstate"
  local init_log="$log_dir/$stack.$account.init.log"
  local plan_log="$log_dir/$stack.$account.plan"
  local plan_cmd="$TF_RUN -chdir=$stack_dir plan -var-file=$tfvars"
  local moved=0

  local_file=$(abs_path "$local_file")

  if [[ -d "$stack_dir/terraform.tfstate.d" ]] &&
    [[ -n "$(find "$stack_dir/terraform.tfstate.d" -type f -name terraform.tfstate 2>/dev/null)" ]]; then
    die "$stack_dir holds workspace states under terraform.tfstate.d, which init would offer to migrate into the one backend; nothing done. Run from a checkout whose terraform/$stack holds none (this branch's worktree), passing the local file by absolute path."
  fi
  if [[ -s "$stack_dir/terraform.tfstate" ]] && ! is_default_file_of "$local_file" "$stack_dir"; then
    die "$stack_dir/terraform.tfstate exists and is not the file being moved; init would offer to migrate it. Move that one first; nothing done."
  fi
  if [[ -s "$local_file" ]]; then
    check_declared "$stack" "$stack_dir" "$local_file"
  elif [[ -s "$backup" ]]; then
    check_declared "$stack" "$stack_dir" "$backup"
  fi

  mkdir -p "$backup_dir" "$log_dir"
  chmod 700 "$backup_dir"

  if [[ ! -e "$local_file" ]]; then
    if is_default_file_of "$local_file" "$stack_dir" && [[ -s "$backup" ]]; then
      echo "backup: $local_file was moved aside by an earlier run; resuming from $backup"
    else
      die "local state file $local_file is missing; nothing done"
    fi
  elif [[ ! -s "$local_file" ]]; then
    die "local state file $local_file is empty; nothing done"
  else
    if [[ -s "$backup" ]] && [[ "$(file_lineage "$backup")" != "$(file_lineage "$local_file")" ]]; then
      die "$backup already holds a different state (another lineage); nothing done. Move it away if it is stale, then re-run."
    fi
    if [[ -s "$backup" ]]; then
      local local_serial backup_serial_now
      read -r _ _ local_serial <<<"$(state_facts "$local_file")"
      read -r _ _ backup_serial_now <<<"$(state_facts "$backup")"
      [[ "$local_serial" -ge "$backup_serial_now" ]] ||
        die "$local_file is behind its own backup $backup (serial $local_serial, backup $backup_serial_now); nothing done."
      if [[ "$local_serial" -eq "$backup_serial_now" && "$(state_difference "$backup" <"$local_file")" != equal ]]; then
        die "$local_file and its backup $backup are two different states at one serial ($local_serial): same lineage, other resources or outputs; nothing done."
      fi
    fi
    if is_default_file_of "$local_file" "$stack_dir"; then
      mv "$local_file" "$backup"
      moved=1
    else
      (umask 077 && command cp -f "$local_file" "$backup.tmp")
      mv "$backup.tmp" "$backup"
    fi
    chmod 600 "$backup"
    if [[ $moved -eq 1 ]]; then
      echo "backup: moved $local_file aside to $backup (mode 600)"
    else
      echo "backup: copied $local_file to $backup (mode 600)"
    fi
  fi

  local file_n
  file_n=$(file_count "$backup")
  [[ "$file_n" -gt 0 ]] || die "$backup holds no resources; nothing pushed"

  if ! tf -chdir="$stack_dir" init -reconfigure -input=false -no-color "$@" >"$init_log" 2>&1; then
    die "init: failed against $label; read $init_log. The backup at $backup is intact; re-run this command once init succeeds."
  fi
  echo "init: $stack against $label (log $init_log)"

  local pulled remote_n remote_lineage remote_serial backup_lineage backup_serial
  if ! pulled=$(tf -chdir="$stack_dir" state pull 2>>"$init_log"); then
    die "remote: could not read $key; read $init_log. Nothing pushed; re-run this command."
  fi
  read -r remote_n remote_lineage remote_serial <<<"$(state_facts - <<<"$pulled")"
  read -r _ backup_lineage backup_serial <<<"$(state_facts "$backup")"
  local push=0
  if [[ "$remote_n" -eq 0 ]]; then
    echo "remote: $key is empty"
    push=1
  elif [[ "$(state_difference "$backup" <<<"$pulled")" == equal ]]; then
    echo "push: already done; $key holds the backup's resources and outputs (the bucket's lineage and serial are not compared)"
  elif [[ "$remote_lineage" == "$backup_lineage" && "$remote_serial" -lt "$backup_serial" ]]; then
    echo "remote: $key holds an older serial of the same state; pushing the newer local state over it"
    push=1
  elif [[ "$remote_lineage" == "$backup_lineage" && "$remote_serial" -gt "$backup_serial" ]]; then
    die "remote: $key holds a newer serial of the same state than $local_file; nothing pushed. The local file is behind the bucket: do not move it."
  elif [[ "$remote_lineage" == "$backup_lineage" ]]; then
    die "remote: $key holds the same lineage and serial as $local_file but different resources or outputs; nothing pushed."
  else
    die "remote: $key holds $remote_n resources with other resources or outputs than the backup, under another lineage; nothing pushed."
  fi
  if [[ $push -eq 1 ]]; then
    tf -chdir="$stack_dir" state push "$backup" >>"$init_log" 2>&1 ||
      die "push: failed; the remote may be unchanged. Read $init_log, then re-run this command."
    echo "push: $file_n resources from $backup to $key"
  fi

  local listed
  if ! listed=$(tf -chdir="$stack_dir" state list 2>>"$init_log"); then
    die "count: could not list $key after the push; read $init_log. Pushed but not verified; next: $plan_cmd"
  fi
  remote_n=$(printf '%s\n' "$listed" | awk 'NF' | wc -l | tr -d ' ')
  if [[ "$remote_n" -ne "$file_n" ]]; then
    die "count: $key lists $remote_n resources but the file holds $file_n. Pushed but not verified."
  fi
  echo "count: remote $remote_n, file $file_n"

  local difference
  if ! pulled=$(tf -chdir="$stack_dir" state pull 2>>"$init_log"); then
    die "state: could not read $key after the push; read $init_log. Pushed but not verified; re-run this command."
  fi
  difference=$(state_difference "$backup" <<<"$pulled")
  [[ "$difference" == equal ]] ||
    die "state: $key differs from the backup in $difference. Pushed but not verified."
  local bucket_lineage bucket_serial
  read -r _ bucket_lineage bucket_serial <<<"$(state_facts - <<<"$pulled")"
  [[ "$bucket_lineage" == "$backup_lineage" ]] && bucket_lineage=same || bucket_lineage=other
  echo "state: $key holds the backup's resources and outputs (content only; bucket lineage $bucket_lineage, serial $bucket_serial)"

  local rc=0 counts replaced
  tf -chdir="$stack_dir" plan -var-file="$tfvars" -detailed-exitcode -input=false -no-color >"$plan_log" 2>&1 || rc=$?
  case "$rc" in
    0) echo "plan: no changes (log $plan_log)" ;;
    2)
      counts=$(awk '/^Plan: [0-9]+ to add, [0-9]+ to change, [0-9]+ to destroy/ {sub(/\.$/, "", $0); sub(/^Plan: /, "", $0); print; exit}' "$plan_log")
      echo "plan: ${counts:-changes}; this is a difference between the code and the account that the move did not cause. Read $plan_log."
      replaced=$(awk '/must be replaced$|will be destroyed$/ {n++} END {print n + 0}' "$plan_log")
      if [[ "$replaced" -gt 0 ]]; then
        echo "WARNING: the plan destroys or replaces $replaced resources. Read $plan_log before any apply; the move did not cause this."
      fi
      ;;
    *) die "plan: failed (exit $rc), so the stack cannot be planned from the bucket. Pushed but not verified. Read $plan_log, or run: $plan_cmd" ;;
  esac

  (umask 077 && printf '%s %s %s\n' "$backup_lineage" "$backup_serial" "$(state_digest "$backup")" >"$backup.verified")
  echo "verified: $stack $label $file_n resources, the bucket holds the backup's state"
  echo "next: $(abs_path "${BASH_SOURCE[0]}") --remove-local $stack $local_file"
}

remove_local() {
  local stack=$1 local_file
  local_file=$(abs_path "$2")
  local sibling="$local_file.backup"
  local probe

  if [[ -s "$local_file" ]]; then
    probe=$local_file
  elif [[ ! -e "$local_file" && -s "$sibling" ]]; then
    probe=$sibling
  else
    die "nothing to remove: $local_file is missing or empty and has no .backup sibling"
  fi

  local lineage match="" candidate
  lineage=$(file_lineage "$probe")
  for candidate in "$backup_dir/$stack".*.tfstate; do
    [[ -f "$candidate" ]] || continue
    if [[ "$(file_lineage "$candidate")" == "$lineage" ]]; then
      [[ -z "$match" ]] || die "more than one backup under $backup_dir matches $probe; nothing removed"
      match=$candidate
    fi
  done
  [[ -n "$match" ]] || die "no backup of $probe under $backup_dir; nothing removed. Run the push first: bootstrap-state-to-s3.sh $stack <backend file> <tfvars file> $local_file"

  local marker="$match.verified" account match_lineage match_serial
  account=${match##*/"$stack".}
  account=${account%.tfstate}
  read -r _ match_lineage match_serial <<<"$(state_facts "$match")"
  if [[ ! -f "$marker" ]] || [[ "$(cat "$marker")" != "$match_lineage $match_serial $(state_digest "$match")" ]]; then
    die "the move of $probe was never verified (no marker $marker matching its backup); nothing removed. Run the move first: bootstrap-state-to-s3.sh $stack <path>/backend.$account.hcl <tfvars file> $local_file"
  fi

  if [[ "$probe" == "$local_file" ]]; then
    local local_lineage local_serial
    read -r _ local_lineage local_serial <<<"$(state_facts "$local_file")"
    if [[ "$local_lineage $local_serial" != "$match_lineage $match_serial" ]]; then
      local relation="is newer than"
      [[ "$local_serial" -gt "$match_serial" ]] || relation="differs from"
      die "$local_file $relation the verified copy (serial $local_serial, verified $match_serial); nothing removed. Run the move again: bootstrap-state-to-s3.sh $stack <path>/backend.$account.hcl <tfvars file> $local_file"
    fi
    [[ "$(state_digest "$local_file")" == "$(state_digest "$match")" ]] ||
      die "$local_file has the verified lineage and serial but other resources or outputs than the verified copy; nothing removed. Run the move again: bootstrap-state-to-s3.sh $stack <path>/backend.$account.hcl <tfvars file> $local_file"
    command rm -f "$local_file"
    echo "removed: $local_file"
  else
    echo "already moved: $local_file (backup $match)"
  fi
  if [[ -e "$sibling" ]]; then
    command rm -f "$sibling"
    echo "removed: $sibling"
  fi
  command rm -f "$marker"
  echo "removed: $marker"

  local dir parent
  dir=$(dirname "$local_file")
  parent=$(dirname "$dir")
  if [[ "$(basename "$parent")" == terraform.tfstate.d ]]; then
    if rmdir "$dir" 2>/dev/null; then
      echo "removed: $dir"
      rmdir "$parent" 2>/dev/null && echo "removed: $parent"
    fi
  fi
  return 0
}

main() {
  if [[ "${1:-}" == --remove-local ]]; then
    [[ $# -eq 3 ]] || usage
    check_stack "$2"
    remove_local "$2" "$3"
    return
  fi

  [[ $# -eq 4 ]] || usage
  local stack=$1 backend_file=$2 tfvars=$3 local_file=$4
  check_stack "$stack"

  [[ -f "$backend_file" ]] || die "backend file $backend_file is missing; copy terraform/backend.account.hcl.example"
  local name account
  name=$(basename "$backend_file")
  [[ "$name" =~ ^backend\.(.+)\.hcl$ ]] || die "backend file must be named backend.<account>.hcl, got $name"
  account=${BASH_REMATCH[1]}
  [[ -f "$tfvars" ]] || die "tfvars file $tfvars is missing"
  backend_file=$(abs_path "$backend_file")
  tfvars=$(abs_path "$tfvars")

  local stack_dir="$repo_root/terraform/$stack" state_file
  state_file=$(abs_path "$local_file")
  if [[ ! -e "$state_file" ]] && [[ "$state_file" == "$stack_dir/terraform.tfstate" ]]; then
    state_file="$backup_dir/$stack.$account.tfstate"
  fi
  [[ -s "$state_file" ]] || die "local state file $local_file is missing or empty; nothing done"
  check_accounts "$backend_file" "$tfvars" "$state_file"

  push_state "$stack" "$stack_dir" "$account" "$name" "$tfvars" "$local_file" -backend-config="$backend_file"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi

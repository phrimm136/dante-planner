#!/usr/bin/env bash
# Usage: AWS_PROFILE=prod scripts/ops/provision/k3s-snapshot-schedule-repair.sh --site oregon|seoul [--dry-run]
#        CP_INSTANCE_ID=i-0abc ... skips the terraform output lookup.
# Steps, endings and rollback: docs/runbooks/etcd-snapshot-repair.md.
set -euo pipefail

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/lib/constants.sh"
# shellcheck source=../lib/etcd-interval.sh
source "$OPS_DIR/lib/etcd-interval.sh"

K3S_UNIT=/etc/systemd/system/k3s.service
LOCK_DIR=/run/k3s-snapshot-schedule-repair.lock
POLL_SECONDS=5
SAVE_BOUND_SECONDS=120
RESTART_BOUND_SECONDS=300
ROLLBACK_BOUND_SECONDS=300
REMOTE_SLACK_SECONDS=120
REMOTE_WORST_SECONDS=$((2 * SAVE_BOUND_SECONDS + RESTART_BOUND_SECONDS + ROLLBACK_BOUND_SECONDS + REMOTE_SLACK_SECONDS))
SSM_TIMEOUT_SECONDS=$((REMOTE_WORST_SECONDS + 60))
LOCAL_WAIT_SECONDS=$((SSM_TIMEOUT_SECONDS + 60))

usage() {
    echo "usage: k3s-snapshot-schedule-repair.sh --site oregon|seoul [--dry-run]" >&2
    exit 2
}

site_region() {
    case "$1" in
        oregon) echo us-west-2 ;;
        seoul) echo ap-northeast-2 ;;
        *) return 1 ;;
    esac
}

remote_script() {
    local mode="$1" unit="$2" cron="$3" save_bound="$4" restart_bound="$5" rollback_bound="$6" poll="$7" lock_dir="$8"
    printf 'MODE=%s\nUNIT=%s\nCRON=%s\nSAVE_BOUND=%s\nRESTART_BOUND=%s\nROLLBACK_BOUND=%s\nPOLL=%s\nLOCK_DIR=%s\n' \
        "$mode" "'$unit'" "'$cron'" "$save_bound" "$restart_bound" "$rollback_bound" "$poll" "'$lock_dir'"
    cat <<'REMOTE'
malformed="'0 \\*/6 \\* \\* \\*' \\"
repaired="'$CRON' \\"
flag="'--etcd-snapshot-schedule-cron' \\"
STATE=checked
OWN_LOCK=0
backup=""
restart_reason=""
KUBECTL_TIMEOUT=--request-timeout=5s

release_lock() { if [ "$OWN_LOCK" = 1 ]; then rm -rf "$LOCK_DIR"; OWN_LOCK=0; fi; }
end() { STATE=finished; release_lock; echo "$2"; exit "$1"; }
snapshot_count() { k3s kubectl get etcdsnapshotfiles.k3s.cattle.io -o name 2>/dev/null | grep -c . || true; }
s3_count() { k3s kubectl get etcdsnapshotfiles.k3s.cattle.io -l etcd.k3s.cattle.io/snapshot-storage-node=s3 -o name 2>/dev/null | grep -c . || true; }
snapshot_split() { total=$(snapshot_count); in_s3=$(s3_count); echo "snapshots: local $((total - in_s3)), s3 $in_s3"; }
cron_line() { sed -n "${value_no}p" "$UNIT" | sed 's/^[[:space:]]*//'; }
unit_kill_mode() { awk '{ line = $0; sub(/^[[:space:]]+/, "", line) } line ~ /^KillMode=/ { print line }' "$UNIT"; }
effective_kill_mode() { systemctl show -p KillMode k3s 2>/dev/null || true; }
api_ready() { [ "$(k3s kubectl get --raw /readyz "$KUBECTL_TIMEOUT" 2>/dev/null)" = ok ]; }
serving_text() {
  if systemctl is-active --quiet k3s && api_ready; then echo "k3s is serving"; else echo "k3s is NOT serving"; fi
}
restart_count() { systemctl show -p NRestarts --value k3s 2>/dev/null || echo 0; }

nodes_back() {
  node_rows=$(k3s kubectl get nodes --no-headers "$KUBECTL_TIMEOUT" 2>/dev/null) || return 1
  lease_rows=$(k3s kubectl -n kube-node-lease get leases "$KUBECTL_TIMEOUT" \
    -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.spec.renewTime}{"\n"}{end}' 2>/dev/null) || return 1
  behind=0
  while IFS= read -r node_name; do
    [ -n "$node_name" ] || continue
    node_status=$(printf '%s\n' "$node_rows" | awk -v n="$node_name" '$1 == n { print $2 }')
    renew_time=$(printf '%s\n' "$lease_rows" | awk -v n="$node_name" '$1 == n { print $2 }')
    case "$node_status" in
      Ready*) ;;
      *) behind=$((behind + 1)); continue ;;
    esac
    renew_epoch=$(date -d "$renew_time" +%s 2>/dev/null) || { behind=$((behind + 1)); continue; }
    [ "$renew_epoch" -gt "$1" ] || behind=$((behind + 1))
  done <<READY
$ready_before
READY
  [ "$behind" = 0 ]
}

wait_ready() {
  deadline=$(( $(date +%s) + $1 ))
  restart_reason="not ready within $1s"
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if systemctl is-failed --quiet k3s; then
      restart_reason="systemd reports k3s failed"
      return 1
    fi
    if [ "$(restart_count)" -gt "$3" ]; then
      restart_reason="k3s exited and systemd restarted it"
      return 1
    fi
    if ! systemctl is-active --quiet k3s; then
      restart_reason="k3s not active within $1s"
    elif ! api_ready; then
      restart_reason="API /readyz not ok within $1s"
    elif ! nodes_back "$2"; then
      restart_reason="a node that was Ready before the run did not report Ready with a new lease within $1s"
    else
      return 0
    fi
    sleep "$POLL"
  done
  return 1
}

start_k3s() {
  since_epoch=$(date +%s)
  restarts_before=$(restart_count)
  systemctl restart --no-block k3s
}

roll_back() {
  STATE=rolling-back
  why="$1"
  look="run journalctl -u k3s -n 200 --no-pager; systemctl status k3s --no-pager; k3s kubectl get --raw /readyz"
  if ! cp -p "$backup" "$UNIT"; then
    end 5 "ROLLED BACK, k3s NOT serving: $why; copying $backup back to $UNIT failed; run cp -p $backup $UNIT; systemctl daemon-reload; systemctl restart --no-block k3s; then $look"
  fi
  systemctl daemon-reload || end 5 "ROLLED BACK, k3s NOT serving: $why; systemctl daemon-reload failed after the restore; $look"
  start_k3s || end 5 "ROLLED BACK, k3s NOT serving: $why; the rollback restart was refused; $look"
  if wait_ready "$ROLLBACK_BOUND" "$since_epoch" "$restarts_before"; then
    end 4 "ROLLED BACK, k3s serving: $why"
  fi
  end 5 "ROLLED BACK, k3s NOT serving: $why; after the rollback $restart_reason; $look"
}

# shellcheck disable=SC2329
on_exit() {
  if [ "$STATE" = unit-edited ]; then
    roll_back "the run stopped before it finished"
  fi
  release_lock
}
trap on_exit EXIT
trap 'exit 1' INT TERM HUP

if [ -d "$LOCK_DIR" ]; then
  lock_state="held since $(date -u -r "$LOCK_DIR" +%Y-%m-%dT%H:%M:%SZ)"
else
  lock_state=free
fi
echo "lock $LOCK_DIR: $lock_state"
if [ "$MODE" = repair ]; then
  mkdir "$LOCK_DIR" 2>/dev/null || end 3 "NOTHING CHANGED: another repair run holds $LOCK_DIR ($lock_state); wait for it to end, or remove the directory when no repair command is InProgress on this instance"
  OWN_LOCK=1
fi

[ -f "$UNIT" ] || end 3 "NOTHING CHANGED: $UNIT does not exist; $(serving_text)"
flag_nos=$(FLAG="$flag" awk '{ line = $0; sub(/^[[:space:]]+/, "", line) } line == ENVIRON["FLAG"] { print NR }' "$UNIT")
flag_count=$(printf '%s\n' "$flag_nos" | grep -c . || true)
[ "$flag_count" = 1 ] || end 3 "NOTHING CHANGED: $UNIT holds the --etcd-snapshot-schedule-cron argument $flag_count times, not once; $(serving_text)"
value_no=$((flag_nos + 1))
found=$(cron_line)
echo "unit line $value_no as found: $found"
if [ "$found" = "$repaired" ]; then
  snapshot_split
  if ! systemctl is-active --quiet k3s; then
    end 3 "NOTHING CHANGED: the unit holds the repaired line, but k3s is $(systemctl is-active k3s || true)"
  fi
  api_ready || end 3 "NOTHING CHANGED: the unit holds the repaired line and k3s is active, but the API /readyz is not ok"
  end 0 "ALREADY REPAIRED"
fi
[ "$found" = "$malformed" ] || end 3 "NOTHING CHANGED: line $value_no is not the known malformed line $malformed; $(serving_text)"

echo "unit KillMode line: $(unit_kill_mode)"
echo "effective $(effective_kill_mode)"
node_rows=$(k3s kubectl get nodes --no-headers "$KUBECTL_TIMEOUT" 2>/dev/null) || end 3 "NOTHING CHANGED: listing the nodes failed; $(serving_text)"
node_total=$(printf '%s\n' "$node_rows" | grep -c . || true)
ready_before=$(printf '%s\n' "$node_rows" | awk '$2 ~ /^Ready/ { print $1 }')
ready_total=$(printf '%s\n' "$ready_before" | grep -c . || true)
echo "nodes Ready before the run: $ready_total of $node_total"
if [ "$ready_total" -lt "$node_total" ]; then
  echo "nodes not Ready before the run: $((node_total - ready_total)); the run does not check them afterwards"
fi

if [ "$MODE" = dry-run ]; then
  if nodes_back "$(( $(date +%s) - 60 ))"; then lease_state="renewed within 60s"; else lease_state="NOT all renewed within 60s"; fi
  echo "node leases of the Ready nodes: $lease_state"
  echo "k3s: $(systemctl is-active k3s || true)"
  snapshot_split
  echo "would: k3s etcd-snapshot save and wait for its S3 copy; copy $UNIT to $UNIT.bak-<timestamp>; replace line $value_no with $repaired; systemctl daemon-reload; systemctl restart --no-block k3s; wait up to ${RESTART_BOUND}s for k3s, /readyz and every node Ready before the run with a new lease"
  end 0 "NOTHING CHANGED: dry run"
fi

[ "$ready_total" -gt 0 ] || end 3 "NOTHING CHANGED: no node is Ready before the run; $(serving_text)"
[ "$(effective_kill_mode)" = KillMode=process ] || end 3 "NOTHING CHANGED: k3s runs with $(effective_kill_mode), not KillMode=process, so a restart stops the running pods"

echo "STEP 1: k3s etcd-snapshot save"
systemctl is-active --quiet k3s || end 3 "NOTHING CHANGED: k3s is not active, and a snapshot needs the running server; start k3s and rerun"
total_before=$(snapshot_count)
s3_before=$(s3_count)
timeout "$SAVE_BOUND" k3s etcd-snapshot save || end 3 "NOTHING CHANGED: k3s etcd-snapshot save failed or ran over ${SAVE_BOUND}s; $(serving_text)"
deadline=$(( $(date +%s) + SAVE_BOUND ))
while [ "$(s3_count)" -le "$s3_before" ] && [ "$(date +%s)" -lt "$deadline" ]; do
  sleep "$POLL"
done
if [ "$(s3_count)" -le "$s3_before" ]; then
  local_new=$(( $(snapshot_count) - $(s3_count) - (total_before - s3_before) ))
  end 3 "NOTHING CHANGED: no S3 copy of the new snapshot is listed ${SAVE_BOUND}s after the save ($local_new new local); the local snapshot was written but the S3 copy is missing; read the k3s journal for the upload error: journalctl -u k3s --since '-15 min'"
fi
STATE=snapshot-proven
snapshot_split

echo "STEP 2: back up the unit"
backup="$UNIT.bak-$(date -u +%Y%m%dT%H%M%SZ)"
cp -p "$UNIT" "$backup" || end 3 "NOTHING CHANGED: copying $UNIT to $backup failed"
echo "backup: $backup"
STATE=unit-edited

echo "STEP 3: replace line $value_no"
lead=$(sed -n "${value_no}p" "$UNIT" | sed 's/[^[:space:]].*$//')
REPLACEMENT="$lead$repaired" awk -v n="$value_no" 'NR == n { print ENVIRON["REPLACEMENT"]; next } { print }' \
  "$backup" > "$UNIT.repair" || roll_back "writing $UNIT.repair failed"
cat "$UNIT.repair" > "$UNIT" || roll_back "writing $UNIT failed"
rm -f "$UNIT.repair"
[ "$(cron_line)" = "$repaired" ] || roll_back "line $value_no did not read back as $repaired"

echo "STEP 4: systemctl daemon-reload, restart k3s without waiting on systemd"
systemctl daemon-reload || roll_back "systemctl daemon-reload failed"
start_k3s || roll_back "systemctl restart --no-block k3s was refused"

echo "STEP 5: wait up to ${RESTART_BOUND}s for k3s, /readyz and the nodes"
wait_ready "$RESTART_BOUND" "$since_epoch" "$restarts_before" || roll_back "$restart_reason"
STATE=restarted-ready
echo "API server ready, every node Ready before the run is Ready with a new lease"

echo "STEP 6: result"
snapshot_split
echo "unit cron line: $(cron_line)"
end 0 "REPAIRED"
REMOTE
}

ending_code() {
    case "$1" in
        "REPAIRED" | "ALREADY REPAIRED" | "NOTHING CHANGED: dry run") echo 0 ;;
        "NOTHING CHANGED: "*) echo 3 ;;
        "ROLLED BACK, k3s serving: "*) echo 4 ;;
        "ROLLED BACK, k3s NOT serving: "*) echo 5 ;;
        *) echo 7 ;;
    esac
}

run_on_cp() {
    local region="$1" cp_id="$2" script_text="$3" params cmd_id status waited=0 remote_out final_line
    params=$(jq -n --arg s "$script_text" --arg t "$SSM_TIMEOUT_SECONDS" \
        '{commands: ($s | split("\n")), executionTimeout: [$t]}')
    cmd_id=$(aws ssm send-command --region "$region" --instance-ids "$cp_id" \
        --document-name AWS-RunShellScript --parameters "$params" \
        --query 'Command.CommandId' --output text)
    echo "== command $cmd_id"
    while :; do
        status=$(aws ssm get-command-invocation --region "$region" --command-id "$cmd_id" \
            --instance-id "$cp_id" --query 'Status' --output text 2>/dev/null || echo Pending)
        case "$status" in
            Pending | InProgress | Delayed) ;;
            *) break ;;
        esac
        if [[ "$waited" -ge "$LOCAL_WAIT_SECONDS" ]]; then
            echo "the remote run did not end within ${LOCAL_WAIT_SECONDS}s (status $status) and may still be going; do not rerun until command $cmd_id has ended: aws ssm get-command-invocation --region $region --command-id $cmd_id --instance-id <cp id>" >&2
            return 6
        fi
        sleep "$POLL_SECONDS"
        waited=$((waited + POLL_SECONDS))
    done
    remote_out=$(aws ssm get-command-invocation --region "$region" --command-id "$cmd_id" --instance-id "$cp_id" \
        --output json | jq -r '.StandardOutputContent, .StandardErrorContent')
    printf '%s\n' "$remote_out"
    final_line=$(printf '%s\n' "$remote_out" | awk 'NF { last = $0 } END { print last }')
    if [[ "$(ending_code "$final_line")" == 7 ]]; then
        echo "the remote run ended with status $status and without one of the five final lines; read the unit state with --dry-run before anything else" >&2
    fi
    return "$(ending_code "$final_line")"
}

main() {
    local site="" mode=repair region cp_id cron run_code=0
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --site)
                [[ $# -ge 2 ]] || usage
                site="$2"
                shift
                ;;
            --dry-run) mode=dry-run ;;
            *) usage ;;
        esac
        shift
    done
    region=$(site_region "$site") || usage
    cron="0 */$(etcd_snapshot_interval_hours) * * *"
    require_aws_session
    if [[ -n "${CP_INSTANCE_ID:-}" ]]; then
        cp_id="$CP_INSTANCE_ID"
    else
        cp_id=$("$OPS_DIR/terraform-run.sh" -chdir="$OPS_DIR/../../terraform/$site" output -raw cp_instance_id)
    fi
    echo "== $mode of the $site control plane ($region)"
    run_on_cp "$region" "$cp_id" "$(remote_script "$mode" "$K3S_UNIT" "$cron" "$SAVE_BOUND_SECONDS" \
        "$RESTART_BOUND_SECONDS" "$ROLLBACK_BOUND_SECONDS" "$POLL_SECONDS" "$LOCK_DIR")" || run_code=$?
    if [[ "$run_code" -eq 0 && "$mode" == repair ]]; then
        echo "== confirm with .claude/skills/ops/bin/ops verify fleet --site $site"
    fi
    exit "$run_code"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi

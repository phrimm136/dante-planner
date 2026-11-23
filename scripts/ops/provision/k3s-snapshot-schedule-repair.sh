#!/usr/bin/env bash
# Usage: AWS_PROFILE=prod scripts/ops/provision/k3s-snapshot-schedule-repair.sh --site oregon|seoul [--dry-run]
#        CP_INSTANCE_ID=i-0abc ... skips the terraform output lookup.
# Steps and rollback: docs/runbooks/etcd-snapshot-repair.md.
set -euo pipefail

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/lib/constants.sh"
# shellcheck source=../lib/etcd-interval.sh
source "$OPS_DIR/lib/etcd-interval.sh"

K3S_UNIT=/etc/systemd/system/k3s.service
READY_BOUND_SECONDS=300
POLL_SECONDS=5
SSM_BOUND_SECONDS=900

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
    local mode="$1" unit="$2" cron="$3" bound="$4" poll="$5"
    printf 'MODE=%s\nUNIT=%s\nCRON=%s\nREADY_BOUND=%s\nPOLL=%s\n' \
        "$mode" "'$unit'" "'$cron'" "$bound" "$poll"
    cat <<'REMOTE'
malformed="'0 \\*/6 \\* \\* \\*' \\"
repaired="'$CRON' \\"
flag="'--etcd-snapshot-schedule-cron' \\"
fail() { echo "FAILED step $1: $2"; exit 1; }
snapshot_count() { k3s kubectl get etcdsnapshotfiles.k3s.cattle.io -o name 2>/dev/null | grep -c . || true; }
s3_count() { k3s kubectl get etcdsnapshotfiles.k3s.cattle.io -l etcd.k3s.cattle.io/snapshot-storage-node=s3 -o name 2>/dev/null | grep -c . || true; }
snapshot_split() { total=$(snapshot_count); in_s3=$(s3_count); echo "snapshots: local $((total - in_s3)), s3 $in_s3"; }
unit_kill_mode() { awk '{ line = $0; sub(/^[[:space:]]+/, "", line) } line ~ /^KillMode=/ { print line }' "$UNIT"; }
effective_kill_mode() { systemctl show -p KillMode k3s 2>/dev/null || true; }
cron_line() { sed -n "${value_no}p" "$UNIT" | sed 's/^[[:space:]]*//'; }

[ -f "$UNIT" ] || fail check "$UNIT does not exist"
flag_nos=$(FLAG="$flag" awk '{ line = $0; sub(/^[[:space:]]+/, "", line) } line == ENVIRON["FLAG"] { print NR }' "$UNIT")
flag_count=$(printf '%s\n' "$flag_nos" | grep -c . || true)
[ "$flag_count" = 1 ] || fail check "$UNIT holds the --etcd-snapshot-schedule-cron argument $flag_count times, not once; nothing changed"
flag_no=$flag_nos
value_no=$((flag_no + 1))
found=$(cron_line)
echo "unit line $value_no as found: $found"
if [ "$found" = "$repaired" ]; then
  echo "ALREADY REPAIRED: the unit holds $repaired; nothing changed"
  snapshot_split
  exit 0
fi
[ "$found" = "$malformed" ] || fail check "line $value_no is not the known malformed line $malformed; nothing changed"

echo "unit KillMode line: $(unit_kill_mode)"
echo "effective $(effective_kill_mode)"
if [ "$MODE" = dry-run ]; then
  echo "k3s: $(systemctl is-active k3s || true)"
  snapshot_split
  echo "would: k3s etcd-snapshot save; copy $UNIT to $UNIT.bak-<timestamp>; replace line $value_no with $repaired; systemctl daemon-reload; systemctl restart k3s; wait up to ${READY_BOUND}s for /readyz and every node Ready"
  exit 0
fi

[ "$(effective_kill_mode)" = KillMode=process ] || fail check "k3s runs with $(effective_kill_mode), not KillMode=process, so a restart stops the running pods; nothing changed"

echo "STEP 1: k3s etcd-snapshot save"
systemctl is-active --quiet k3s || fail 1 "k3s is not active, and a snapshot needs the running server; start k3s and rerun"
total_before=$(snapshot_count)
s3_before=$(s3_count)
k3s etcd-snapshot save || fail 1 "k3s etcd-snapshot save failed"
waited=0
while [ "$waited" -le "$READY_BOUND" ]; do
  [ "$(s3_count)" -gt "$s3_before" ] && break
  sleep "$POLL"
  waited=$((waited + POLL))
done
local_new=$(( $(snapshot_count) - $(s3_count) - (total_before - s3_before) ))
[ "$(s3_count)" -gt "$s3_before" ] || fail 1 "no S3 copy of the new snapshot is listed ${READY_BOUND}s after the save ($local_new new local); the local snapshot was written but the S3 copy is missing; read the k3s journal for the upload error: journalctl -u k3s --since '-15 min'"
snapshot_split

echo "STEP 2: back up the unit"
backup="$UNIT.bak-$(date -u +%Y%m%dT%H%M%SZ)"
cp -p "$UNIT" "$backup" || fail 2 "copying $UNIT to $backup failed"
echo "backup: $backup"

echo "STEP 3: replace line $value_no"
lead=$(sed -n "${value_no}p" "$UNIT" | sed 's/[^[:space:]].*$//')
REPLACEMENT="$lead$repaired" awk -v n="$value_no" 'NR == n { print ENVIRON["REPLACEMENT"]; next } { print }' \
  "$backup" > "$UNIT.repair" || fail 3 "writing $UNIT.repair failed"
cat "$UNIT.repair" > "$UNIT" || fail 3 "writing $UNIT failed; the original is $backup"
rm -f "$UNIT.repair"
[ "$(cron_line)" = "$repaired" ] || { cp -p "$backup" "$UNIT"; fail 3 "line $value_no did not read back as $repaired; restored $backup"; }

echo "STEP 4: systemctl daemon-reload, restart k3s"
systemctl daemon-reload || fail 4 "systemctl daemon-reload failed; the unit holds the repaired line, the original is $backup"
systemctl restart k3s || fail 4 "systemctl restart k3s failed; restore with: cp -p $backup $UNIT; systemctl daemon-reload; systemctl restart k3s"

echo "STEP 5: wait up to ${READY_BOUND}s for /readyz and every node Ready"
ready=0
waited=0
while [ "$waited" -le "$READY_BOUND" ]; do
  if [ "$(k3s kubectl get --raw /readyz 2>/dev/null)" = ok ]; then
    nodes=$(k3s kubectl get nodes --no-headers 2>/dev/null || true)
    if [ -n "$nodes" ] && [ -z "$(printf '%s\n' "$nodes" | awk '$2 != "Ready"')" ]; then
      ready=1
      break
    fi
  fi
  sleep "$POLL"
  waited=$((waited + POLL))
done
if [ "$ready" != 1 ]; then
  cp -p "$backup" "$UNIT"
  systemctl daemon-reload
  systemctl restart k3s
  fail 5 "the API server or a node was not ready within ${READY_BOUND}s; restored $backup and restarted k3s"
fi
echo "API server ready, every node Ready"

echo "STEP 6: result"
snapshot_split
echo "unit cron line: $(cron_line)"
echo "REPAIRED"
REMOTE
}

run_on_cp() {
    local region="$1" cp_id="$2" script_text="$3" params cmd_id status waited=0
    params=$(jq -n --arg s "$script_text" '{commands: ($s | split("\n")), executionTimeout: ["1200"]}')
    cmd_id=$(aws ssm send-command --region "$region" --instance-ids "$cp_id" \
        --document-name AWS-RunShellScript --parameters "$params" \
        --query 'Command.CommandId' --output text)
    while :; do
        status=$(aws ssm get-command-invocation --region "$region" --command-id "$cmd_id" \
            --instance-id "$cp_id" --query 'Status' --output text 2>/dev/null || echo Pending)
        case "$status" in
            Pending | InProgress | Delayed) ;;
            *) break ;;
        esac
        if [[ "$waited" -ge "$SSM_BOUND_SECONDS" ]]; then
            echo "the SSM command did not finish within ${SSM_BOUND_SECONDS}s (status $status); read it with: aws ssm get-command-invocation --region $region --command-id $cmd_id --instance-id <cp id>" >&2
            return 1
        fi
        sleep "$POLL_SECONDS"
        waited=$((waited + POLL_SECONDS))
    done
    aws ssm get-command-invocation --region "$region" --command-id "$cmd_id" --instance-id "$cp_id" \
        --output json | jq -r '.StandardOutputContent, .StandardErrorContent'
    [[ "$status" == Success ]]
}

main() {
    local site="" mode=repair region cp_id cron
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
    if run_on_cp "$region" "$cp_id" "$(remote_script "$mode" "$K3S_UNIT" "$cron" "$READY_BOUND_SECONDS" "$POLL_SECONDS")"; then
        echo "== $mode finished; confirm with .claude/skills/ops/bin/ops verify fleet --site $site"
    else
        echo "== $mode FAILED; the lines above name the step" >&2
        exit 1
    fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi

#!/bin/bash
# k3s fleet operational verification for one site (oregon or seoul).
# Runs the cluster health checks ON the CP node via SSM send-command, so it needs
# no VPN or bastion: the CP already has `k3s kubectl` and a local kubeconfig at
# 127.0.0.1:6443. Read-only and idempotent. Exit 0 = healthy, non-zero = a check
# failed (see verdict()) so a gate or CI can trust the exit code.
#
# Usage:
#   scripts/ops/fleet-verify.sh [oregon|seoul]              # resolve CP id from terraform output
#   CP_INSTANCE_ID=i-0abc scripts/ops/fleet-verify.sh seoul
#   scripts/ops/fleet-verify.sh --kubeconfig [oregon|seoul] # also fetch kubeconfig -> ~/.kube/dante-<site>
#
# Operator creds needed: ssm:SendCommand + ssm:GetCommandInvocation on the CP;
# ssm:GetParameter + kms:Decrypt for --kubeconfig. jq required.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$SCRIPT_DIR/lib"
# shellcheck source=lib/common.sh
source "$LIB_DIR/common.sh"
# shellcheck source=lib/etcd-interval.sh
source "$LIB_DIR/etcd-interval.sh"

TF_ROOT="$SCRIPT_DIR/../../terraform"
CHECK_BOUND_SECONDS=180
POLL_SECONDS=5

site_region() {
    case "$1" in
        oregon) echo us-west-2 ;;
        seoul) echo ap-northeast-2 ;;
        *) return 1 ;;
    esac
}

resolve_cp_id() {
    if [[ -n "${CP_INSTANCE_ID:-}" ]]; then
        echo "$CP_INSTANCE_ID"
        return
    fi
    terraform -chdir="$TF_ROOT/$1" output -raw cp_instance_id
}

fetch_kubeconfig() {
    local site="$1" param dest
    param=$(terraform -chdir="$TF_ROOT/$site" output -raw kubeconfig_ssm_parameter)
    dest="$HOME/.kube/dante-$site"
    mkdir -p "$(dirname "$dest")"
    aws ssm get-parameter --with-decryption --region "$AWS_REGION" \
        --name "$param" --query 'Parameter.Value' --output text >"$dest"
    chmod 600 "$dest"
    log_info "kubeconfig written to $dest  (export KUBECONFIG=$dest)"
}

remote_checks() {
    cat <<'REMOTE'
echo "=== NODES ==="
k3s kubectl get nodes --no-headers
echo "=== PODS ==="
k3s kubectl -n danteplanner get pods --no-headers
echo "=== ARGOCD ==="
k3s kubectl -n argocd get applications.argoproj.io \
  -o custom-columns=NAME:.metadata.name,SYNC:.status.sync.status,HEALTH:.status.health.status --no-headers
echo "=== RDS ==="
k3s kubectl -n danteplanner exec ds/backend -- sh -c 'nc -zv -w5 "$MYSQL_HOST" 3306' 2>&1 || echo RDS_UNREACHABLE
echo "=== SNAPSHOTS ==="
if snapshot_rows=$(k3s kubectl get etcdsnapshotfiles.k3s.cattle.io \
  -o jsonpath='{range .items[*]}{"row "}{.status.creationTime}{"\n"}{end}' 2>&1); then
  echo "count=$(printf '%s\n' "$snapshot_rows" | grep -c '^row' || true)"
  if s3_names=$(k3s kubectl get etcdsnapshotfiles.k3s.cattle.io -l etcd.k3s.cattle.io/snapshot-storage-node=s3 -o name 2>/dev/null); then
    echo "s3_count=$(printf '%s\n' "$s3_names" | grep -c . || true)"
  else
    echo SNAPSHOT_READ_FAILED
  fi
  newest=$(printf '%s\n' "$snapshot_rows" | sed -n 's/^row //p' | grep . | sort | tail -n 1 || true)
  if [ -n "$newest" ]; then
    if newest_epoch=$(date -d "$newest" +%s 2>/dev/null); then
      echo "newest_age_seconds=$(( $(date +%s) - newest_epoch ))"
    else
      echo SNAPSHOT_TIME_UNPARSABLE
    fi
  fi
elif printf '%s\n' "$snapshot_rows" | grep -q "doesn't have a resource type"; then
  echo SNAPSHOT_CRD_MISSING
else
  echo SNAPSHOT_READ_FAILED
fi
echo "=== SNAPSHOT METRIC ==="
if metric_text=$(k3s kubectl get --raw /api/v1/namespaces/danteplanner/services/kube-state-metrics:8080/proxy/metrics 2>&1); then
  echo "series=$(printf '%s\n' "$metric_text" | grep -c '^kube_etcd_snapshot_creation_timestamp_seconds[{ ]' || true)"
elif printf '%s\n' "$metric_text" | grep -q 'no endpoints available'; then
  echo METRIC_SOURCE_DOWN
else
  echo METRIC_PROXY_FAILED
fi
REMOTE
}

# The remote script is JSON-encoded line-by-line with jq so quoting inside the commands
# (the pod's own $MYSQL_HOST, kubectl -o custom-columns) survives the send-command boundary.
run_checks_on_cp() {
    local cp_id="$1" params cmd_id status waited=0
    params=$(jq -n --arg s "$(remote_checks)" '{commands: ($s | split("\n"))}')
    cmd_id=$(aws ssm send-command --region "$AWS_REGION" \
        --instance-ids "$cp_id" \
        --document-name "AWS-RunShellScript" \
        --parameters "$params" \
        --query 'Command.CommandId' --output text)
    while :; do
        status=$(aws ssm get-command-invocation --region "$AWS_REGION" --command-id "$cmd_id" \
            --instance-id "$cp_id" --query 'Status' --output text 2>/dev/null || echo Pending)
        case "$status" in
            Pending | InProgress | Delayed) ;;
            *) break ;;
        esac
        if [[ "$waited" -ge "$CHECK_BOUND_SECONDS" ]]; then
            log_error "the control plane did not answer within ${CHECK_BOUND_SECONDS} seconds; status $status" >&2
            return 1
        fi
        sleep "$POLL_SECONDS"
        waited=$((waited + POLL_SECONDS))
    done
    if [[ "$status" != Success ]]; then
        log_error "the check command on the control plane ended with status $status" >&2
        return 1
    fi
    aws ssm get-command-invocation --region "$AWS_REGION" \
        --command-id "$cmd_id" --instance-id "$cp_id" \
        --query 'StandardOutputContent' --output text
}

NODES_NOT_READY='$2!="Ready"{print $1}'
PODS_NOT_READY='{split($2,r,"/"); if($3!="Running"||r[1]!=r[2]) print $1"("$3","$2")"}'
ARGOCD_NOT_HEALTHY='$2!="Synced"||$3!="Healthy"{print $1"("$2"/"$3")"}'

# Lines under the "=== NAME ===" header of $2, up to the next header.
section_of() {
    awk -v want="=== $1 ===" 'index($0, want){f=1;next} /^=== /{f=0} f' <<<"$2"
}

field_of() {
    sed -n "s/^$1=//p" <<<"$2" | head -n 1
}

snapshot_verdict() {
    local output="$1" interval="$2" fatal=0 section count s3_count age series stale_after
    stale_after=$((interval * 3 / 2))

    section=$(section_of SNAPSHOTS "$output")
    count=$(field_of count "$section")
    s3_count=$(field_of s3_count "$section")
    age=$(field_of newest_age_seconds "$section")
    if grep -qx SNAPSHOT_CRD_MISSING <<<"$section"; then
        log_error "SNAPSHOTS: the ETCDSnapshotFile CRD is missing"
        fatal=1
    elif grep -qx SNAPSHOT_READ_FAILED <<<"$section"; then
        log_error "SNAPSHOTS: listing etcdsnapshotfiles.k3s.cattle.io failed"
        fatal=1
    elif [[ ! "$count" =~ ^[0-9]+$ ]]; then
        log_error "SNAPSHOTS: no snapshot count reported"
        fatal=1
    elif [[ "$count" -eq 0 ]]; then
        log_error "SNAPSHOTS: count 0, no etcd snapshot exists"
        fatal=1
    elif [[ ! "$s3_count" =~ ^[0-9]+$ ]]; then
        log_error "SNAPSHOTS: count $count, no S3 count reported"
        fatal=1
    elif [[ "$s3_count" -eq 0 ]]; then
        log_error "SNAPSHOTS: count $count, 0 in S3; snapshots that never leave the node are not a backup"
        fatal=1
    elif grep -qx SNAPSHOT_TIME_UNPARSABLE <<<"$section"; then
        log_error "SNAPSHOTS: count $count, the newest creationTime does not parse as a date"
        fatal=1
    elif [[ ! "$age" =~ ^-?[0-9]+$ ]]; then
        log_error "SNAPSHOTS: count $count, no newest snapshot age reported"
        fatal=1
    elif [[ "$age" -gt "$stale_after" ]]; then
        log_error "SNAPSHOTS: count $count, $s3_count in S3, newest ${age}s old, over ${stale_after}s (1.5 x the ${interval}s schedule)"
        fatal=1
    else
        log_info "SNAPSHOTS: count $count, $s3_count in S3, newest ${age}s old (limit ${stale_after}s)"
    fi

    section=$(section_of "SNAPSHOT METRIC" "$output")
    series=$(field_of series "$section")
    if grep -qx METRIC_SOURCE_DOWN <<<"$section"; then
        log_error "SNAPSHOT METRIC: kube-state-metrics has no ready endpoint"
        fatal=1
    elif grep -qx METRIC_PROXY_FAILED <<<"$section"; then
        log_error "SNAPSHOT METRIC: the API server proxy call to kube-state-metrics failed"
        fatal=1
    elif [[ ! "$series" =~ ^[0-9]+$ ]]; then
        log_error "SNAPSHOT METRIC: no series count reported"
        fatal=1
    elif [[ "$count" =~ ^[0-9]+$ && "$count" -gt 0 && "$series" -eq 0 ]]; then
        log_error "SNAPSHOT METRIC: 0 kube_etcd_snapshot_creation_timestamp_seconds series for $count snapshots"
        fatal=1
    else
        log_info "SNAPSHOT METRIC: $series kube_etcd_snapshot_creation_timestamp_seconds series"
    fi
    return "$fatal"
}

# verdict OUTPUT INTERVAL_SECONDS - decide healthy vs failed from the raw check output.
# Fatal: any node not Ready; any pod not Running and ready; any app not Synced+Healthy;
# RDS unreachable; the snapshot checks of snapshot_verdict.
verdict() {
    local output="$1" interval="$2" fatal=0 section offenders

    section=$(section_of NODES "$output")
    offenders=$(awk "$NODES_NOT_READY" <<<"$section")
    if [[ -z "$section" ]]; then
        log_error "NODES: none reported"
        fatal=1
    elif [[ -n "$offenders" ]]; then
        log_error "NODES not Ready: $(paste -sd' ' <<<"$offenders")"
        fatal=1
    else
        log_info "NODES Ready: $(grep -c . <<<"$section")"
    fi

    section=$(section_of PODS "$output")
    offenders=$(awk "$PODS_NOT_READY" <<<"$section")
    if [[ -z "$section" ]]; then
        log_error "PODS: none reported"
        fatal=1
    elif [[ -n "$offenders" ]]; then
        log_error "PODS not ready: $(paste -sd' ' <<<"$offenders")"
        fatal=1
    else
        log_info "PODS Running+Ready: $(grep -c . <<<"$section")"
    fi

    section=$(section_of ARGOCD "$output")
    offenders=$(awk "$ARGOCD_NOT_HEALTHY" <<<"$section")
    if [[ -z "$section" ]]; then
        log_error "ARGOCD: no applications reported"
        fatal=1
    elif [[ -n "$offenders" ]]; then
        log_error "ARGOCD not Synced+Healthy: $(paste -sd' ' <<<"$offenders")"
        fatal=1
    else
        log_info "ARGOCD Synced+Healthy: $(grep -c . <<<"$section")"
    fi

    section=$(section_of RDS "$output")
    if [[ -z "$section" ]] || grep -q "RDS_UNREACHABLE" <<<"$section"; then
        log_error "RDS unreachable from backend pod"
        fatal=1
    else
        log_info "RDS reachable"
    fi

    snapshot_verdict "$output" "$interval" || fatal=1

    if [[ "$fatal" -ne 0 ]]; then
        log_error "fleet is NOT healthy"
        return 1
    fi
    log_info "fleet healthy"
}

main() {
    local site=oregon want_kubeconfig=0 cp_id output interval
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --kubeconfig) want_kubeconfig=1 ;;
            oregon | seoul) site="$1" ;;
            *)
                echo "usage: fleet-verify.sh [--kubeconfig] [oregon|seoul]" >&2
                exit 2
                ;;
        esac
        shift
    done
    AWS_REGION=$(site_region "$site")
    interval=$(etcd_snapshot_interval_seconds)
    if [[ "$want_kubeconfig" -eq 1 ]]; then
        fetch_kubeconfig "$site"
    fi
    cp_id=$(resolve_cp_id "$site")
    log_info "Verifying the $site fleet via its CP ($AWS_REGION)"
    output=$(run_checks_on_cp "$cp_id") || exit 1
    echo "$output"
    verdict "$output" "$interval"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi

#!/bin/bash
# Oregon k3s fleet operational verification (cutover runbook Part A, checks A2-A4).
# Runs the cluster health checks ON the CP node via SSM send-command, so it needs
# no VPN or bastion: the CP already has `k3s kubectl` and a local kubeconfig at
# 127.0.0.1:6443. Read-only and idempotent. Exit 0 = healthy, non-zero = a check
# failed (see verdict()) so a pre-cutover gate or CI can trust the exit code.
#
# Usage:
#   scripts/ops/oregon-verify.sh                  # resolve CP id from terraform output
#   CP_INSTANCE_ID=i-0abc scripts/ops/oregon-verify.sh
#   scripts/ops/oregon-verify.sh --kubeconfig     # also fetch kubeconfig -> ~/.kube/dante-oregon
#
# Operator creds needed: ssm:SendCommand + ssm:GetCommandInvocation on the CP;
# ssm:GetParameter + kms:Decrypt for --kubeconfig. jq required.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$SCRIPT_DIR/lib"
# shellcheck source=lib/common.sh
source "$LIB_DIR/common.sh"

AWS_REGION="${AWS_REGION:-us-west-2}"
TF_DIR="${TF_DIR:-$SCRIPT_DIR/../../terraform/oregon}"

resolve_cp_id() {
    if [ -n "${CP_INSTANCE_ID:-}" ]; then
        echo "$CP_INSTANCE_ID"
        return
    fi
    terraform -chdir="$TF_DIR" output -raw cp_instance_id
}

fetch_kubeconfig() {
    local param dest
    param=$(terraform -chdir="$TF_DIR" output -raw kubeconfig_ssm_parameter)
    dest="$HOME/.kube/dante-oregon"
    mkdir -p "$(dirname "$dest")"
    aws ssm get-parameter --with-decryption --region "$AWS_REGION" \
        --name "$param" --query 'Parameter.Value' --output text >"$dest"
    chmod 600 "$dest"
    log_info "kubeconfig written to $dest  (export KUBECONFIG=$dest)"
}

# Run the read-only check bundle on the CP and echo its stdout. The remote script
# is JSON-encoded line-by-line with jq so quoting inside the commands (the pod's
# own $MYSQL_HOST, kubectl -o custom-columns) survives the send-command boundary.
run_checks_on_cp() {
    local cp_id="$1" remote params cmd_id
    remote=$(cat <<'REMOTE'
echo "=== NODES ==="
k3s kubectl get nodes --no-headers
echo "=== PODS ==="
k3s kubectl -n danteplanner get pods --no-headers
echo "=== ARGOCD ==="
k3s kubectl -n argocd get applications.argoproj.io \
  -o custom-columns=NAME:.metadata.name,SYNC:.status.sync.status,HEALTH:.status.health.status --no-headers
echo "=== RDS ==="
k3s kubectl -n danteplanner exec ds/backend -- sh -c 'nc -zv -w5 "$MYSQL_HOST" 3306' 2>&1 || echo RDS_UNREACHABLE
REMOTE
)
    params=$(jq -n --arg s "$remote" '{commands: ($s | split("\n"))}')
    cmd_id=$(aws ssm send-command --region "$AWS_REGION" \
        --instance-ids "$cp_id" \
        --document-name "AWS-RunShellScript" \
        --parameters "$params" \
        --query 'Command.CommandId' --output text)
    aws ssm wait command-executed --region "$AWS_REGION" \
        --command-id "$cmd_id" --instance-id "$cp_id" 2>/dev/null || true
    aws ssm get-command-invocation --region "$AWS_REGION" \
        --command-id "$cmd_id" --instance-id "$cp_id" \
        --query 'StandardOutputContent' --output text
}

# verdict — decide healthy vs failed from the raw check output and exit accordingly.
# The output carries four sections, each after a "=== NAME ===" header:
#   NODES  : one line per node, e.g. "cp   Ready   control-plane,master  1h  v1.30"
#   PODS   : one line per danteplanner pod, cols incl. STATUS (Running / Pending / ...)
#   ARGOCD : "NAME SYNC HEALTH" per app, e.g. "danteplanner-oregon Synced Healthy"
#   RDS    : nc output; success contains "succeeded"/"open", failure prints RDS_UNREACHABLE
# Decide what is fatal vs a warning, log via log_info / log_warn / log_error, and
# `exit 1` on any fatal failure so the exit code gates a cutover. (Suggested fatal
# set: any node not Ready; any pod not Running; any app not Synced+Healthy; RDS
# unreachable.)
verdict() {
    local output="$1" fatal=0 section

    # NODES — every node must be Ready.
    section=$(awk '/=== NODES ===/{f=1;next} /^=== /{f=0} f' <<<"$output")
    if [ -z "$section" ]; then
        log_error "NODES: none reported"
        fatal=1
    elif [ -n "$(awk '$2!="Ready"{print $1}' <<<"$section")" ]; then
        log_error "NODES not Ready: $(awk '$2!="Ready"{print $1}' <<<"$section" | paste -sd' ')"
        fatal=1
    else
        log_info "NODES Ready: $(grep -c . <<<"$section")"
    fi

    # PODS — every danteplanner pod Running AND all containers ready (READY N/N).
    section=$(awk '/=== PODS ===/{f=1;next} /^=== /{f=0} f' <<<"$output")
    if [ -z "$section" ]; then
        log_error "PODS: none reported"
        fatal=1
    elif [ -n "$(awk '{split($2,r,"/"); if($3!="Running"||r[1]!=r[2]) print $1"("$3","$2")"}' <<<"$section")" ]; then
        log_error "PODS not ready: $(awk '{split($2,r,"/"); if($3!="Running"||r[1]!=r[2]) print $1"("$3","$2")"}' <<<"$section" | paste -sd' ')"
        fatal=1
    else
        log_info "PODS Running+Ready: $(grep -c . <<<"$section")"
    fi

    # ARGOCD — every app Synced AND Healthy (Progressing/OutOfSync fails the gate).
    section=$(awk '/=== ARGOCD ===/{f=1;next} /^=== /{f=0} f' <<<"$output")
    if [ -z "$section" ]; then
        log_error "ARGOCD: no applications reported"
        fatal=1
    elif [ -n "$(awk '$2!="Synced"||$3!="Healthy"{print $1"("$2"/"$3")"}' <<<"$section")" ]; then
        log_error "ARGOCD not Synced+Healthy: $(awk '$2!="Synced"||$3!="Healthy"{print $1"("$2"/"$3")"}' <<<"$section" | paste -sd' ')"
        fatal=1
    else
        log_info "ARGOCD Synced+Healthy: $(grep -c . <<<"$section")"
    fi

    # RDS — reachable from a backend pod (RDS_UNREACHABLE marker = nc failed).
    section=$(awk '/=== RDS ===/{f=1;next} /^=== /{f=0} f' <<<"$output")
    if [ -z "$section" ] || grep -q "RDS_UNREACHABLE" <<<"$section"; then
        log_error "RDS unreachable from backend pod"
        fatal=1
    else
        log_info "RDS reachable"
    fi

    if [ "$fatal" -ne 0 ]; then
        log_error "fleet is NOT cutover-ready"
        exit 1
    fi
    log_info "fleet healthy — cutover-ready"
}

main() {
    local cp_id output
    if [ "${1:-}" = "--kubeconfig" ]; then
        fetch_kubeconfig
        shift || true
    fi
    cp_id=$(resolve_cp_id)
    log_info "Verifying Oregon fleet via CP $cp_id ($AWS_REGION)"
    output=$(run_checks_on_cp "$cp_id")
    echo "$output"
    verdict "$output"
}

main "$@"

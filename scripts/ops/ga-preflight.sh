#!/bin/bash
# Global Accelerator pre-flight gate. Verifies the prerequisites that make GA's
# health checks pass BEFORE you apply terraform/global-accelerator — otherwise GA
# applies fine, then marks every endpoint unhealthy and routes nowhere. Exit 0 =
# safe to apply; non-zero = a prerequisite is missing (see the failing check).
#
# Codifies the four GA prerequisites:
#   1. Oregon applied  → ingress_instance_id is in state (GA reads it via remote_state)
#   2. SG allowlists GA → the managed-prefix-list rule exists on the ingress SG
#      (terraform/oregon applied with enable_global_accelerator = true)
#   3. App healthy      → /healthz-local returns 200 through Traefik to Spring readiness
#   4. (reminder)       → do NOT repoint Cloudflare at GA until both groups are Healthy
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

AWS_REGION="${AWS_REGION:-us-west-2}"
TF_OREGON="${TF_OREGON:-$SCRIPT_DIR/../../terraform/oregon}"
fatal=0

# 1. Oregon applied → ingress_instance_id materialized in state.
if INGRESS_ID=$(terraform -chdir="$TF_OREGON" output -raw ingress_instance_id 2>/dev/null) && [ -n "$INGRESS_ID" ]; then
    log_info "ingress_instance_id in state: $INGRESS_ID"
else
    log_error "ingress_instance_id not in Oregon state — apply terraform/oregon first (GA reads it via remote_state)"
    fatal=1
fi

# 2. GA managed-prefix-list rule present on the ingress SG (443).
if [ -n "${INGRESS_ID:-}" ]; then
    INGRESS_SG=$(aws ec2 describe-instances --region "$AWS_REGION" --instance-ids "$INGRESS_ID" \
        --query 'Reservations[0].Instances[0].SecurityGroups[?contains(GroupName, `ingress`)].GroupId | [0]' --output text 2>/dev/null || echo "")
    if [ -n "$INGRESS_SG" ] && [ "$INGRESS_SG" != "None" ] && \
       aws ec2 describe-security-group-rules --region "$AWS_REGION" \
         --filters "Name=group-id,Values=$INGRESS_SG" \
         --query 'SecurityGroupRules[?PrefixListId!=`null` && FromPort==`443`]' --output text 2>/dev/null | grep -q .; then
        log_info "ingress SG $INGRESS_SG admits a prefix list on 443 (GA health ranges)"
    else
        log_error "no GA prefix-list rule on the ingress SG — apply terraform/oregon with enable_global_accelerator=true"
        fatal=1
    fi
fi

# 3. /healthz-local returns 200 (checked on the CP via SSM, no VPN needed).
CP_ID=$(terraform -chdir="$TF_OREGON" output -raw cp_instance_id 2>/dev/null || echo "")
if [ -n "$CP_ID" ]; then
    HZ=$(aws ssm send-command --region "$AWS_REGION" --instance-ids "$CP_ID" \
        --document-name AWS-RunShellScript \
        --parameters 'commands=["curl -sk -o /dev/null -w %{http_code} https://localhost/healthz-local || echo 000"]' \
        --query 'Command.CommandId' --output text 2>/dev/null || echo "")
    if [ -n "$HZ" ]; then
        aws ssm wait command-executed --region "$AWS_REGION" --command-id "$HZ" --instance-id "$CP_ID" 2>/dev/null || true
        CODE=$(aws ssm get-command-invocation --region "$AWS_REGION" --command-id "$HZ" --instance-id "$CP_ID" \
            --query 'StandardOutputContent' --output text 2>/dev/null | tr -d '[:space:]')
        # Fail-closed: this gates a cutover, so only a clear 200 passes.
        case "$CODE" in
            200)
                log_info "/healthz-local → 200 (Spring readiness green)"
                ;;
            ""|000)
                # No TLS response: handshake rejected (mTLS misconfigured) or Traefik
                # not listening — structural, and exactly what would fail GA's check.
                log_error "/healthz-local unreachable (empty/000) — TLS handshake rejected or Traefik down; GA's health check would fail. Fix before applying GA"
                fatal=1
                ;;
            *)
                # A served but non-ready response — often transient during a rollout.
                log_error "/healthz-local → $CODE (not ready). If a rollout is in progress, re-run once it settles; a persistent non-200 blocks GA"
                fatal=1
                ;;
        esac
    else
        log_warn "could not probe /healthz-local via SSM; verify the app is serving before applying GA"
    fi
fi

if [ "$fatal" -ne 0 ]; then
    log_error "GA pre-flight FAILED — fix the above before applying terraform/global-accelerator"
    exit 1
fi
log_info "GA pre-flight passed. After apply: confirm BOTH endpoint groups Healthy in the GA console BEFORE repointing Cloudflare's api record at the GA anycast IPs."

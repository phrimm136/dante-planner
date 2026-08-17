#!/usr/bin/env bash
# Shell helpers for driving the k3s control planes over SSM. Sourced, not executed.

# Runs one command on an instance and returns its stdout; non-zero if SSM did not report Success.
ssm() {
  local region="$1" iid="$2" cmd="$3" cid status=Pending
  cid=$(aws ssm send-command --region "$region" --instance-ids "$iid" \
    --document-name AWS-RunShellScript \
    --parameters "{\"commands\":[$(printf '%s' "$cmd" | jq -Rs .)]}" \
    --query Command.CommandId --output text)
  for _ in $(seq 1 180); do
    sleep 5
    status=$(aws ssm get-command-invocation --region "$region" --command-id "$cid" \
      --instance-id "$iid" --query Status --output text 2>/dev/null || echo Pending)
    case "$status" in Success|Failed|TimedOut|Cancelled) break;; esac
  done
  aws ssm get-command-invocation --region "$region" --command-id "$cid" \
    --instance-id "$iid" --query StandardOutputContent --output text
  [ "$status" = Success ]
}

cp_id() {
  aws ec2 describe-instances --region "$1" \
    --filters "Name=tag:Name,Values=$2" "Name=instance-state-name,Values=running" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text
}

# ArgoCD here is core-install: no API server, no argocd CLI on any node, so sync policy is
# changed by patching the Application CR.
argocd_pause() { ssm "$1" "$2" "sudo k3s kubectl -n argocd patch application danteplanner-$3 --type merge -p '{\"spec\":{\"syncPolicy\":null}}'"; }
argocd_resume() { ssm "$1" "$2" "sudo k3s kubectl -n argocd patch application danteplanner-$3 --type merge -p '{\"spec\":{\"syncPolicy\":{\"automated\":{\"prune\":true,\"selfHeal\":true}}}}'"; }

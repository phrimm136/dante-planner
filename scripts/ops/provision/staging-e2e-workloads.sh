#!/usr/bin/env bash
# Deploy the staging-only in-cluster workloads that ArgoCD deliberately does not manage:
# cloudflared (both regions), the OAuth stub IdP, and the SPA server. Manifests travel to
# each control plane as a presigned S3 bundle and are applied with kubectl over SSM —
# see docs/adr/035 for why this bypasses GitOps.  Requires frontend/dist to be built.
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
region=us-west-2
seoul_region=ap-northeast-2
account=$(aws sts get-caller-identity --query Account --output text)
bucket="danteplanner-staging-e2e-artifacts"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

aws s3api head-bucket --bucket "$bucket" 2>/dev/null \
  || aws s3 mb "s3://$bucket" --region "$region" >/dev/null

# SPA dist, fetched by the pod's initContainer via a presigned URL.
tar -czf "$work/dist.tar.gz" -C "$repo_root/frontend/dist" .
aws s3 cp "$work/dist.tar.gz" "s3://$bucket/staging-dist.tar.gz" --region "$region" >/dev/null
dist_url=$(aws s3 presign "s3://$bucket/staging-dist.tar.gz" --expires-in 86400 --region "$region")

mkdir -p "$work/render"
python3 - "$repo_root" "$work" "$dist_url" <<'EOF'
import pathlib, sys
repo, work, url = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3]
r = work/"render"
(r/"frontend.yaml").write_text(
    (repo/"deploy/staging-e2e/frontend.yaml").read_text().replace("DIST_URL_PLACEHOLDER", url))
(r/"oauth-stub.yaml").write_text((repo/"deploy/staging-e2e/oauth-stub.yaml").read_text())
cf = (repo/"deploy/base/cloudflared.yaml").read_text()
(r/"cloudflared-oregon.yaml").write_text(cf)
(r/"cloudflared-seoul.yaml").write_text(cf.replace("key: oregon", "key: seoul"))
EOF

tar -czf "$work/k8s.tar.gz" -C "$work/render" .
aws s3 cp "$work/k8s.tar.gz" "s3://$bucket/k8s-staging-e2e.tar.gz" --region "$region" >/dev/null
k8s_url=$(aws s3 presign "s3://$bucket/k8s-staging-e2e.tar.gz" --expires-in 900 --region "$region")

cp_instance() { # region name-tag
  local id
  id=$(aws ec2 describe-instances --region "$1" \
    --filters "Name=tag:Name,Values=$2" "Name=instance-state-name,Values=running" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text)
  [ -n "$id" ] && [ "$id" != "None" ] || { echo "no running $2 instance in $1" >&2; return 1; }
  printf '%s' "$id"
}

apply_on() { # region instance-id manifests...
  local r=$1 id=$2; shift 2
  local params
  params=$(jq -n --arg url "$k8s_url" --args '
    {commands: (
      ["curl -fsSL \($url | @sh) -o /tmp/k8s.tar.gz",
       "mkdir -p /tmp/k8s-e2e && tar -xzf /tmp/k8s.tar.gz -C /tmp/k8s-e2e"]
      + [$ARGS.positional[] | "kubectl apply -n danteplanner -f /tmp/k8s-e2e/\(.)"]
      + ["rm -rf /tmp/k8s.tar.gz /tmp/k8s-e2e"]
    )}' "$@")
  local cmd_id
  cmd_id=$(aws ssm send-command --region "$r" --instance-ids "$id" \
    --document-name AWS-RunShellScript \
    --parameters "$params" \
    --query 'Command.CommandId' --output text)
  aws ssm wait command-executed --region "$r" --command-id "$cmd_id" --instance-id "$id"
  aws ssm get-command-invocation --region "$r" --command-id "$cmd_id" --instance-id "$id" \
    --query 'StandardOutputContent' --output text
}

oregon_cp=$(cp_instance "$region" danteplanner-oregon-cp)
seoul_cp=$(cp_instance "$seoul_region" danteplanner-seoul-cp)

apply_on "$region"       "$oregon_cp" cloudflared-oregon.yaml oauth-stub.yaml frontend.yaml
apply_on "$seoul_region" "$seoul_cp"  cloudflared-seoul.yaml

# Pin the backend tag through the ArgoCD Application's kustomize override — the ONLY
# mechanism that survives selfHeal; a direct `kubectl set image` is reverted within one
# sync loop.
if [[ -n "${BACKEND_IMAGE_TAG:-}" ]]; then
  pin_on() { # region instance-id app  — the fleet pulls from ITS region's registry
    local image="danteplanner-backend=$account.dkr.ecr.$1.amazonaws.com/danteplanner-backend:$BACKEND_IMAGE_TAG"
    local patch params cmd_id
    patch=$(IMAGE="$image" python3 -c "import json,os;print(json.dumps({'spec':{'source':{'kustomize':{'images':[os.environ['IMAGE']]}}}}))")
    params=$(jq -n --arg patch "$patch" --arg app "$3" '
      {commands: [
        "kubectl -n argocd patch application \($app | @sh) --type merge -p \($patch | @sh)",
        "kubectl -n argocd annotate application \($app | @sh) argocd.argoproj.io/refresh=hard --overwrite"
      ]}')
    cmd_id=$(aws ssm send-command --region "$1" --instance-ids "$2" \
      --document-name AWS-RunShellScript \
      --parameters "$params" \
      --query 'Command.CommandId' --output text)
    aws ssm wait command-executed --region "$1" --command-id "$cmd_id" --instance-id "$2"
  }
  pin_on "$region"       "$oregon_cp" danteplanner-oregon
  pin_on "$seoul_region" "$seoul_cp"  danteplanner-seoul
fi

echo "workloads applied"

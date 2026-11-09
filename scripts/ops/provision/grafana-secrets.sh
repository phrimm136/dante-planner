#!/usr/bin/env bash
# Write the Grafana Cloud credential bundle danteplanner/grafana/cloud: the Loki push pair, the
# remote_write pair, and the observability read token (logs:read + metrics:read). The bundle is
# written whole, so every run supplies all five values. The container is created with its Seoul
# replica when absent; values go to the PRIMARY region and AWS replicates them.
# Usage: grafana-secrets.sh             (values prompted, never echoed, never on disk)
#        grafana-secrets.sh --migrate   (copies the five single-value containers, no prompt)
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
# shellcheck source=../lib/secrets.sh
source "$repo_root/scripts/ops/lib/secrets.sh"
# shellcheck source=../lib/constants.sh
source "$repo_root/scripts/ops/lib/constants.sh"
region=us-west-2
replica_region=ap-northeast-2
name=$SECRET_GRAFANA_BUNDLE

old() { aws secretsmanager get-secret-value --region "$region" --secret-id "$1" --output json; }

case "${1:-}" in
  --migrate)
    bundle=$(
      {
        old danteplanner/grafana/loki-username
        old danteplanner/grafana/loki-password
        old danteplanner/grafana/remote-write-username
        old danteplanner/grafana/remote-write-password
        old danteplanner/grafana/observability-read-token
      } | jq -cs 'if length != 5 or any(.[]; .SecretString | type != "string")
                  then error("expected five string secrets") else
                  {loki_username: .[0].SecretString, loki_password: .[1].SecretString,
                   remote_write_username: .[2].SecretString, remote_write_password: .[3].SecretString,
                   observability_read_token: .[4].SecretString} end'
    )
    ;;
  "")
    read -rp  "Grafana Cloud Loki numeric user ID (from the stack's Loki 'Send Logs' page): " LK_USER
    read -rsp "Access-policy token with logs:write (hidden): " LK_TOKEN; echo
    read -rp  "Grafana Cloud stack numeric ID (remote_write username): " GC_USER
    read -rsp "Access-policy token with metrics:write (hidden): " GC_TOKEN; echo
    read -rsp "Access-policy token with logs:read + metrics:read (hidden): " RD_TOKEN; echo
    bundle=$(LK_USER=$LK_USER LK_TOKEN=$LK_TOKEN GC_USER=$GC_USER GC_TOKEN=$GC_TOKEN RD_TOKEN=$RD_TOKEN \
      jq -nc '{loki_username: env.LK_USER, loki_password: env.LK_TOKEN,
               remote_write_username: env.GC_USER, remote_write_password: env.GC_TOKEN,
               observability_read_token: env.RD_TOKEN}')
    ;;
  *) echo "usage: grafana-secrets.sh [--migrate]" >&2; exit 2 ;;
esac

if ! secret_exists "$region" "$name"; then
  create_secret "$region" "$name" --add-replica-regions "Region=$replica_region"
  echo "  created  $name  (with its $replica_region replica)"
fi
printf %s "$bundle" | put_secret "$region" "$name"
echo "  updated  $name  (replicates to $replica_region)"
echo "done — force ESO if impatient:"
echo "  kubectl -n danteplanner annotate externalsecret grafana-loki-write grafana-remote-write force-sync=\$(date +%s) --overwrite"
echo "verify the read token: .claude/skills/ops/bin/ops logs '{app=\"backend\"}' --since 1h --limit 5"

#!/usr/bin/env bash
# Write the mysqld-exporter bundle danteplanner/mysqld-exporter/dsn (monitoring user, both RDS
# endpoints), print the SQL for the RDS monitoring user, then watch the ExternalSecret/pod heal.
# The bundle is written whole, so every run supplies all four values. The container is created
# with its Seoul replica when absent; values go to the PRIMARY region and AWS replicates them.
# Usage: mysqld-secrets.sh             (values prompted, never echoed, never written to disk)
#        mysqld-secrets.sh --migrate   (copies the four single-value containers, no prompt)
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
# shellcheck source=../lib/secrets.sh
source "$repo_root/scripts/ops/lib/secrets.sh"
# shellcheck source=../lib/constants.sh
source "$repo_root/scripts/ops/lib/constants.sh"
region=us-west-2
replica_region=ap-northeast-2
name=$SECRET_MYSQLD_EXPORTER_BUNDLE

old() { aws secretsmanager get-secret-value --region "$region" --secret-id "$1" --output json; }

write() {
  if ! secret_exists "$region" "$name"; then
    create_secret "$region" "$name" --add-replica-regions "Region=$replica_region"
    echo "  created  $name  (with its $replica_region replica)"
  fi
  printf %s "$bundle" | put_secret "$region" "$name"
  echo "  updated  $name  (replicates to $replica_region)"
}

case "${1:-}" in
  --migrate)
    bundle=$(
      {
        old danteplanner/mysqld-exporter/username
        old danteplanner/mysqld-exporter/password
        old danteplanner/mysqld-exporter/primary-endpoint
        old danteplanner/mysqld-exporter/replica-endpoint
      } | jq -cs 'if length != 4 or any(.[]; .SecretString | type != "string")
                  then error("expected four string secrets") else
                  {username: .[0].SecretString, password: .[1].SecretString,
                   primary_endpoint: .[2].SecretString, replica_endpoint: .[3].SecretString} end'
    )
    write
    exit 0
    ;;
  "") ;;
  *) echo "usage: mysqld-secrets.sh [--migrate]" >&2; exit 2 ;;
esac

read -rp    "monitoring username [exporter]: " EXP_USER; EXP_USER=${EXP_USER:-exporter}
read -rsp   "monitoring password (hidden): " EXP_PASS; echo
read -rp    "Oregon RDS primary endpoint (host:3306): " PRIMARY_EP
read -rp    "Seoul RDS replica endpoint (host:3306): " REPLICA_EP

bundle=$(EXP_USER=$EXP_USER EXP_PASS=$EXP_PASS PRIMARY_EP=$PRIMARY_EP REPLICA_EP=$REPLICA_EP \
  jq -nc '{username: env.EXP_USER, password: env.EXP_PASS,
           primary_endpoint: env.PRIMARY_EP, replica_endpoint: env.REPLICA_EP}')
write

cat <<SQL

== now create the RDS monitoring user (runs on the PRIMARY; replicates to Seoul).
Open a client INSIDE the cluster/VPC (RDS is not publicly reachable), e.g.:
  kubectl --kubeconfig ~/.kube/dante-oregon -n danteplanner run mysql-client \\
    --rm -it --image=mysql:8 -- mysql -h ${PRIMARY_EP%%:*} -u admin -p
then run:
  CREATE USER '${EXP_USER}'@'%' IDENTIFIED BY '<the password>' WITH MAX_USER_CONNECTIONS 3;
  GRANT PROCESS, REPLICATION CLIENT ON *.* TO '${EXP_USER}'@'%';
  GRANT SELECT ON performance_schema.* TO '${EXP_USER}'@'%';
SQL

echo "== after the SQL, watch the exporter heal (per region):"
echo "  kubectl --kubeconfig <kc> -n danteplanner annotate externalsecret --all force-sync=\$(date +%s) --overwrite"
echo "  kubectl --kubeconfig <kc> -n danteplanner get externalsecret,pod -l app=mysqld-exporter"
echo "Healthy = ExternalSecret SecretSynced, pod 1/1 Running (the CreateContainerConfigError clears itself)."

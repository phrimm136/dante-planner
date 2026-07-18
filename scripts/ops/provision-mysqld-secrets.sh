#!/usr/bin/env bash
# Inject the mysqld-exporter secret VALUES (monitoring user, both RDS endpoints),
# print the SQL for the RDS monitoring user, then watch the ExternalSecret/pod heal.
# The secret containers are Terraform-managed (terraform/secrets, cross-region
# replication included) — this script only writes versions into the PRIMARY
# region; AWS replicates them to Seoul. A missing container means the secrets
# stack was never applied.
# Usage: c1-provision-secrets.sh
# Values are prompted, never echoed, never written to disk (secrets never enter the repo).
set -euo pipefail

read -rp    "monitoring username [exporter]: " EXP_USER; EXP_USER=${EXP_USER:-exporter}
read -rsp   "monitoring password (hidden): " EXP_PASS; echo
read -rp    "Oregon RDS primary endpoint (host:3306): " PRIMARY_EP
read -rp    "Seoul RDS replica endpoint (host:3306): " REPLICA_EP

put() { # name value
  local name=$1 value=$2
  if aws secretsmanager put-secret-value --region us-west-2 --secret-id "$name" \
       --secret-string "$value" >/dev/null; then
    echo "  updated  $name  (replicates to ap-northeast-2)"
  else
    echo "ERROR: put-secret-value failed for $name — if the secret does not exist," >&2
    echo "the container is Terraform-managed: apply terraform/secrets first." >&2
    exit 1
  fi
}

put danteplanner/mysqld-exporter/username         "$EXP_USER"
put danteplanner/mysqld-exporter/password         "$EXP_PASS"
put danteplanner/mysqld-exporter/primary-endpoint "$PRIMARY_EP"
put danteplanner/mysqld-exporter/replica-endpoint "$REPLICA_EP"

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

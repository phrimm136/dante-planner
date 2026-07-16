#!/usr/bin/env bash
# Create the three mysqld-exporter Secrets Manager entries in BOTH
# regions (each region's SecretStore resolves only its own region), print the SQL
# for the RDS monitoring user, then watch the ExternalSecret/pod heal.
# Usage: c1-provision-secrets.sh
# Values are prompted, never echoed, never written to disk (secrets never enter the repo).
set -euo pipefail

read -rp    "monitoring username [exporter]: " EXP_USER; EXP_USER=${EXP_USER:-exporter}
read -rsp   "monitoring password (hidden): " EXP_PASS; echo
read -rp    "Oregon RDS primary endpoint (host:3306): " PRIMARY_EP

put() { # region name value
  local region=$1 name=$2 value=$3
  if aws secretsmanager create-secret --region "$region" --name "$name" \
       --secret-string "$value" >/dev/null 2>&1; then
    echo "  created  $region  $name"
  else
    aws secretsmanager put-secret-value --region "$region" --secret-id "$name" \
      --secret-string "$value" >/dev/null
    echo "  updated  $region  $name"
  fi
}

for region in us-west-2 ap-northeast-2; do
  echo "== $region"
  put "$region" danteplanner/mysqld-exporter/username         "$EXP_USER"
  put "$region" danteplanner/mysqld-exporter/password         "$EXP_PASS"
  put "$region" danteplanner/mysqld-exporter/primary-endpoint "$PRIMARY_EP"
done

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

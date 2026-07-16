#!/usr/bin/env bash
# Metrics probe battery against one region's local Prometheus.
# Usage: f-verify-probes.sh <kubeconfig> <cluster-name oregon|seoul>
set -euo pipefail
KC=${1:?usage: f-verify-probes.sh <kubeconfig> <oregon|seoul>}
NAME=${2:?cluster name required}
k() { kubectl --kubeconfig "$KC" "$@"; }
q() { k -n danteplanner exec deploy/prometheus -- \
       wget -qO- --post-data "query=$1" http://localhost:9090/api/v1/query |
       jq -r '.data.result[0].value[1] // "absent"'; }

printf '%-46s %s\n' "PROBE ($NAME)" "RESULT"

up_ksm=$(q 'up{job="kube-state-metrics"}')
printf '%-46s %s\n' "up{job=kube-state-metrics} == 1" "$up_ksm"

up_mysqld=$(q 'up{job="mysqld"}')
printf '%-46s %s\n' "up{job=mysqld} == 1 (needs mysqld credentials)" "$up_mysqld"

ready=$(q 'count(kube_node_status_condition{condition="Ready",status="true"} == 1)')
actual=$(k get nodes --no-headers 2>/dev/null | wc -l | tr -d ' ')
printf '%-46s %s\n' "KSM ready-node count vs kubectl ($actual)" "$ready"

series=$(q 'prometheus_tsdb_head_series')
printf '%-46s %s\n' "head series (weaker absolute check, budget ~2k over pre-KSM base)" "$series"

etcd_age=$(q '(time() - max(kube_etcd_snapshot_creation_timestamp_seconds))')
printf '%-46s %s\n' "etcd snapshot age seconds (fresh < 1.5x interval)" "$etcd_age"

up_gaps=$(k -n danteplanner exec deploy/prometheus -- \
  wget -qO- --post-data 'query=up{job=~"argocd|external-secrets|coredns|apiserver|etcd"}' \
  http://localhost:9090/api/v1/query | jq -r '.data.result[] | .metric.job + "=" + .value[1]' | paste -sd' ' -)
printf '%-46s %s\n' "gap-cluster jobs up (needs the etcd flag on live CPs)" "${up_gaps:-none}"

echo
echo "Notes: 'absent' for the etcd snapshot metric before the etcd flag is enabled (or before a snapshot has"
echo "occurred since KSM started) is expected. The kubectl pod-level cross-check and the"
echo "node Ready-flip drill remain manual — see manual kubectl cross-checks."

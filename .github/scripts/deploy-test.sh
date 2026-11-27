#!/usr/bin/env bash
set -euo pipefail

. "$(dirname "$0")/images.sh"
KUBECONFORM_IMAGE=ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e
K3S_IMAGE=rancher/k3s:v1.36.3-k3s1@sha256:d0f79175794edd9694b4a12bafc5c52ae1977369a2f7cf256264e7bd2dae0be9
WHOAMI_IMAGE=traefik/whoami:v1.10.2@sha256:1474027c316661cdec87df2623e13a41e7e1ce0ba99c24917631de8f300b5420
PROMETHEUS_IMAGE=prom/prometheus:v3.5.0@sha256:63805ebb8d2b3920190daf1cb14a60871b16fd38bed42b857a3182bc621f4996
LOKI_IMAGE=grafana/loki:3.7.10@sha256:b43c257ccfc925d4a50b8717cac61a3b51df8c48d01847936057e5c4075a24a7
K3S_TAG=${K3S_IMAGE#*:}
K3S_TAG=${K3S_TAG%@*}
KUBERNETES_VERSION=${K3S_TAG#v}
KUBERNETES_VERSION=${KUBERNETES_VERSION%%-*}
KUBERNETES_SCHEMAS=https://raw.githubusercontent.com/yannh/kubernetes-json-schema/89a35a5e9a1412a692a2603d79c3786f602238a4
GATEWAY_API_CRDS=https://raw.githubusercontent.com/kubernetes-sigs/gateway-api/690f754646e8326128fd686e3e46117ac479cfdf/config/crd/standard
ACCEPT_LANGUAGE='ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7,ja;q=0.6,zh-CN;q=0.5,zh;q=0.4,de;q=0.3,fr;q=0.2,es;q=0.1,it;q=0.05'
PROBE_ATTEMPTS=30
LOKI_READY_ATTEMPTS=90

usage() {
  echo "usage: $0 [deploy-dir]   (default: the repository's deploy/; needs docker, bash, curl and python3 with PyYAML)" >&2
  exit 2
}

[ $# -le 1 ] || usage
case "${1:-}" in -*) usage ;; esac

repo=$(cd "$(dirname "$0")/../.." && pwd)
harness=$repo/.github/deploy-test
deploy_dir=$(cd "${1:-$repo/deploy}" && pwd)
regions=()
for values_file in "$deploy_dir"/chart/values-*.yaml; do
  [ -e "$values_file" ] || continue
  region=$(basename "$values_file" .yaml)
  regions+=("${region#values-}")
done
work=$(mktemp -d)
cluster=deploy-test-k3s-$$
loki=deploy-test-loki-$$
started=$SECONDS

cleanup() {
  docker rm -fv "$cluster" >/dev/null 2>&1 || true
  docker rm -fv "$loki" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  echo "$1: FAIL - $2" >&2
  exit 1
}

pass() {
  echo "$1: ok - $2"
}

docs_of_kind() {
  awk -v want="$1" '
    function flush() {
      if (doc != "" && kind == want) printf "---\n%s", doc
      doc = ""; kind = ""
    }
    /^---[[:space:]]*$/ { flush(); next }
    { doc = doc $0 "\n" }
    /^kind:/ { kind = $2 }
    END { flush() }
  '
}

doc_named() {
  awk -v want_kind="$1" -v want_name="$2" '
    function flush() {
      if (doc != "" && kind == want_kind && name == want_name) printf "---\n%s", doc
      doc = ""; kind = ""; name = ""; top = ""
    }
    /^---[[:space:]]*$/ { flush(); next }
    { doc = doc $0 "\n" }
    /^[A-Za-z]/ { top = $1 }
    /^kind:/ { kind = $2 }
    top == "metadata:" && /^  name:/ && name == "" { name = $2; gsub(/["'\'']/, "", name) }
    END { flush() }
  '
}

first_image() {
  awk '$1 == "image:" || ($1 == "-" && $2 == "image:") { print $NF; exit }'
}

promtool_test() {
  docker run --rm --user "$(id -u):$(id -g)" -v "$1:/alerts:ro" -w /alerts --entrypoint promtool "$PROMETHEUS_IMAGE" \
    test rules tests.yaml
}

failed_cases() {
  awk '/^ *name: / { sub(/^ *name: /, ""); sub(/,[[:space:]]*$/, ""); print }' | paste -sd ',' - | sed 's/,/, /g'
}

alerts() {
  local rules_py=$deploy_dir/grafana/rules.py out written fixture_failures loki_addr loki_summary
  if ! out=$(python3 -m unittest discover -s "$deploy_dir/grafana/tests" 2>&1); then
    echo "$out" >&2
    fail alerts "the rules.py loader tests failed"
  fi
  written=$(python3 "$rules_py" promtool "$work/alerts") || fail alerts "rules.py could not build the promtool files"
  if ! out=$(promtool_test "$work/alerts" 2>&1); then
    echo "$out" >&2
    fail alerts "promtool failed these cases: $(printf '%s\n' "$out" | failed_cases); a failed fires case means the rule never yields a value above 0, which Grafana's threshold needs"
  fi

  python3 "$rules_py" --rules "$harness/alerts/dead-rule-fixture.yaml" promtool "$work/dead-rule" >/dev/null \
    || fail alerts "rules.py could not build the dead-rule fixture"
  if out=$(promtool_test "$work/dead-rule" 2>&1); then
    fail alerts "the dead-rule fixture passed promtool; the wrapper no longer catches an expression that filters to 0"
  fi
  fixture_failures=$(printf '%s\n' "$out" | failed_cases)
  if [ "$fixture_failures" != "dead-rule-fixture fires[0]" ]; then
    echo "$out" >&2
    fail alerts "the dead-rule fixture failed with '${fixture_failures:-no named case}', want only 'dead-rule-fixture fires[0]'"
  fi

  python3 "$rules_py" loki-cases > "$work/loki-cases.jsonl" || fail alerts "rules.py could not list the Loki cases"
  docker run --rm -d --name "$loki" -p 127.0.0.1::3100 "$LOKI_IMAGE" -config.file=/etc/loki/local-config.yaml >/dev/null
  loki_addr=$(docker port "$loki" 3100/tcp | awk 'NR == 1')
  retry "$LOKI_READY_ATTEMPTS" curl -sf "http://$loki_addr/ready" \
    || fail alerts "Loki ($LOKI_IMAGE) did not become ready within ${LOKI_READY_ATTEMPTS}s"
  if ! out=$(python3 "$harness/alerts/loki-cases.py" "http://$loki_addr" < "$work/loki-cases.jsonl" 2>&1); then
    echo "$out" >&2
    fail alerts "a Loki rule case failed against $LOKI_IMAGE"
  fi
  loki_summary=$(printf '%s\n' "$out" | awk 'END { print }')
  docker rm -fv "$loki" >/dev/null 2>&1 || true
  pass alerts "${written% written to *}, all pass; the dead-rule fixture fails its fires case; $loki_summary"
}

helm_chart() {
  docker run --rm --network none -v "$deploy_dir/chart:/chart:ro" "$HELM_IMAGE" template "danteplanner-$1" /chart \
    -f "/chart/values-$1.yaml" --set image.repository=registry.example/danteplanner-backend "${@:2}"
}

chart_refuses() {
  local region=$1
  ! helm_chart "$@" >/dev/null 2>"$work/refusal.err" \
    || fail render "values-$region.yaml with ${*:2} rendered; values.schema.json no longer refuses it"
  grep -q "don't meet the specifications of the schema" "$work/refusal.err" \
    || fail render "values-$region.yaml with ${*:2} failed for another reason than the schema: $(cat "$work/refusal.err")"
}

rendered_contract() {
  local region=$1 tag image
  tag=$(sed -n 's/^  tag: "\([0-9a-f]\{40\}\)"$/\1/p' "$deploy_dir/chart/values-$region.yaml")
  image=$(doc_named DaemonSet backend < "$work/$region.yaml" | first_image)
  { [ -n "$tag" ] && [ "$image" = "registry.example/danteplanner-backend:$tag" ]; } \
    || fail render "the backend image of $region is '${image:-(none)}', want registry.example/danteplanner-backend:${tag:-(no tag in values-$region.yaml)}; it must take image.repository and image.tag"
}

render() {
  local region probes counts=() renders=()
  [ "${#regions[@]}" -gt 0 ] || fail render "no values-<region>.yaml under $deploy_dir/chart"
  for region in "${regions[@]}"; do
    if ! helm_chart "$region" > "$work/$region.yaml" 2> "$work/$region.render.err"; then
      cat "$work/$region.render.err" >&2
      fail render "helm could not render deploy/chart with values-$region.yaml"
    fi
    rendered_contract "$region"
    counts+=("$region: $(awk '/^kind:/ { n++ } END { print n + 0 }' "$work/$region.yaml") resources")
    renders+=("$work/$region.yaml")
  done
  if ! probes=$(python3 "$repo/.github/scripts/rendered-redis-probes.py" "${renders[@]}" 2>&1); then
    echo "$probes" >&2
    fail render "the Redis containers no longer render their probes (listed above): a TCP liveness probe on 6379, and a readiness probe that passes only when PING answers PONG, so a Redis replaying its AOF (which answers LOADING) leaves its Service; restore them in deploy/chart/templates/redis-auth.yaml or redis-ratelimit.yaml"
  fi
  chart_refuses seoul --set region=null
  chart_refuses seoul --set region=oregon
  chart_refuses seoul --set backendConfig.DEPLOY_REGION=us-west-2
  chart_refuses seoul --set backendConfig.REDIS_AUTHLOCAL_HOST=null
  chart_refuses seoul --set redisAuth.replicaOf.host=
  chart_refuses oregon --set image.tag=latest
  pass render "$(printf '%s\n' "${counts[@]}" | paste -sd ',' - | sed 's/,/, /g'), the Redis containers keep their probes, 6 wrong values are refused"
}

validate() {
  local stage=$1 out
  shift
  if ! out=$(docker run --rm -v "$harness/schemas:/schemas:ro" -v "$work:/render:ro" "$KUBECONFORM_IMAGE" \
    -strict -summary -kubernetes-version "$KUBERNETES_VERSION" \
    -schema-location "$KUBERNETES_SCHEMAS/{{.NormalizedKubernetesVersion}}-standalone{{.StrictSuffix}}/{{.ResourceKind}}{{.KindSuffix}}.json" \
    -schema-location '/schemas/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' \
    "$@" 2>&1); then
    echo "$out" >&2
    fail "$stage" "kubeconform rejected the render; a 'could not find schema' error on a custom kind means a new CRD: add it to .github/deploy-test/vendor-schemas.py and rerun it; on a built-in kind it means KUBERNETES_SCHEMAS has no v$KUBERNETES_VERSION directory: move it to a commit that has one"
  fi
  printf '%s\n' "$out" | awk '/^Summary:/ { sub(/^Summary: /, ""); print }'
}

charts() {
  local report drift region chart crds exporters summary files=() renders=()
  for region in "${regions[@]}"; do renders+=("$work/$region.yaml"); done
  drift=$(python3 - "${renders[@]}" <<'PY'
import pathlib
import sys

import yaml


def applications(path):
    docs = yaml.safe_load_all(path.read_text())
    return {doc["metadata"]["name"]: doc for doc in docs if doc and doc.get("kind") == "Application"}


first, *others = map(pathlib.Path, sys.argv[1:])
expected = applications(first)
for path in others:
    found = applications(path)
    for name in sorted(set(expected) | set(found)):
        if expected.get(name) != found.get(name):
            print(f"Application {name} differs between {first.stem} and {path.stem}")
PY
  )
  [ -z "$drift" ] || fail charts "$(printf '%s\n' "$drift" | paste -sd ',' - | sed 's/,/, /g'); an Application is identical in every region"
  mkdir -p "$work/charts"
  if ! report=$("$repo/.github/scripts/render-charts.sh" "$work/charts" "${renders[@]}" 2>"$work/charts.err"); then
    cat "$work/charts.err" >&2
    fail charts "helm could not render the chart of an Application that deploy/chart renders"
  fi
  for chart in "$work"/charts/chart-*.yaml; do
    [ -e "$chart" ] && files+=("/render/charts/$(basename "$chart")")
  done
  [ "${#files[@]}" -gt 0 ] || fail charts "deploy/chart renders no Application with a Helm chart source"
  if ! crds=$(python3 "$repo/.github/scripts/rendered-crds.py" "${renders[@]}" "$work"/charts/chart-*.yaml 2>&1); then
    echo "$crds" >&2
    fail charts "the custom kinds in use and the rendered CRDs do not match. A CRD that a chart stops rendering is pruned, and its removal deletes every object of its kind: if it was removed by accident, restore it; if on purpose, remove the objects that use it in the same merge. The removal of a whole Application leaves its CRDs in the cluster, to be deleted by hand. An OUTSIDE_GROUPS line above is an entry to remove from .github/scripts/rendered-crds.py"
  fi
  if ! exporters=$(python3 "$repo/.github/scripts/rendered-exporters.py" --regions "${renders[@]}" --charts "$work"/charts/chart-*.yaml 2>&1); then
    echo "$exporters" >&2
    fail charts "the exporters no longer render what their metrics depend on (listed above): the scrape annotations and the app label that name the series and logs, the args of node-exporter and mysqld-exporter, the mysqld-exporter endpoint, credentials, my.cnf and CA, the args, address and password of the Redis exporters, or the Secret and CA ConfigMap that mysqld-exporter reads from a region render. Helm ignores a value that a chart raise renamed: set the new name in the Application under deploy/chart/templates"
  fi
  # The schema set holds none for CustomResourceDefinition.
  summary=$(validate charts -skip CustomResourceDefinition "${files[@]}")
  pass charts "$(printf '%s\n' "$report" | paste -sd ',' - | sed 's/,/, /g'); every custom kind in use has its CRD; the exporters keep their contract; $summary"
}

schema() {
  local region summary fleet_k3s files=()
  fleet_k3s=$(awk '/^variable "k3s_version"/ { inside = 1 } inside && /^[[:space:]]*default[[:space:]]*=/ { gsub(/^[^"]*"|".*$/, ""); print; exit }' \
    "$repo/terraform/modules/fleet/variables.tf")
  [ "${fleet_k3s//+/-}" = "$K3S_TAG" ] \
    || fail schema "terraform/modules/fleet/variables.tf installs k3s ${fleet_k3s:-(no k3s_version default found)} but .github/scripts/deploy-test.sh tests $K3S_TAG (K3S_IMAGE); raise both to the same release"
  for region in "${regions[@]}"; do files+=("/render/$region.yaml"); done
  summary=$(validate schema "${files[@]}")
  pass schema "$summary"
}

kube() {
  docker exec "$cluster" kubectl "$@"
}

load_image() {
  local ref=$1 tag=${1%@*}
  docker image inspect "$ref" >/dev/null 2>&1 || docker pull -q "$ref" >/dev/null
  [ "$ref" = "$tag" ] || docker tag "$ref" "$tag"
  docker save "$tag" | docker exec -i "$cluster" ctr -n k8s.io images import - >/dev/null
}

retry() {
  local attempts=$1 i
  shift
  for ((i = 1; i <= attempts; i++)); do
    "$@" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

start_cluster() {
  docker run -d --privileged --name "$cluster" "$K3S_IMAGE" server \
    --disable=traefik,servicelb,metrics-server,local-storage,coredns >/dev/null
  retry 90 docker exec "$cluster" kubectl get --raw /readyz \
    || fail behavior "k3s API did not become ready within 90s"
  retry 60 sh -c "docker exec $cluster kubectl get nodes -o name | awk 'END { exit NR == 0 }'" \
    || fail behavior "no k3s node registered within 60s"
  kube wait --for=condition=Ready node --all --timeout=120s >/dev/null
  local crd
  for crd in gatewayclasses gateways httproutes referencegrants grpcroutes; do
    kube apply --server-side -f "$GATEWAY_API_CRDS/gateway.networking.k8s.io_$crd.yaml" >/dev/null
  done
  kube wait --for=condition=Established crd --all --timeout=60s >/dev/null
  kube create namespace danteplanner >/dev/null
  load_image "$WHOAMI_IMAGE"
}

backend_rules() {
  kube -n danteplanner get httproute \
    -o jsonpath='{range .items[*]}{"route\t"}{.metadata.name}{"\n"}{range .spec.rules[*]}{"rule"}{range .backendRefs[*]}{"\t"}{.name}{end}{"\n"}{range .matches[*]}{"match\t"}{.path.type}{"\t"}{.path.value}{"\t"}{.headers[*].name}{"\t"}{.queryParams[*].name}{"\t"}{.method}{"\n"}{end}{end}{end}' \
    | rule_matches
}

rule_matches() {
  awk -F '\t' '
    function flush() { if (backend && matches == 0) print route "\t" rule "\tPathPrefix\t/\t"; backend = 0 }
    $1 == "route" { flush(); route = $2; rule = -1; next }
    $1 == "rule" { flush(); rule++; matches = 0; for (i = 2; i <= NF; i++) if ($i == "backend") backend = 1; next }
    $1 == "match" {
      matches++; conditions = ""
      if ($4 != "") conditions = "headers"
      if ($5 != "") conditions = conditions (conditions == "" ? "" : " and ") "queryParams"
      if ($6 != "") conditions = conditions (conditions == "" ? "" : " and ") "method"
      if (backend) print route "\t" rule "\t" ($2 == "" ? "PathPrefix" : $2) "\t" ($3 == "" ? "/" : $3) "\t" conditions
    }
    END { flush() }
  '
}

unsupported_matches() {
  awk -F '\t' '
    $3 != "PathPrefix" && $3 != "Exact" { printf "%sHTTPRoute %s rule %s matches path type %s", (n++ ? ", " : ""), $1, $2, $3 }
    $5 != "" { printf "%sHTTPRoute %s rule %s matches on %s", (n++ ? ", " : ""), $1, $2, $5 }
  '
}

SERVES_AWK='
  function serves(type, value, path) {
    if (type == "Exact") return path == value
    sub(/\/+$/, "", value)
    return path == value || index(path, value "/") == 1
  }
'

unserved_rows() {
  awk -F '\t' "$SERVES_AWK"'
    NR == FNR { k = $1 SUBSEP $2; n[k]++; type[k, n[k]] = $3; value[k, n[k]] = $4; next }
    {
      k = $1 SUBSEP $2; served = 0; listed = ""
      for (i = 1; i <= n[k]; i++) {
        listed = listed (i > 1 ? ", " : "") type[k, i] " " value[k, i]
        if (serves(type[k, i], value[k, i], $3)) served = 1
      }
      if (!served) printf "%s%s rule %s request_path %s (its matches: %s)", (bad++ ? ", " : ""), $1, $2, $3, listed
    }
  ' "$1" -
}

routed_unrouted_rows() {
  awk -F '\t' "$SERVES_AWK"'
    NR == FNR { n++; route[n] = $1; rule[n] = $2; type[n] = $3; value[n] = $4; next }
    {
      for (i = 1; i <= n; i++)
        if (serves(type[i], value[i], $1)) printf "%s%s (HTTPRoute %s rule %s matches %s %s)", (bad++ ? ", " : ""), $1, route[i], rule[i], type[i], value[i]
    }
  ' "$1" -
}

contract_rows() {
  awk -F '\t' 'NR > 1 && NF' "$harness/route-contract.tsv"
}

unrouted_paths() {
  awk 'NF' "$harness/unrouted-paths.txt"
}

verdict() {
  local backend_path=$1 present=$2 absent=$3
  awk -v want_path="$backend_path" -v present="$present" -v absent="$absent" '
    BEGIN { np = split(present == "-" ? "" : present, p, ","); na = split(absent == "-" ? "" : absent, a, ",") }
    $1 ~ /^[A-Z]+$/ && $3 ~ /^HTTP\// && path == "" { path = $2 }
    { line = $0; sub(/\r$/, "", line); colon = index(line, ":"); if (colon > 1) seen[tolower(substr(line, 1, colon - 1))] = 1 }
    END {
      bad = 0
      if (path != want_path) { printf "backend saw path %s, want %s; ", (path == "" ? "(none)" : path), want_path; bad = 1 }
      for (i = 1; i <= np; i++) if (!(tolower(p[i]) in seen)) { printf "header %s missing; ", p[i]; bad = 1 }
      for (i = 1; i <= na; i++) if (tolower(a[i]) in seen) { printf "header %s reached the backend; ", a[i]; bad = 1 }
      exit bad
    }
  '
}

probe() {
  docker exec "$cluster" wget -qO- -T 5 \
    --header "Accept-Language: $ACCEPT_LANGUAGE" \
    --header "X-Deploy-Test-Marker: deploy-test" \
    "http://$gateway_ip:8000$1"
}

check_route() {
  local route=$1 request_path=$2 backend_path=$3 present=$4 absent=$5 i response problems=""
  for ((i = 1; i <= PROBE_ATTEMPTS; i++)); do
    if response=$(probe "$request_path" 2>&1); then
      problems=$(printf '%s\n' "$response" | verdict "$backend_path" "$present" "$absent") && return 0
    else
      problems="no response ($response)"
    fi
    sleep 1
  done
  echo "route $route (GET $request_path): $problems" >&2
  return 1
}

status_of() {
  { docker exec "$cluster" wget -S -O /dev/null -T 5 "http://$gateway_ip:8000$1" 2>&1 || true; } \
    | awk '$1 ~ /^HTTP\// { status = $2 } END { print status }'
}

check_unrouted() {
  local request_path=$1 i status
  for ((i = 1; i <= PROBE_ATTEMPTS; i++)); do
    status=$(status_of "$request_path")
    case $status in
      404) return 0 ;;
      200) echo "GET $request_path reached the backend (200), which no route may serve it to" >&2; return 1 ;;
    esac
    sleep 1
  done
  echo "GET $request_path: Traefik answered ${status:-nothing}, want 404" >&2
  return 1
}

behavior_region() {
  local region=$1 rendered=$work/$1.yaml traefik_image route rule request_path backend_path present absent
  traefik_image=$(doc_named DaemonSet traefik < "$rendered" | first_image)
  [ -n "$traefik_image" ] || fail behavior "$region renders no DaemonSet traefik with an image"
  load_image "$traefik_image"

  kube -n danteplanner delete httproute --all --ignore-not-found >/dev/null
  kube -n danteplanner delete deployment traefik --ignore-not-found --wait=true --timeout=60s >/dev/null
  {
    doc_named GatewayClass traefik < "$rendered"
    doc_named ServiceAccount traefik < "$rendered"
    doc_named ClusterRole traefik < "$rendered"
    doc_named ClusterRoleBinding traefik < "$rendered"
    doc_named Service backend < "$rendered"
    docs_of_kind HTTPRoute < "$rendered"
    printf -- '---\n'
    sed -e "s|TRAEFIK_IMAGE|$traefik_image|" -e "s|WHOAMI_IMAGE|${WHOAMI_IMAGE%@*}|" "$harness/fixture.yaml"
  } | docker exec -i "$cluster" kubectl -n danteplanner apply -f - >/dev/null
  kube -n danteplanner rollout status deployment/traefik --timeout=120s >/dev/null \
    || fail behavior "$region: Traefik ($traefik_image) did not become ready within 120s"
  kube -n danteplanner rollout status deployment/whoami --timeout=120s >/dev/null \
    || fail behavior "$region: the whoami backend did not become ready within 120s"
  kube -n danteplanner wait httproute --all --timeout=60s \
    --for=jsonpath='{.status.parents[0].conditions[?(@.type=="Accepted")].status}'=True >/dev/null \
    || fail behavior "$region: an HTTPRoute was not Accepted by the Gateway within 60s"
  gateway_ip=$(kube -n danteplanner get service traefik-web -o jsonpath='{.spec.clusterIP}')

  local known rendered_rules unknown stale unsupported unserved routed
  known=$(contract_rows | cut -f 1,2)
  backend_rules > "$work/$region.rules"
  rendered_rules=$(cut -f 1,2 "$work/$region.rules" | awk '!seen[$0]++')
  unknown=$(printf '%s\n' "$rendered_rules" | awk 'NR == FNR { k[$0] = 1; next } NF && !($0 in k)' <(printf '%s\n' "$known") -)
  [ -z "$unknown" ] || fail behavior "$region: $(printf '%s\n' "$unknown" | awk -F '\t' '{ printf "%sHTTPRoute %s rule %s", (NR > 1 ? ", " : ""), $1, $2 }') targets Service backend but has no row in .github/deploy-test/route-contract.tsv; add its contract there"
  stale=$(printf '%s\n' "$known" | awk 'NR == FNR { r[$0] = 1; next } NF && !($0 in r)' <(printf '%s\n' "$rendered_rules") -)
  [ -z "$stale" ] || fail behavior "$region renders no backend rule $(printf '%s\n' "$stale" | awk -F '\t' '{ printf "%s%s rule %s", (NR > 1 ? ", " : ""), $1, $2 }') that route-contract.tsv lists; remove its row or restore the rule"
  unsupported=$(unsupported_matches < "$work/$region.rules")
  [ -z "$unsupported" ] || fail behavior "$region: $unsupported, which the contract check cannot evaluate; only PathPrefix and Exact path matches without headers, queryParams or method are supported"
  unserved=$(contract_rows | unserved_rows "$work/$region.rules")
  [ -z "$unserved" ] || fail behavior "$region: route-contract.tsv row $unserved is not served by that rule; set request_path to a path under one of its matches"
  routed=$(unrouted_paths | routed_unrouted_rows "$work/$region.rules")
  [ -z "$routed" ] || fail behavior "$region: .github/deploy-test/unrouted-paths.txt lists $routed as unrouted; drop the match or move the path to a route-contract.tsv row"

  while IFS=$'\t' read -r route rule request_path backend_path present absent; do
    check_route "$route rule $rule" "$request_path" "$backend_path" "$present" "$absent" \
      || fail behavior "$region: route $route rule $rule broke its contract"
  done < <(contract_rows)
  while read -r request_path; do
    check_unrouted "$request_path" || fail behavior "$region: $request_path is served although .github/deploy-test/unrouted-paths.txt lists it as unrouted"
  done < <(unrouted_paths)
  echo "  $region: $(contract_rows | wc -l | tr -d ' ') routed rows and $(unrouted_paths | wc -l | tr -d ' ') unrouted paths hold through $traefik_image"
}

behavior() {
  start_cluster
  local region
  for region in "${regions[@]}"; do
    behavior_region "$region"
  done
  pass behavior "every backend rule in ${regions[*]} holds its contract"
}

alerts
render
charts
schema
behavior
echo "deploy-test: ok in $((SECONDS - started))s"

#!/usr/bin/env bash
set -euo pipefail

KUSTOMIZE_IMAGE=registry.k8s.io/kustomize/kustomize:v5.8.1@sha256:899fcd3bc898160e62bcaf82932b0cb29ba38d16272353db2e7acbba82129429
KUBECONFORM_IMAGE=ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e
K3S_IMAGE=rancher/k3s:v1.36.3-k3s1@sha256:d0f79175794edd9694b4a12bafc5c52ae1977369a2f7cf256264e7bd2dae0be9
WHOAMI_IMAGE=traefik/whoami:v1.10.2@sha256:1474027c316661cdec87df2623e13a41e7e1ce0ba99c24917631de8f300b5420
KUBERNETES_VERSION=1.36.3
KUBERNETES_SCHEMAS=https://raw.githubusercontent.com/yannh/kubernetes-json-schema/89a35a5e9a1412a692a2603d79c3786f602238a4
GATEWAY_API_CRDS=https://raw.githubusercontent.com/kubernetes-sigs/gateway-api/690f754646e8326128fd686e3e46117ac479cfdf/config/crd/standard
OVERLAYS=(oregon seoul)
ACCEPT_LANGUAGE='ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7,ja;q=0.6,zh-CN;q=0.5,zh;q=0.4,de;q=0.3,fr;q=0.2,es;q=0.1,it;q=0.05'
PROBE_ATTEMPTS=30

usage() {
  echo "usage: $0 [deploy-dir]   (default: the repository's deploy/; needs docker and bash)" >&2
  exit 2
}

[ $# -le 1 ] || usage
case "${1:-}" in -*) usage ;; esac

repo=$(cd "$(dirname "$0")/../.." && pwd)
harness=$repo/.github/deploy-test
deploy_dir=$(cd "${1:-$repo/deploy}" && pwd)
work=$(mktemp -d)
cluster=deploy-test-k3s-$$
started=$SECONDS

cleanup() {
  docker rm -f "$cluster" >/dev/null 2>&1 || true
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

render() {
  local overlay
  for overlay in "${OVERLAYS[@]}"; do
    if ! docker run --rm -v "$deploy_dir:/deploy:ro" "$KUSTOMIZE_IMAGE" build "/deploy/overlays/$overlay" \
      > "$work/$overlay.yaml" 2> "$work/$overlay.render.err"; then
      cat "$work/$overlay.render.err" >&2
      fail render "kustomize could not build overlays/$overlay"
    fi
  done
  pass render "$(for overlay in "${OVERLAYS[@]}"; do
    printf '%s: %s resources\n' "$overlay" "$(awk '/^kind:/ { n++ } END { print n + 0 }' "$work/$overlay.yaml")"
  done | paste -sd ',' - | sed 's/,/, /g')"
}

schema() {
  local out overlay files=()
  for overlay in "${OVERLAYS[@]}"; do files+=("/render/$overlay.yaml"); done
  if ! out=$(docker run --rm -v "$harness/schemas:/schemas:ro" -v "$work:/render:ro" "$KUBECONFORM_IMAGE" \
    -strict -summary -kubernetes-version "$KUBERNETES_VERSION" \
    -schema-location "$KUBERNETES_SCHEMAS/{{.NormalizedKubernetesVersion}}-standalone{{.StrictSuffix}}/{{.ResourceKind}}{{.KindSuffix}}.json" \
    -schema-location '/schemas/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' \
    "${files[@]}" 2>&1); then
    echo "$out" >&2
    fail schema "kubeconform rejected the render; a 'could not find schema' error means a new custom kind: add it to .github/deploy-test/vendor-schemas.py and rerun it"
  fi
  pass schema "$(printf '%s\n' "$out" | awk '/^Summary:/ { sub(/^Summary: /, ""); print }')"
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

unserved_rows() {
  awk -F '\t' '
    NR == FNR { k = $1 SUBSEP $2; n[k]++; type[k, n[k]] = $3; value[k, n[k]] = $4; next }
    {
      k = $1 SUBSEP $2; served = 0; listed = ""
      for (i = 1; i <= n[k]; i++) {
        v = value[k, i]; listed = listed (i > 1 ? ", " : "") type[k, i] " " v
        if (type[k, i] == "Exact" && $3 == v) served = 1
        if (type[k, i] == "PathPrefix") { sub(/\/+$/, "", v); if ($3 == v || index($3, v "/") == 1) served = 1 }
      }
      if (!served) printf "%s%s rule %s request_path %s (its matches: %s)", (bad++ ? ", " : ""), $1, $2, $3, listed
    }
  ' "$1" -
}

contract_rows() {
  awk -F '\t' 'NR > 1 && NF' "$harness/route-contract.tsv"
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

behavior_overlay() {
  local overlay=$1 rendered=$work/$1.yaml traefik_image route rule request_path backend_path present absent
  traefik_image=$(doc_named DaemonSet traefik < "$rendered" | first_image)
  [ -n "$traefik_image" ] || fail behavior "overlays/$overlay renders no DaemonSet traefik with an image"
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
    || fail behavior "overlays/$overlay: Traefik ($traefik_image) did not become ready within 120s"
  kube -n danteplanner rollout status deployment/whoami --timeout=120s >/dev/null \
    || fail behavior "overlays/$overlay: the whoami backend did not become ready within 120s"
  kube -n danteplanner wait httproute --all --timeout=60s \
    --for=jsonpath='{.status.parents[0].conditions[?(@.type=="Accepted")].status}'=True >/dev/null \
    || fail behavior "overlays/$overlay: an HTTPRoute was not Accepted by the Gateway within 60s"
  gateway_ip=$(kube -n danteplanner get service traefik-web -o jsonpath='{.spec.clusterIP}')

  local known rendered_rules unknown stale unsupported unserved
  known=$(contract_rows | cut -f 1,2)
  backend_rules > "$work/$overlay.rules"
  rendered_rules=$(cut -f 1,2 "$work/$overlay.rules" | awk '!seen[$0]++')
  unknown=$(printf '%s\n' "$rendered_rules" | awk 'NR == FNR { k[$0] = 1; next } NF && !($0 in k)' <(printf '%s\n' "$known") -)
  [ -z "$unknown" ] || fail behavior "overlays/$overlay: $(printf '%s\n' "$unknown" | awk -F '\t' '{ printf "%sHTTPRoute %s rule %s", (NR > 1 ? ", " : ""), $1, $2 }') targets Service backend but has no row in .github/deploy-test/route-contract.tsv; add its contract there"
  stale=$(printf '%s\n' "$known" | awk 'NR == FNR { r[$0] = 1; next } NF && !($0 in r)' <(printf '%s\n' "$rendered_rules") -)
  [ -z "$stale" ] || fail behavior "overlays/$overlay renders no backend rule $(printf '%s\n' "$stale" | awk -F '\t' '{ printf "%s%s rule %s", (NR > 1 ? ", " : ""), $1, $2 }') that route-contract.tsv lists; remove its row or restore the rule"
  unsupported=$(unsupported_matches < "$work/$overlay.rules")
  [ -z "$unsupported" ] || fail behavior "overlays/$overlay: $unsupported, which the contract check cannot evaluate; only PathPrefix and Exact path matches without headers, queryParams or method are supported"
  unserved=$(contract_rows | unserved_rows "$work/$overlay.rules")
  [ -z "$unserved" ] || fail behavior "overlays/$overlay: route-contract.tsv row $unserved is not served by that rule; set request_path to a path under one of its matches"

  while IFS=$'\t' read -r route rule request_path backend_path present absent; do
    check_route "$route rule $rule" "$request_path" "$backend_path" "$present" "$absent" \
      || fail behavior "overlays/$overlay: route $route rule $rule broke its contract"
  done < <(contract_rows)
  echo "  $overlay: $(contract_rows | wc -l | tr -d ' ') backend rules hold their contract through $traefik_image"
}

behavior() {
  start_cluster
  local overlay
  for overlay in "${OVERLAYS[@]}"; do
    behavior_overlay "$overlay"
  done
  pass behavior "every backend rule in ${OVERLAYS[*]} holds its contract"
}

render
schema
behavior
echo "deploy-test: ok in $((SECONDS - started))s"

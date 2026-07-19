#!/bin/bash
# Pull Cloudflare Web Analytics (RUM) Core Web Vitals out of the GraphQL
# Analytics API and print a per-page p75 table.
#
# CWV come from the rumWebVitalsEventsAdaptiveGroups dataset (LCP/INP/CLS/FCP);
# page-load + TTFB from rumPerformanceEventsAdaptiveGroups. Both are joined by
# requestPath. Timing quantiles are microseconds — the script converts them.
#
# Reads three secrets from an .env file (repo root by default; --env to override):
#   CF_API_TOKEN   API token with "Account Analytics: Read"  (mint guide below)
#   CF_ACCOUNT_ID  Cloudflare account id  (the account tag, NOT the zone id)
#   CF_SITE_TAG    the API siteTag — run `rum-cwv.sh --sites` to discover it.
#
# ⚠ The API siteTag is NOT the public data-cf-beacon token in the page HTML —
#   they are different values. Use `--sites` (or --site TAG) to get the real one.
#
# ── How to mint the token ────────────────────────────────────────────────────
# 1. dash.cloudflare.com → My Profile → API Tokens → Create Token → Custom token.
# 2. Permissions:  Account · Account Analytics · Read.   (RUM is account-scoped.)
# 3. Account Resources:  Include · <your account>.
# 4. Create, copy the token (shown once), put it in .env as CF_API_TOKEN.
# CF_ACCOUNT_ID: dashboard right-sidebar "Account ID", or the dash URL segment.
# CF_SITE_TAG:   run `rum-cwv.sh --sites` and copy the tag with the most samples.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

ENV_FILE="$REPO_ROOT/.env"
DAYS=7
INTROSPECT=0
BY_DEVICE=0
SITES_MODE=0
SITE_OVERRIDE=""
DEBUG_PATH=""
DEBUG_METRIC="cls"
ENDPOINT="https://api.cloudflare.com/client/v4/graphql"

usage() {
  sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  echo
  echo "Usage: rum-cwv.sh [--env PATH] [--days N] [--site TAG] [--by-device] [--sites]"
  echo "                   [--debug PATH [--metric cls|lcp|inp]] [--introspect]"
  echo "  --sites          list siteTags that have RUM traffic (the API siteTag is NOT the beacon token)"
  echo "  --site TAG       override CF_SITE_TAG for this run"
  echo "  --debug PATH     for one requestPath, show WHICH element drives the metric (default cls)"
  echo "  --metric M       which element to debug: cls (default), lcp, or inp"
  exit "${1:-0}"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --env) ENV_FILE="$2"; shift 2 ;;
    --days) DAYS="$2"; shift 2 ;;
    --site) SITE_OVERRIDE="$2"; shift 2 ;;
    --debug) DEBUG_PATH="$2"; shift 2 ;;
    --metric) DEBUG_METRIC="$2"; shift 2 ;;
    --by-device) BY_DEVICE=1; shift ;;
    --sites) SITES_MODE=1; shift ;;
    --introspect) INTROSPECT=1; shift ;;
    -h|--help) usage 0 ;;
    *) echo "Unknown arg: $1" >&2; usage 1 ;;
  esac
done

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

# Parse only the known CF_ keys from .env — do NOT source it (avoids executing
# arbitrary file contents). Strips surrounding single/double quotes.
if [ ! -f "$ENV_FILE" ]; then
  echo "env file not found: $ENV_FILE (use --env PATH)" >&2
  exit 1
fi
while IFS='=' read -r key val; do
  val="${val%\"}"; val="${val#\"}"; val="${val%\'}"; val="${val#\'}"
  export "$key=$val"
done < <(grep -E '^[[:space:]]*(CF_API_TOKEN|CF_ACCOUNT_ID|CF_SITE_TAG)=' "$ENV_FILE" || true)

# --site overrides the .env value for this run.
[ -n "$SITE_OVERRIDE" ] && CF_SITE_TAG="$SITE_OVERRIDE"

: "${CF_API_TOKEN:?set CF_API_TOKEN in $ENV_FILE}"

# POST a GraphQL query; fail loudly on API-level errors (200 with .errors set).
gql() {
  local resp
  resp="$(curl -fsS "$ENDPOINT" \
    -H "Authorization: Bearer $CF_API_TOKEN" \
    -H "Content-Type: application/json" \
    --data "$1")" || { echo "HTTP request to Cloudflare failed" >&2; exit 1; }
  if echo "$resp" | jq -e '.errors and (.errors | length > 0)' >/dev/null; then
    echo "Cloudflare GraphQL returned errors:" >&2
    echo "$resp" | jq '.errors' >&2
    exit 1
  fi
  echo "$resp"
}

# Dump the fields of a named type, one per line: "<fieldName>\t<concreteTypeName>\t<kind>".
# Unwraps NON_NULL/LIST wrappers to reach the concrete named type.
type_fields() {
  gql "$(jq -n --arg t "$1" '{query:"query($t:String!){__type(name:$t){fields{name type{name kind ofType{name kind ofType{name kind ofType{name kind}}}}}}}",variables:{t:$t}}')" \
    | jq -r '(.data.__type.fields // [])[]
        | .name as $n
        | ([.type, .type.ofType, .type.ofType.ofType, .type.ofType.ofType.ofType]
            | map(select(.!=null and .name!=null)) | .[0]) as $ct
        | "\($n)\t\($ct.name // "?")\t\($ct.kind // "?")"'
}

if [ "$INTROSPECT" -eq 1 ]; then
  echo "Discovering the RUM schema (datasets + where the CWV metrics live)…"
  echo
  echo "▚ RUM datasets available on the Account type:"
  type_fields "Account" | grep -iE '^rum' | sed 's/^/    /' || echo "    (none — token may lack Account Analytics Read)"
  echo
  all_types="$(gql '{"query":"{ __schema { types { name } } }"}' | jq -r '.data.__schema.types[].name')"
  # Dump every Account RUM group type + its object sub-fields (UNFILTERED).
  for PT in $(echo "$all_types" | grep -iE '^AccountRum.*AdaptiveGroups$'); do
    echo "════ $PT ════"
    fields="$(type_fields "$PT")"
    echo "$fields" | sed 's/^/    /'
    echo "$fields" | while IFS=$'\t' read -r fname ftype fkind; do
      case "$fkind" in
        OBJECT|INTERFACE)
          echo "    ▚ $fname → $ftype"
          type_fields "$ftype" | sed 's/^/        /'
          ;;
      esac
    done
    echo
  done
  echo "→ Tell me which dataset + field path holds LCP/FCP/INP/CLS and I'll wire the query."
  exit 0
fi

: "${CF_ACCOUNT_ID:?set CF_ACCOUNT_ID in $ENV_FILE}"

SINCE="$(date -u -d "${DAYS} days ago" +%Y-%m-%dT%H:%M:%SZ)"
UNTIL="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

# --sites: discover which siteTag(s) actually carry RUM traffic. The GraphQL
# siteTag is an internal analytics id, DISTINCT from the public data-cf-beacon
# token — so grouping the data by siteTag is the reliable way to find it.
if [ "$SITES_MODE" -eq 1 ]; then
  echo "siteTags with RUM traffic in the last ${DAYS}d (use one as CF_SITE_TAG):"
  body="$(jq -n --arg a "$CF_ACCOUNT_ID" --arg since "$SINCE" --arg until "$UNTIL" \
    '{query:"query($a:String!,$since:Time!,$until:Time!){viewer{accounts(filter:{accountTag:$a}){rumPageloadEventsAdaptiveGroups(filter:{datetime_geq:$since,datetime_leq:$until},limit:100,orderBy:[count_DESC]){count dimensions{siteTag}}}}}",variables:{a:$a,since:$since,until:$until}}')"
  gql "$body" | jq -r '
    (.data.viewer.accounts[0].rumPageloadEventsAdaptiveGroups // [])
    | group_by(.dimensions.siteTag)
    | map({siteTag: .[0].dimensions.siteTag, samples: (map(.count) | add)})
    | sort_by(-.samples)[] | "  \(.siteTag)\t\(.samples) samples"'
  exit 0
fi

: "${CF_SITE_TAG:?set CF_SITE_TAG in $ENV_FILE (or pass --site TAG; run --sites to discover)}"

# --debug PATH: for one requestPath, group by the culprit element dimension so
# you can see WHICH DOM element drives the poor metric (e.g. what is shifting for CLS).
if [ -n "$DEBUG_PATH" ]; then
  case "$DEBUG_METRIC" in
    cls) PREFIX="cumulativeLayoutShift"; UNIT="cls" ;;
    lcp) PREFIX="largestContentfulPaint"; UNIT="us" ;;
    inp) PREFIX="interactionToNextPaint"; UNIT="us" ;;
    *) echo "--metric must be cls|lcp|inp" >&2; exit 1 ;;
  esac
  echo "Element debug — ${DEBUG_METRIC} culprits on ${DEBUG_PATH}, last ${DAYS}d (p75, by share of events):"
  body="$(jq -n --arg a "$CF_ACCOUNT_ID" --arg s "$CF_SITE_TAG" --arg p "$DEBUG_PATH" \
    --arg since "$SINCE" --arg until "$UNTIL" --arg el "${PREFIX}Element" --arg pa "${PREFIX}Path" --arg q "${PREFIX}P75" \
    '{query:("query($a:String!,$s:String!,$p:String!,$since:Time!,$until:Time!){viewer{accounts(filter:{accountTag:$a}){rumWebVitalsEventsAdaptiveGroups(filter:{siteTag:$s,requestPath:$p,datetime_geq:$since,datetime_leq:$until},limit:20,orderBy:[count_DESC]){count dimensions{"+$el+" "+$pa+"} quantiles{"+$q+"}}}}}"),
      variables:{a:$a,s:$s,p:$p,since:$since,until:$until}}')"
  {
    printf 'element\tdom-path\tp75\tsamples\n'
    gql "$body" | jq -r --arg el "${PREFIX}Element" --arg pa "${PREFIX}Path" --arg q "${PREFIX}P75" --arg unit "$UNIT" '
      def fmt($v): if $v==null then "-" elif $unit=="cls" then (($v*1000|round)/1000|tostring) else (($v/1000*10|round)/10|tostring)+"ms" end;
      (.data.viewer.accounts[0].rumWebVitalsEventsAdaptiveGroups // [])[]
      | [ (.dimensions[$el] // "(none)"), (.dimensions[$pa] // "-"), fmt(.quantiles[$q]), (.count|tostring) ] | @tsv'
  } | column -t -s "$(printf '\t')"
  exit 0
fi

DIM='requestPath'
[ "$BY_DEVICE" -eq 1 ] && DIM='requestPath deviceType'

# Run one dataset query and return its rows array (JSON). $1 dataset, $2 quantile fields.
query_dataset() {
  local ds="$1" fields="$2" q body
  q="query(\$a:String!,\$s:String!,\$since:Time!,\$until:Time!){
    viewer{ accounts(filter:{accountTag:\$a}){
      ${ds}(filter:{siteTag:\$s, datetime_geq:\$since, datetime_leq:\$until}, limit:200, orderBy:[count_DESC]){
        count avg{ sampleInterval } dimensions{ ${DIM} } quantiles{ ${fields} }
      }
    }}
  }"
  body="$(jq -n --arg q "$q" --arg a "$CF_ACCOUNT_ID" --arg s "$CF_SITE_TAG" --arg since "$SINCE" --arg until "$UNTIL" \
    '{query:$q, variables:{a:$a, s:$s, since:$since, until:$until}}')"
  gql "$body" | jq -c ".data.viewer.accounts[0].${ds} // []"
}

# CWV come from the Web Vitals dataset; page-load/TTFB from the Performance
# dataset. Field names verified via --introspect (LCP percentiles only exist on
# Web Vitals; Performance has pageLoadTime/responseTime).
VITALS="$(query_dataset rumWebVitalsEventsAdaptiveGroups \
  'largestContentfulPaintP75 interactionToNextPaintP75 cumulativeLayoutShiftP75 firstContentfulPaintP75')"
PERF="$(query_dataset rumPerformanceEventsAdaptiveGroups 'pageLoadTimeP75 responseTimeP75')"

if [ "$(echo "$VITALS" | jq 'length')" -eq 0 ]; then
  echo "No RUM rows returned for the last ${DAYS}d." >&2
  echo "Checklist: CF_ACCOUNT_ID is the ACCOUNT id (not zone); CF_SITE_TAG is the" >&2
  echo "Web Analytics beacon token; the site has had real traffic in the window." >&2
  exit 0
fi

echo "Cloudflare RUM — Core Web Vitals p75, last ${DAYS}d"
echo "  thresholds: LCP <=2.5s good / >4s poor · INP <=200ms / >500ms · CLS <=0.1 / >0.25 · FCP <=1.8s / >3s"
{
  printf 'path\tdevice\tLCP\tINP\tCLS\tFCP\tTTFB\tLoad\tsamples\n'
  jq -rn --argjson vitals "$VITALS" --argjson perf "$PERF" --argjson byDevice "$BY_DEVICE" '
    # RUM timing quantiles are in MICROSECONDS. Convert before formatting/marking.
    def toms($us): if $us == null then null else $us/1000 end;
    def secfmt($us): if $us == null then "-" else (($us/1000000*100|round)/100|tostring)+"s" end;
    def msfmt($us): if $us == null then "-" else (($us/1000*10|round)/10|tostring)+"ms" end;
    def cls($v): if $v == null then "-" else (($v*1000|round)/1000|tostring) end;
    def m($v; $good; $poor): if $v == null then "" elif $v <= $good then "🟢" elif $v > $poor then "🔴" else "🟡" end;
    def markms($us; $good; $poor): m(toms($us); $good; $poor);
    def key($r): $r.dimensions.requestPath + (if $byDevice==1 then "|" + ($r.dimensions.deviceType // "-") else "" end);
    ($perf | map({ (key(.)): .quantiles }) | add // {}) as $pmap |
    $vitals | sort_by(-.count) | .[] | .quantiles as $q | ($pmap[key(.)] // {}) as $p | [
      .dimensions.requestPath,
      (if $byDevice==1 then (.dimensions.deviceType // "-") else "-" end),
      (secfmt($q.largestContentfulPaintP75) + markms($q.largestContentfulPaintP75; 2500; 4000)),
      (msfmt($q.interactionToNextPaintP75) + markms($q.interactionToNextPaintP75; 200; 500)),
      (cls($q.cumulativeLayoutShiftP75) + m($q.cumulativeLayoutShiftP75; 0.1; 0.25)),
      (secfmt($q.firstContentfulPaintP75) + markms($q.firstContentfulPaintP75; 1800; 3000)),
      msfmt($p.responseTimeP75),
      secfmt($p.pageLoadTimeP75),
      (.count|tostring)
    ] | @tsv'
} | column -t -s "$(printf '\t')"

#!/usr/bin/env bash
# Usage: AWS_PROFILE=prod scripts/ops/provision/gitops-chart-switch.sh --site oregon|seoul [--dry-run|--rollback]
#        CP_INSTANCE_ID=i-0abc ... skips the terraform output lookup.
# Moves the root Application of one site from deploy/overlays/<site> to deploy/chart. Automated sync
# goes off before the source changes, and comes back on only when nothing outside the expected objects differs.
set -euo pipefail

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/lib/constants.sh"

COMPARE_BOUND_SECONDS=180
SYNC_BOUND_SECONDS=300
POLL_SECONDS=5
SSM_TIMEOUT_SECONDS=1200
LOCAL_WAIT_SECONDS=1260

usage() {
    echo "usage: gitops-chart-switch.sh --site oregon|seoul [--dry-run|--rollback]" >&2
    exit 2
}

site_region() {
    case "$1" in
        oregon) echo us-west-2 ;;
        seoul) echo ap-northeast-2 ;;
        *) return 1 ;;
    esac
}

remote_script() {
    printf 'MODE=%s\nSITE=%s\nCOMPARE_BOUND=%s\nSYNC_BOUND=%s\nPOLL=%s\n' "$1" "$2" "$3" "$4" "$5"
    cat <<'REMOTE'
APP=danteplanner-$SITE
STATE=checked
repository=""

kc() { k3s kubectl -n argocd --request-timeout=10s "$@"; }
field() { kc get application "$APP" -o "jsonpath=$1"; }
merge() { kc patch application "$APP" --type merge -p "$1" >/dev/null; }
end() { STATE=finished; printf '%s\n' "$2"; exit "$1"; }
refresh() { kc annotate application "$APP" argocd.argoproj.io/refresh=hard --overwrite >/dev/null; }
AUTOMATED='{"prune":true,"selfHeal":true}'
sync_off() { merge '{"spec":{"syncPolicy":{"automated":null}}}'; }
sync_on() { merge "{\"spec\":{\"syncPolicy\":{\"automated\":$AUTOMATED}}}"; }
scrub() { sed -E 's/[0-9]{12}/<account>/g'; }
chart_source() {
  merge "{\"spec\":{\"source\":{\"path\":\"deploy/chart\",\"kustomize\":null,\"helm\":{\"valueFiles\":[\"values-$SITE.yaml\"],\"parameters\":[{\"name\":\"image.repository\",\"value\":\"$repository\"}]}}}}"
}
restore() {
  tries=0
  while [ "$tries" -lt 3 ]; do
    merge "{\"spec\":{\"syncPolicy\":{\"automated\":$AUTOMATED},\"source\":{\"path\":\"deploy/overlays/$SITE\",\"helm\":null,\"kustomize\":{\"images\":[\"danteplanner-backend=$repository:*\"]}}}}" && return 0
    tries=$((tries + 1))
    sleep "$POLL"
  done
  return 1
}

on_exit() {
  code=$1
  case "$STATE" in
    patched)
      printf '%s\n' "ended early with code $code"
      STATE=finished
      if restore; then
        printf '%s\n' "ROLLED BACK"
        exit 4
      fi
      printf '%s\n' "FAILED: the rollback did not go through, and automated sync may be off; run this script with --rollback"
      exit 5 ;;
    syncing)
      STATE=finished
      printf '%s\n' "FAILED: ended while the chart was syncing; read it with ops verify fleet, go back with --rollback"
      exit 5 ;;
  esac
}
trap 'on_exit $?' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

idle() {
  operation=$(field 'op={.operation}{.status.operationState.phase}') || return 1
  case "$operation" in *Running* | op=*sync* | op=*\{*) return 1 ;; op=*) return 0 ;; esac
  return 1
}
wait_idle() {
  deadline=$(($(date +%s) + COMPARE_BOUND))
  quiet=0
  while :; do
    if idle; then quiet=$((quiet + 1)); else quiet=0; fi
    [ "$quiet" -lt 2 ] || return 0
    [ "$(date +%s)" -lt "$deadline" ] || return 1
    sleep "$POLL"
  done
}

wait_compared() {
  deadline=$(($(date +%s) + COMPARE_BOUND))
  while :; do
    compared=$(field '{.status.sync.comparedTo.source.path}' 2>/dev/null || true)
    pending=$(field '{.metadata.annotations.argocd\.argoproj\.io/refresh}' 2>/dev/null || true)
    [ "$compared" = "$1" ] && [ -z "$pending" ] && return 0
    [ "$(date +%s)" -lt "$deadline" ] || return 1
    sleep "$POLL"
  done
}

wait_settled() {
  deadline=$(($(date +%s) + SYNC_BOUND))
  while :; do
    settled="$(field '{.status.sync.status} {.status.health.status} {.status.operationState.phase}' 2>/dev/null || true)"
    case "$settled" in "Synced Healthy Running") ;; "Synced Healthy"*) return 0 ;; esac
    [ "$(date +%s)" -lt "$deadline" ] || return 1
    sleep "$POLL"
  done
}

kustomize_repository() {
  repository=${kustomize_image#danteplanner-backend=}
  repository=${repository%:\*}
  case "$kustomize_image" in danteplanner-backend=?*:\*) ;; *) repository="" ;; esac
  case "$repository" in "" | *[!a-z0-9./-]*) end 3 "STOPPED: the kustomize image override is not danteplanner-backend=<registry>:*; nothing was changed" ;; esac
}
differing() {
  field '{range .status.resources[*]}{.status}{" "}{.kind}{" "}{.name}{"\n"}{end}' | awk 'NF == 3 && $1 != "Synced" { print $2 " " $3 }'
}
unexpected() {
  grep -Ev '^(ConfigMap (mysqld-tls-cnf|rds-ca-bundle)(-[a-z0-9]{10})?|Deployment mysqld-exporter|GatewayClass traefik)$' || true
}
expected_missing() {
  for want in "ConfigMap mysqld-tls-cnf" "ConfigMap rds-ca-bundle" "Deployment mysqld-exporter"; do
    printf '%s\n' "$1" | grep -qx "$want" || printf '%s\n' "$want"
  done
}

path=$(field '{.spec.source.path}') || end 3 "STOPPED: no Application $APP is readable on this cluster; nothing was changed"
kustomize_image=$(field '{.spec.source.kustomize.images[0]}')
helm_repository=$(field '{.spec.source.helm.parameters[?(@.name=="image.repository")].value}')
printf '%s\n' "source path: $path"
printf '%s\n' "status: $(field '{.status.sync.status} {.status.health.status}')"
printf '%s\n' "resources with a status: $(field '{range .status.resources[*]}{.status}{"\n"}{end}' | grep -c . || true)"

if [ "$MODE" = rollback ]; then
  case "$path" in
    deploy/chart) repository=$helm_repository ;;
    "deploy/overlays/$SITE")
      automated=$(field '{.spec.syncPolicy.automated}') || end 3 "STOPPED: the sync policy is not readable; nothing was changed"
      [ -z "$automated" ] || end 0 "NOTHING TO ROLL BACK: the source is $path and automated sync is on"
      kustomize_repository ;;
    *) end 3 "STOPPED: the source path is $path; nothing was changed" ;;
  esac
  [ -n "$repository" ] || end 3 "STOPPED: no image registry is readable; nothing was changed"
  restore || end 5 "FAILED: the rollback patch did not go through; run it again"
  refresh || true
  if wait_compared "deploy/overlays/$SITE" && wait_settled; then end 0 "ROLLBACK DONE"; fi
  end 5 "FAILED: the source is back and automated sync is on, but the Application is not Synced and Healthy"
fi

if [ "$path" = deploy/chart ]; then
  automated=$(field '{.spec.syncPolicy.automated}') || end 3 "STOPPED: the sync policy is not readable; nothing was changed"
  [ -n "$automated" ] || end 5 "FAILED: the source is deploy/chart and automated sync is off, so an earlier run did not finish; run this script with --rollback"
  end 0 "NOTHING CHANGED: the source is deploy/chart already and automated sync is on"
fi
[ "$path" = "deploy/overlays/$SITE" ] || end 3 "STOPPED: the source path is $path; nothing was changed"
kustomize_repository
[ "$(field '{.status.sync.status} {.status.health.status}')" = "Synced Healthy" ] || end 3 "STOPPED: the Application is not Synced and Healthy; nothing was changed"
idle || end 3 "STOPPED: a sync is running; nothing was changed"
[ -n "$(field '{.spec.syncPolicy.automated}')" ] || end 3 "STOPPED: automated sync is off already; nothing was changed"
[ "$(field '{.status.resources[0].status}')" != "" ] || end 3 "STOPPED: the Application lists no status per resource, so the comparison cannot be read; nothing was changed"
[ "$MODE" != dry-run ] || end 0 "DRY RUN: ready to switch"

STATE=patched
sync_off || exit 1
wait_idle || { printf '%s\n' "a sync was still running ${COMPARE_BOUND}s after automated sync went off"; exit 1; }
chart_source || exit 1
refresh || exit 1
if ! wait_compared deploy/chart; then
  printf '%s\n' "no comparison with deploy/chart within ${COMPARE_BOUND}s: $(field '{range .status.conditions[*]}{.type}: {.message}{"\n"}{end}' | scrub)"
  exit 1
fi
errors=$(field '{range .status.conditions[*]}{.type}: {.message}{"\n"}{end}') || exit 1
case "$errors" in *Error*) printf '%s\n' "$errors" | scrub; exit 1 ;; esac
[ "$(field '{.status.sync.status}')" = OutOfSync ] || { printf '%s\n' "the comparison ended in $(field '{.status.sync.status}'), and OutOfSync was expected"; exit 1; }
differ=$(differing) || exit 1
printf '%s\n' "differs from the chart:" "$differ"
strange=$(printf '%s\n' "$differ" | grep . | unexpected)
[ -z "$strange" ] || { printf '%s\n' "UNEXPECTED:" "$strange"; exit 1; }
absent=$(expected_missing "$differ")
[ -z "$absent" ] || { printf '%s\n' "EXPECTED AND ABSENT:" "$absent"; exit 1; }

sync_on || exit 1
STATE=syncing
wait_settled || end 5 "FAILED: not Synced and Healthy within ${SYNC_BOUND}s on deploy/chart; read it with ops verify fleet, go back with --rollback"
end 0 "SWITCHED"
REMOTE
}

run_on_cp() {
    local region="$1" cp_id="$2" script="$3" params cmd_id status waited=0 remote_out
    params=$(jq -n --arg s "$script" --arg t "$SSM_TIMEOUT_SECONDS" \
        '{commands: ($s | split("\n")), executionTimeout: [$t]}')
    cmd_id=$(aws ssm send-command --region "$region" --instance-ids "$cp_id" \
        --document-name AWS-RunShellScript --parameters "$params" \
        --query 'Command.CommandId' --output text)
    while :; do
        status=$(aws ssm get-command-invocation --region "$region" --command-id "$cmd_id" --instance-id "$cp_id" \
            --query Status --output text 2>/dev/null || echo Pending)
        case "$status" in
            Pending | InProgress | Delayed) ;;
            *) break ;;
        esac
        if [[ "$waited" -ge "$LOCAL_WAIT_SECONDS" ]]; then
            echo "the remote run did not end within ${LOCAL_WAIT_SECONDS}s and may still be going; do not rerun until command $cmd_id has ended" >&2
            return 6
        fi
        sleep "$POLL_SECONDS"
        waited=$((waited + POLL_SECONDS))
    done
    remote_out=$(aws ssm get-command-invocation --region "$region" --command-id "$cmd_id" --instance-id "$cp_id" --output json)
    jq -r '.StandardOutputContent, .StandardErrorContent' <<<"$remote_out" | sed -E 's/[0-9]{12}/<account>/g'
    [[ "$status" == Success ]]
}

main() {
    local site="" mode=switch region cp_id
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --site)
                [[ $# -ge 2 ]] || usage
                site="$2"
                shift
                ;;
            --dry-run) mode=dry-run ;;
            --rollback) mode=rollback ;;
            *) usage ;;
        esac
        shift
    done
    region=$(site_region "$site") || usage
    require_aws_session
    cp_id="${CP_INSTANCE_ID:-}"
    if [[ -z "$cp_id" ]]; then
        cp_id=$("$OPS_DIR/terraform-run.sh" -chdir="$OPS_DIR/../../terraform/$site" output -raw cp_instance_id)
    fi
    echo "== $mode of the $site root Application ($region)"
    run_on_cp "$region" "$cp_id" "$(remote_script "$mode" "$site" "$COMPARE_BOUND_SECONDS" "$SYNC_BOUND_SECONDS" "$POLL_SECONDS")"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi

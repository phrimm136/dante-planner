#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$SCRIPT_DIR/lib"
# shellcheck source=lib/common.sh
source "$LIB_DIR/common.sh"
# shellcheck source=lib/control-plane.sh
source "$LIB_DIR/control-plane.sh"
# shellcheck source=lib/traps.sh
source "$LIB_DIR/traps.sh"
# shellcheck source=lib/constants.sh
source "$LIB_DIR/constants.sh"

install_err_trap

TF_DIR="${TF_DIR:-$SCRIPT_DIR/../../terraform/oregon}"

NAMESPACE="danteplanner"
RUNTIME_SECRET="backend-runtime-config"
PASSWORD_KEY="AUTH_REDIS_PASSWORD"
REDIS_POD="redis-auth-0"
REDIS_CONTAINER="redis"
REDIS_ENV="REDIS_AUTH_PASSWORD"
BACKEND_SELECTOR="app=backend"
BACKEND_CONTAINER="backend"
EXPORTER_SELECTOR="app=redis-exporter-auth"
READY_TIMEOUT="300s"
COMMAND_BOUND_SECONDS=3900
POD_READ=".claude/skills/ops/bin/ops verify fleet --site oregon"

usage() {
    cat <<EOF
Restart Oregon's auth Redis so it picks up $PASSWORD_KEY, then its exporter.

Usage:
  scripts/ops/redis-auth-restart.sh [--dry-run]    pre-checks only (default)
  scripts/ops/redis-auth-restart.sh --apply        pre-checks, then restart
  scripts/ops/redis-auth-restart.sh --exporter-only
                                                   check $REDIS_POD requires AUTH, then restart only its exporter
  CP_INSTANCE_ID=i-0abc scripts/ops/redis-auth-restart.sh ...

Runs k3s kubectl on the Oregon CP via SSM send-command. Prints no secret values.
Needs ssm:SendCommand + ssm:GetCommandInvocation on the CP. jq required.
EOF
}

remote_prelude() {
    printf 'NS=%q\nSECRET=%q\nKEY=%q\nREDIS_POD=%q\nREDIS_CONTAINER=%q\nREDIS_ENV=%q\nBACKEND_SELECTOR=%q\nBACKEND_CONTAINER=%q\nEXPORTER_SELECTOR=%q\nREADY_TIMEOUT=%q\n' \
        "$NAMESPACE" "$RUNTIME_SECRET" "$PASSWORD_KEY" "$REDIS_POD" "$REDIS_CONTAINER" \
        "$REDIS_ENV" "$BACKEND_SELECTOR" "$BACKEND_CONTAINER" "$EXPORTER_SELECTOR" "$READY_TIMEOUT"
}

run_on_cp() {
    run_on_control_plane "$AWS_REGION" "$1" "$(remote_prelude)"$'\n'"$2" "$COMMAND_BOUND_SECONDS"
}

step_ending() {
    cat "$CONTROL_PLANE_ENDING_FILE"
}

next_action() {
    case "$1 $2" in
        "pre-check "* | "AUTH check "*)
            log_info "the $1 only reads; a rerun is safe" ;;
        "Redis restart NOT_SENT")
            log_info "nothing ran; a rerun is safe" ;;
        "Redis restart "*)
            log_warn "$REDIS_POD may already be restarted, and the exporter is not; read the pods' AGE first: $POD_READ"
            log_warn "a rerun of --apply restarts auth Redis again" ;;
        "exporter restart NOT_SENT")
            log_warn "nothing ran; $REDIS_POD already requires AUTH, and the $EXPORTER_SELECTOR pods are not restarted"
            log_info "rerun with --exporter-only to restart only the exporter" ;;
        "exporter restart "*)
            log_warn "the $EXPORTER_SELECTOR pods may already be restarted; read their AGE first: $POD_READ"
            log_info "rerun with --exporter-only to restart only the exporter" ;;
    esac
}

stop_after() {
    local ending
    ending=$(step_ending)
    if [[ -n "$2" ]]; then
        printf '%s\n' "$2"
    fi
    log_error "the $1 command ended $ending on the control plane; stopping" >&2
    next_action "$1" "$ending" >&2
    exit 1
}

PRECHECK=$(cat <<'REMOTE'
echo "=== SECRET ==="
v=$(k3s kubectl -n "$NS" get secret "$SECRET" -o jsonpath="{.data.$KEY}" 2>/dev/null || true)
if [ -n "$v" ]; then echo key-present; else echo key-absent; fi
unset v
echo "=== REDIS ==="
k3s kubectl -n "$NS" exec "$REDIS_POD" -c "$REDIS_CONTAINER" -- \
  sh -c "if [ -n \"\$$REDIS_ENV\" ]; then echo env-set; else echo env-empty; fi" 2>/dev/null || echo exec-failed
echo "=== BACKEND ==="
for p in $(k3s kubectl -n "$NS" get pods -l "$BACKEND_SELECTOR" -o jsonpath='{.items[*].metadata.name}'); do
  s=$(k3s kubectl -n "$NS" exec "$p" -c "$BACKEND_CONTAINER" -- \
    sh -c "if [ -n \"\$$KEY\" ]; then echo set; else echo MISSING; fi" 2>/dev/null) || s=MISSING
  echo "$p $s"
done
REMOTE
)

AUTH_CHECK=$(cat <<'REMOTE'
if k3s kubectl -n "$NS" exec "$REDIS_POD" -c "$REDIS_CONTAINER" -- redis-cli ping 2>&1 | grep -q NOAUTH; then
  echo AUTH_REQUIRED
else
  echo AUTH_NOT_REQUIRED
fi
REMOTE
)

RESTART_REDIS=$(cat <<'REMOTE'
old=$(k3s kubectl -n "$NS" get pod "$REDIS_POD" -o jsonpath='{.metadata.uid}')
k3s kubectl -n "$NS" delete pod "$REDIS_POD" --wait=true
for _ in $(seq 60); do
  new=$(k3s kubectl -n "$NS" get pod "$REDIS_POD" -o jsonpath='{.metadata.uid}' 2>/dev/null || true)
  if [ -n "$new" ] && [ "$new" != "$old" ]; then break; fi
  sleep 2
done
if k3s kubectl -n "$NS" wait --for=condition=Ready pod/"$REDIS_POD" --timeout="$READY_TIMEOUT"; then
  echo REDIS_READY
else
  echo REDIS_NOT_READY
  k3s kubectl -n "$NS" get pod "$REDIS_POD" --no-headers
  exit 0
fi
REMOTE
)$'\n'"$AUTH_CHECK"

RESTART_EXPORTER=$(cat <<'REMOTE'
k3s kubectl -n "$NS" delete pod -l "$EXPORTER_SELECTOR" --wait=true
for _ in $(seq 60); do
  if [ -n "$(k3s kubectl -n "$NS" get pods -l "$EXPORTER_SELECTOR" -o name 2>/dev/null)" ]; then break; fi
  sleep 2
done
if k3s kubectl -n "$NS" wait --for=condition=Ready pod -l "$EXPORTER_SELECTOR" --timeout="$READY_TIMEOUT"; then
  echo EXPORTER_READY
else
  echo EXPORTER_NOT_READY
  k3s kubectl -n "$NS" get pods -l "$EXPORTER_SELECTOR" --no-headers
fi
REMOTE
)

section_of() {
    awk -v want="=== $1 ===" 'index($0, want){f=1;next} /^=== /{f=0} f' <<<"$2"
}

report_prechecks() {
    local output="$1" fatal=0 secret redis backend missing
    secret=$(section_of SECRET "$output")
    redis=$(section_of REDIS "$output")
    backend=$(section_of BACKEND "$output")

    if [[ "$secret" == "key-present" ]]; then
        log_info "Secret $RUNTIME_SECRET has $PASSWORD_KEY"
    else
        log_error "Secret $RUNTIME_SECRET lacks $PASSWORD_KEY (${secret:-no output})"
        fatal=1
    fi

    case "$redis" in
        env-empty) log_info "$REDIS_POD: env-empty (running without AUTH; restart needed)" ;;
        env-set) log_warn "$REDIS_POD: env-set (already started with a password)" ;;
        *) log_warn "$REDIS_POD: ${redis:-no output}" ;;
    esac

    if [[ -z "$backend" ]]; then
        log_error "no backend pods reported"
        fatal=1
    else
        while read -r pod state; do
            if [[ "$state" == "set" ]]; then
                log_info "backend $pod: set"
            else
                log_error "backend $pod: MISSING"
            fi
        done <<<"$backend"
        missing=$(awk '$2!="set"' <<<"$backend")
        if [[ -n "$missing" ]]; then
            fatal=1
        fi
    fi
    return "$fatal"
}

print_verification() {
    cat <<'EOF'

Verify Seoul caught up (Seoul replica offset should climb above 0, keys-with-expiry should reappear):
  .claude/skills/ops/bin/ops metrics 'max(redis_master_repl_offset{job="redis-auth",cluster="seoul"})' --since 30m --step 1m
  .claude/skills/ops/bin/ops metrics 'sum(redis_db_keys_expiring{job="redis-auth"}) by (cluster)' --since 30m --step 1m
EOF
}

precheck_then_restart_redis() {
    local output
    log_info "Pre-checks on Oregon CP $1 ($AWS_REGION)"
    output=$(run_on_cp "$1" "$PRECHECK") || stop_after "pre-check" "$output"
    if ! report_prechecks "$output"; then
        log_error "pre-checks failed; not restarting"
        exit 1
    fi
    if [[ "$2" == "dry-run" ]]; then
        log_info "pre-checks passed; re-run with --apply to restart"
        exit 0
    fi

    log_info "Restarting $REDIS_POD"
    output=$(run_on_cp "$1" "$RESTART_REDIS") || stop_after "Redis restart" "$output"
    echo "$output"
    if ! grep -qx REDIS_READY <<<"$output"; then
        log_error "$REDIS_POD not Ready; exporter left untouched"
        exit 1
    fi
    if ! grep -qx AUTH_REQUIRED <<<"$output"; then
        log_error "$REDIS_POD is Ready but does not require AUTH"
        exit 1
    fi
    log_info "$REDIS_POD Ready and requires AUTH"
}

require_auth() {
    local output
    log_info "Checking that $REDIS_POD requires AUTH on Oregon CP $1 ($AWS_REGION)"
    output=$(run_on_cp "$1" "$AUTH_CHECK") || stop_after "AUTH check" "$output"
    echo "$output"
    if ! grep -qx AUTH_REQUIRED <<<"$output"; then
        log_error "$REDIS_POD does not require AUTH; exporter left untouched; run --apply to restart $REDIS_POD first"
        exit 1
    fi
    log_info "$REDIS_POD requires AUTH"
}

main() {
    local mode="dry-run" cp_id output
    case "${1:-}" in
        "" | --dry-run) mode="dry-run" ;;
        --apply) mode="apply" ;;
        --exporter-only) mode="exporter-only" ;;
        -h | --help) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
    require_cmd jq
    require_cmd aws
    require_aws_session
    CONTROL_PLANE_ENDING_FILE=$(mktemp)
    trap 'rm -f "$CONTROL_PLANE_ENDING_FILE"' EXIT
    cp_id=$(control_plane_id "$TF_DIR")
    if [[ "$mode" == "exporter-only" ]]; then
        require_auth "$cp_id"
    else
        precheck_then_restart_redis "$cp_id" "$mode"
    fi

    log_info "Restarting auth exporter pods ($EXPORTER_SELECTOR)"
    output=$(run_on_cp "$cp_id" "$RESTART_EXPORTER") || stop_after "exporter restart" "$output"
    echo "$output"
    if ! grep -qx EXPORTER_READY <<<"$output"; then
        log_error "auth exporter not Ready"
        exit 1
    fi
    log_info "auth exporter Ready"
    print_verification
}

main "$@"

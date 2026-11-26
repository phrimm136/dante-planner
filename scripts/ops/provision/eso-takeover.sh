#!/usr/bin/env bash
set -euo pipefail

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib/common.sh
source "$OPS_DIR/lib/common.sh"
# shellcheck source=../lib/control-plane.sh
source "$OPS_DIR/lib/control-plane.sh"
# shellcheck source=../lib/traps.sh
source "$OPS_DIR/lib/traps.sh"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/lib/constants.sh"

install_err_trap

TF_ROOT="$OPS_DIR/../../terraform"
END_MARKER="=== END ==="

ESO_NAMESPACE="external-secrets"
RELEASE_NAME="external-secrets"
CHART_NAME="external-secrets"
CHART_REPO="https://charts.external-secrets.io"
CHART_VERSION="0.10.4"
ARGO_NAMESPACE="argocd"
APP_NAME="external-secrets"
APP_NAMESPACE="danteplanner"
WEBHOOK_SECRET="external-secrets-webhook"
INSTANCE_LABEL="app.kubernetes.io/instance=external-secrets"
STRAY_KINDS="deploy,ds,sts,svc,cm,secret,sa,role,rolebinding,clusterrole,clusterrolebinding,pvc,job,cronjob"
CRD_SUFFIX=".external-secrets.io"
KUBECONFIG_PATH="/etc/rancher/k3s/k3s.yaml"
INITIATED_BY="take-over"
EXPECTED_KINDS="CustomResourceDefinition ValidatingWebhookConfiguration"
WAIT_POLLS=120
SNAPSHOT_FORMAT="eso-takeover-2"

EXIT_OK=0
EXIT_FAILED=1
EXIT_USAGE=2
EXIT_STOPPED=3
EXIT_UNVERIFIED=4
EXIT_INTERRUPTED=130
EXIT_TERMINATED=143

usage() {
    cat <<EOF
Take over the boot-installed External Secrets Operator (helm release $RELEASE_NAME,
chart $CHART_VERSION) into the Argo CD Application $APP_NAME on one site's control plane.

Usage:
  scripts/ops/provision/eso-takeover.sh --site oregon|seoul [--dry-run]
                                     pre-checks and the baseline only (default)
  scripts/ops/provision/eso-takeover.sh --site oregon|seoul --apply
                                     pre-checks, baseline, one sync with prune false, wait, verify
  CP_INSTANCE_ID=i-0abc ...          skips the terraform output lookup

Exit: 0 dry run passed, or apply verified (by this run, or against the baseline an earlier
--apply saved under <main checkout>/.claude/reports/eso-takeover/); 3 a pre-check stopped the run,
or an earlier --apply already sent the take-over sync and it did not end Synced and Healthy;
4 already taken over and no saved baseline, so nothing was verified; 1 the verification failed
or a remote command failed; 2 usage.
Needs a live AWS session for the profile in AWS_PROFILE. Runs k3s kubectl and helm on the control
plane via SSM send-command. Prints no secret values.
EOF
}

sync_patch() {
    jq -cn --arg user "$INITIATED_BY" --arg revision "$CHART_VERSION" \
        '{operation: {initiatedBy: {username: $user}, sync: {revision: $revision, prune: false}}}'
}

remote_prelude() {
    printf 'NS=%q\nRELEASE=%q\nCHART=%q\nREPO=%q\nVERSION=%q\nARGO_NS=%q\nAPP=%q\nAPP_NS=%q\n' \
        "$ESO_NAMESPACE" "$RELEASE_NAME" "$CHART_NAME" "$CHART_REPO" "$CHART_VERSION" \
        "$ARGO_NAMESPACE" "$APP_NAME" "$APP_NAMESPACE"
    printf 'WEBHOOK_SECRET=%q\nINSTANCE_LABEL=%q\nSTRAY_KINDS=%q\nCRD_SUFFIX=%q\nPATCH=%q\n' \
        "$WEBHOOK_SECRET" "$INSTANCE_LABEL" "$STRAY_KINDS" "$CRD_SUFFIX" "$(sync_patch)"
    printf 'SNAPSHOT_FORMAT=%q\n' "$SNAPSHOT_FORMAT"
    printf 'export KUBECONFIG=%q\n' "$KUBECONFIG_PATH"
    cat <<'REMOTE'
export HOME="${HOME:-/root}"
k() { k3s kubectl "$@"; }
fail() { printf '%s\n' "$1" >&2; exit 1; }
work=$(mktemp -d) || fail "mktemp -d failed on the control plane"
trap 'rm -rf "$work"' EXIT
REMOTE
}

APP_STATE_READ=$(cat <<'REMOTE'
printf '%s\n' "=== APP ==="
k -n "$ARGO_NS" get application "$APP" -o jsonpath='sync{"\t"}{.status.sync.status}{"\n"}health{"\t"}{.status.health.status}{"\n"}phase{"\t"}{.status.operationState.phase}{"\n"}started{"\t"}{.status.operationState.startedAt}{"\n"}pending{"\t"}{.operation.sync.revision}{"\n"}initiator{"\t"}{.status.operationState.operation.initiatedBy.username}{"\n"}{range .status.resources[*]}resource{"\t"}{.kind}{"\t"}{.namespace}{"\t"}{.name}{"\t"}{.status}{"\t"}{.requiresPruning}{"\n"}{end}message{"\t"}{.status.operationState.message}{"\n"}' \
  || fail "reading the Application $ARGO_NS/$APP failed"
REMOTE
)

PRECHECK=$(cat <<'REMOTE'
printf '%s\n' "=== HELM ==="
helm -n "$NS" list -a -o json || fail "helm -n $NS list failed"
printf '\n%s\n' "=== LIVE ==="
k get "$STRAY_KINDS" -A -l "$INSTANCE_LABEL" \
  -o custom-columns=K:.kind,NS:.metadata.namespace,N:.metadata.name --no-headers || fail "the labeled object query failed"
k -n "$ARGO_NS" get application "$APP" -o jsonpath='{.spec.source.helm.valuesObject}' > "$work/values.json" \
  || fail "reading the Application's valuesObject failed"
printf '%s\n' "=== RENDER ==="
rendered=0
if [ ! -s "$work/values.json" ]; then
  printf '%s\n' "render-failed the Application $APP has no .spec.source.helm.valuesObject"
elif helm template "$RELEASE" "$CHART" --repo "$REPO" --version "$VERSION" -n "$NS" --include-crds \
  -f "$work/values.json" > "$work/render.yaml" 2> "$work/render.err"; then
  rendered=1
  awk 'function flush() { if (kind != "") print kind, (ns == "" ? "<none>" : ns), name; kind = ""; ns = ""; name = ""; meta = 0 }
    /^---/ { flush(); next }
    /^kind: / { kind = $2; next }
    /^metadata:/ { meta = 1; next }
    /^[^ ]/ { meta = 0 }
    meta && /^  name: / { name = $2; gsub(/"/, "", name) }
    meta && /^  namespace: / { ns = $2; gsub(/"/, "", ns) }
    END { flush() }' "$work/render.yaml"
else
  printf '%s\n' "render-failed $(tail -n 3 "$work/render.err" | tr '\n' ' ')"
fi
printf '%s\n' "=== DIFF ==="
if [ "$rendered" = 1 ]; then
  cat > "$work/eso-diff" <<'DIFF'
#!/bin/sh
block() {
  [ -f "$1" ] || return 0
  awk '/^spec:/ { s = 1; next } /^[^ ]/ { s = 0 } s && /^  template:/ { t = 1; print; next }
    t && (/^    / || /^$/) { print; next } { t = 0 }' "$1"
}
stripped() {
  [ -f "$1" ] || return 0
  awk '/^[^ ]/ { meta = ($0 == "metadata:") }
    meta && /^    kubectl\.kubernetes\.io\/last-applied-configuration:/ { skip = 1; next }
    skip && /^      / { next }
    { skip = 0; kept[++n] = $0 }
    END { for (i = 1; i <= n; i++) if (kept[i] != "  annotations:" || kept[i + 1] ~ /^    /) print kept[i] }' "$1"
}
kind_of() {
  for side in "$@"; do
    [ -f "$side" ] && { awk '/^kind: / { print $2; exit }' "$side"; return 0; }
  done
}
code=0
for id in $(for f in "$1"/* "$2"/*; do [ -e "$f" ] && printf '%s\n' "${f##*/}"; done | sort -u); do
  [ "$(stripped "$1/$id")" = "$(stripped "$2/$id")" ] && continue
  code=1
  printf 'changed %s %s\n' "$(kind_of "$2/$id" "$1/$id")" "$id"
  [ "$(block "$1/$id")" = "$(block "$2/$id")" ] || printf 'template %s\n' "$id"
done
exit "$code"
DIFF
  chmod +x "$work/eso-diff"
  KUBECTL_EXTERNAL_DIFF="$work/eso-diff" k diff -f "$work/render.yaml"
  diff_code=$?
  [ "$diff_code" -le 1 ] || fail "kubectl diff failed with exit $diff_code"
  printf '%s\n' "diff-exit $diff_code"
fi
REMOTE
)

SNAPSHOT_READ=$(cat <<'REMOTE'
printf '%s\n' "=== SNAPSHOT ==="
printf 'format %s\n' "$SNAPSHOT_FORMAT"
rows=$(k -n "$NS" get deploy -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.metadata.uid}{" "}{.metadata.annotations.deployment\.kubernetes\.io/revision}{"\n"}{end}') \
  || fail "reading the $NS Deployments failed"
printf '%s\n' "$rows" | awk 'NF { print "deployment", $1, $2, ($3 == "" ? "-" : $3) }'
rows=$(k -n "$NS" get replicasets -o custom-columns=N:.metadata.name,U:.metadata.uid --no-headers) \
  || fail "reading the $NS ReplicaSets failed"
printf '%s\n' "$rows" | awk 'NF { print "replicaset", $1, $2 }'
rows=$(k -n "$NS" get pods -o custom-columns=N:.metadata.name,U:.metadata.uid,S:.status.startTime,R:.status.containerStatuses[0].restartCount --no-headers) \
  || fail "reading the $NS pods failed"
printf '%s\n' "$rows" | awk 'NF { print "pod", $1, $2, $3, $4 }'
rows=$(k -n "$APP_NS" get secrets -o custom-columns=N:.metadata.name,U:.metadata.uid --no-headers) \
  || fail "reading the $APP_NS Secrets failed"
printf '%s\n' "$rows" | awk 'NF { print "secret", $1, $2 }'
data=$(k -n "$NS" get secret "$WEBHOOK_SECRET" -o jsonpath='{.data}') || fail "reading the Secret $NS/$WEBHOOK_SECRET failed"
printf 'webhook-data %s %s\n' "$WEBHOOK_SECRET" "$(printf '%s' "$data" | sha256sum | cut -d ' ' -f 1)"
unset data
rows=$(k get crd -o custom-columns=N:.metadata.name,U:.metadata.uid,G:.metadata.generation --no-headers) \
  || fail "reading the CRDs failed"
printf '%s\n' "$rows" | awk -v suffix="$CRD_SUFFIX" 'NF && substr($1, length($1) - length(suffix) + 1) == suffix { print "crd", $1, $2, $3 }'
rows=$(k -n "$APP_NS" get externalsecret -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}') \
  || fail "reading the $APP_NS ExternalSecrets failed"
printf '%s\n' "$rows" | awk 'NF { print "externalsecret", $1, ($2 == "" ? "-" : $2) }'
REMOTE
)

SYNC=$(cat <<'REMOTE'
k -n "$ARGO_NS" patch application "$APP" --type merge -p "$PATCH" || fail "patching the Application $ARGO_NS/$APP failed"
REMOTE
)

section_of() {
    awk -v want="=== $1 ===" '$0 == want { f = 1; next } /^=== .* ===$/ { f = 0 } f' <<<"$2"
}

field_of() {
    awk -F '\t' -v want="$1" '$1 == want { print $2; exit }' <<<"$2"
}

run_phase() {
    local output
    if ! output=$(run_on_control_plane "$REGION" "$CP_ID" "$(remote_prelude)"$'\n'"$1"$'\n'"printf '%s\n' '$END_MARKER'"); then
        [[ -z "$output" ]] || printf '%s\n' "$output" >&2
        return 1
    fi
    if [[ "${output##*$'\n'}" != "$END_MARKER" ]]; then
        printf '%s\n' "$output" >&2
        log_error "the output read from the control plane is incomplete" >&2
        return 1
    fi
    printf '%s\n' "${output%"$END_MARKER"}"
}

already_taken_over() {
    [[ "$(field_of sync "$1")" == Synced && "$(field_of health "$1")" == Healthy \
        && "$(field_of phase "$1")" == Succeeded ]]
}

check_release() {
    local rows
    if ! rows=$(jq -r --arg name "$RELEASE_NAME" '.[] | select(.name == $name) | "\(.chart) \(.status)"' <<<"$1" 2>/dev/null); then
        echo "stop: helm list printed no JSON"
    elif [[ -z "$rows" ]]; then
        echo "stop: helm lists no release $RELEASE_NAME in $ESO_NAMESPACE"
    elif [[ "$rows" != "$CHART_NAME-$CHART_VERSION deployed" ]]; then
        echo "stop: release $RELEASE_NAME is ${rows//$'\n'/, }, want $CHART_NAME-$CHART_VERSION deployed"
    else
        echo "ok: release $RELEASE_NAME is $CHART_NAME-$CHART_VERSION deployed"
    fi
}

render_failure() {
    sed -n 's/^render-failed //p' <<<"$1"
}

check_strays() {
    local render="$1" live="$2" failure strays
    failure=$(render_failure "$render")
    if [[ -n "$failure" ]]; then
        echo "stop: helm template $CHART_NAME $CHART_VERSION failed: $failure"
        return
    fi
    strays=$(awk 'NR == FNR { if (NF) seen[$1 " " $2 " " $3] = 1; next }
        NF && !(($1 " " $2 " " $3) in seen) { print $1 "/" $2 "/" $3 }' <(printf '%s\n' "$render") <(printf '%s\n' "$live"))
    if [[ -n "$strays" ]]; then
        echo "stop: labeled objects outside the chart's render: ${strays//$'\n'/, }"
    else
        echo "ok: every labeled object is in the chart's render"
    fi
}

check_diff() {
    local render="$1" diff="$2" failure templates others expected
    failure=$(render_failure "$render")
    if [[ -n "$failure" ]]; then
        echo "stop: helm template $CHART_NAME $CHART_VERSION failed: $failure"
        return
    fi
    if ! grep -qx 'diff-exit [01]' <<<"$diff"; then
        echo "stop: kubectl diff printed no exit line"
        return
    fi
    templates=$(sed -n 's/^template //p' <<<"$diff")
    others=$(awk -v kinds=" $EXPECTED_KINDS " '$1 == "changed" && index(kinds, " " $2 " ") == 0 { print $3 }' <<<"$diff")
    expected=$(awk -v kinds=" $EXPECTED_KINDS " '$1 == "changed" && index(kinds, " " $2 " ") > 0 { print $3 }' <<<"$diff")
    if [[ -n "$templates" ]]; then
        echo "stop: the sync would change spec.template of ${templates//$'\n'/, }"
    elif [[ -n "$others" ]]; then
        echo "stop: the sync would change objects besides ${EXPECTED_KINDS// / and }: ${others//$'\n'/, }"
    elif [[ -n "$expected" ]]; then
        echo "ok: only ${EXPECTED_KINDS// / and } objects change: ${expected//$'\n'/, }"
    else
        echo "ok: no difference"
    fi
}

check_resources() {
    local app="$1" rows counts others pruning
    if [[ -n "$(field_of pending "$app")" || "$(field_of phase "$app")" == Running ]]; then
        echo "stop: an operation is already pending or running on the Application"
        return
    fi
    rows=$(awk -F '\t' '$1 == "resource"' <<<"$app")
    if [[ -z "$rows" ]]; then
        echo "stop: the Application lists no resources"
        return
    fi
    counts=$(awk -F '\t' -v kinds="$EXPECTED_KINDS" 'BEGIN { split(kinds, wanted, " ") }
        $5 != "Synced" { n[$2]++ }
        END { for (i = 1; i in wanted; i++) printf "%s%d %s", (i > 1 ? ", " : ""), n[wanted[i]], wanted[i] }' <<<"$rows")
    others=$(awk -F '\t' -v kinds=" $EXPECTED_KINDS " '$5 != "Synced" && index(kinds, " " $2 " ") == 0 {
        print $2 "/" ($3 == "" ? "-" : $3) "/" $4 " " ($5 == "" ? "-" : $5) }' <<<"$rows")
    pruning=$(awk -F '\t' '$6 == "true" { print $2 "/" ($3 == "" ? "-" : $3) "/" $4 }' <<<"$rows")
    if [[ -n "$others" ]]; then
        echo "stop: non-Synced resources besides the expected kinds: ${others//$'\n'/, } (non-Synced: $counts)"
    elif [[ -n "$pruning" ]]; then
        echo "stop: resources that require pruning: ${pruning//$'\n'/, }"
    else
        echo "ok: non-Synced: $counts; none requires pruning"
    fi
}

report_prechecks() {
    local output="$1" app verdicts
    app=$(section_of APP "$output")
    verdicts=$(printf '%s\n' \
        "check 1 helm release: $(check_release "$(section_of HELM "$output")")" \
        "check 2 stray objects: $(check_strays "$(section_of RENDER "$output")" "$(section_of LIVE "$output")")" \
        "check 3 diff: $(check_diff "$(section_of RENDER "$output")" "$(section_of DIFF "$output")")" \
        "check 4 application resources: $(check_resources "$app")")
    printf '%s\n' "$verdicts"
    ! grep -q '^check [0-9] [^:]*: stop: ' <<<"$verdicts"
}

compare_snapshots() {
    awk 'NR == FNR { if (NF) { key = $1 " " $2; before[key] = $0; order[++count] = key }; next }
        NF { key = $1 " " $2; after[key] = $0; if (!(key in before)) order[++count] = key }
        END {
            for (i = 1; i <= count; i++) {
                key = order[i]
                if (before[key] != after[key]) {
                    print "before: " (key in before ? before[key] : "-")
                    print "after:  " (key in after ? after[key] : "-")
                    differs = 1
                }
            }
            exit differs
        }' <(printf '%s\n' "$1") <(printf '%s\n' "$2")
}

not_ready() {
    awk '$1 == "externalsecret" && $3 != "True" { print $2 " " $3 }' <<<"$1"
}

print_stopped_action() {
    log_info "nothing was patched; fix the stop reason above and rerun with --site $SITE --dry-run"
}

print_failed_action() {
    log_error "no rollback ran; read the Application with: k3s kubectl -n $ARGO_NAMESPACE get application $APP_NAME -o yaml"
    log_error "then fix forward, or revert the commit that added deploy/chart/templates/external-secrets.yaml"
}

explain_failed() {
    log_error "the take-over operation ended $(field_of phase "$1"): $(field_of message "$1")"
    log_error "do not rerun --apply"
    print_failed_action
}

unsynced_rows() {
    awk -F '\t' '$1 == "resource" && $5 != "Synced" { print $2 "/" $4 " " ($5 == "" ? "-" : $5) }' <<<"$1"
}

print_unsynced() {
    local unsynced
    unsynced=$(unsynced_rows "$1")
    unsynced=${unsynced//$'\n'/, }
    log_error "Application $APP_NAME: sync $(field_of sync "$1"), health $(field_of health "$1"); not Synced: ${unsynced:-none}"
}

explain_unsynced() {
    local unsynced
    unsynced=$(unsynced_rows "$1")
    log_error "the sync ran and nothing was recreated: the snapshot equals the saved baseline"
    print_unsynced "$1"
    if [[ -n "$unsynced" ]] && ! grep -qv '^ValidatingWebhookConfiguration/' <<<"$unsynced"; then
        log_error "every resource not Synced is a ValidatingWebhookConfiguration: the cert-controller injects its caBundle, which chart $CHART_VERSION does not render"
        log_error "next: add the ignoreDifferences entry in deploy/chart/templates/external-secrets.yaml, merge, then rerun with --site $SITE --apply: it sends no second sync and verifies against the saved baseline"
        return
    fi
    print_failed_action
    log_error "after the fix syncs, rerun with --site $SITE --apply: it sends no second sync and verifies against the saved baseline"
}

explain_verify_failed() {
    log_error "the sync ran and the snapshot differs from the saved baseline; do not rerun --apply"
    print_failed_action
}

explain_read_failed() {
    log_error "the snapshot could not be read from the control plane; the baseline is saved at $BASELINE_FILE"
    log_error "next: rerun with --site $SITE --apply; it verifies against the saved baseline"
}

explain_unsynced_uncompared() {
    log_error "the sync ran; the snapshot comparison against the saved baseline did not finish, so this run says nothing about recreated objects"
    print_unsynced "$OUTCOME_APP"
    log_error "the baseline is saved at $BASELINE_FILE"
    log_error "next: rerun with --site $SITE --apply; it sends no second sync and verifies against the saved baseline"
}

after_patch_exit() {
    [[ "$1" -ne 0 ]] || return 0
    case "$OUTCOME" in
        failed) explain_failed "$OUTCOME_APP" ;;
        unsynced) explain_unsynced "$OUTCOME_APP" ;;
        comparing) explain_unsynced_uncompared ;;
        differs) explain_verify_failed ;;
        read-failed) explain_read_failed ;;
        *)
            log_error "a sync operation was submitted and may still be running"
            log_error "the baseline is saved at $BASELINE_FILE"
            log_error "next: rerun with --site $SITE --apply; it verifies against the saved baseline"
            ;;
    esac
}

wait_until_synced() {
    local started_before="$1" poll output app phase sync health last_app="" last_done=""
    for ((poll = 1; poll <= WAIT_POLLS; poll++)); do
        if ! output=$(run_phase "$APP_STATE_READ"); then
            log_warn "poll $poll/$WAIT_POLLS: reading the Application failed; polling again"
            sleep "$POLL_SECONDS"
            continue
        fi
        app=$(section_of APP "$output")
        phase=$(field_of phase "$app")
        sync=$(field_of sync "$app")
        health=$(field_of health "$app")
        last_app="$app"
        last_done=""
        if [[ -z "$(field_of pending "$app")" && "$(field_of started "$app")" != "$started_before" ]]; then
            case "$phase" in
                Failed | Error)
                    OUTCOME=failed
                    OUTCOME_APP="$app"
                    return 1
                    ;;
                Succeeded)
                    if [[ "$sync" == Synced && "$health" == Healthy ]]; then
                        OUTCOME=unfinished
                        OUTCOME_APP=""
                        log_info "operation Succeeded; Application $APP_NAME Synced and Healthy"
                        return 0
                    fi
                    last_done=Succeeded
                    ;;
            esac
        fi
        log_info "poll $poll/$WAIT_POLLS: operation ${phase:-not started}, sync ${sync:--}, health ${health:--}"
        sleep "$POLL_SECONDS"
    done
    log_error "the Application was not Succeeded, Synced and Healthy within $WAIT_POLLS polls"
    if [[ "$last_done" == Succeeded ]]; then
        OUTCOME=comparing
        OUTCOME_APP="$last_app"
    fi
    return 1
}

verify() {
    local baseline="$1" output after failed=0 missing
    if ! output=$(run_phase "$SNAPSHOT_READ"); then
        [[ "$OUTCOME" == comparing ]] || OUTCOME=read-failed
        return 1
    fi
    after=$(section_of SNAPSHOT "$output")
    if ! compare_snapshots "$baseline" "$after"; then
        log_error "the snapshot differs from the baseline in the rows above"
        failed=1
    fi
    missing=$(not_ready "$after")
    if [[ -n "$missing" ]]; then
        log_error "ExternalSecrets not Ready: ${missing//$'\n'/, }"
        failed=1
    fi
    if [[ "$failed" -ne 0 ]]; then
        OUTCOME=differs
        return 1
    fi
    log_info "the snapshot equals the baseline; every ExternalSecret is Ready"
}

explain_verify_outcome() {
    case "$OUTCOME" in
        differs) explain_verify_failed ;;
        read-failed) explain_read_failed ;;
    esac
}

load_saved_baseline() {
    if [[ "$(awk '$1 == "format" { print $2; exit }' "$BASELINE_FILE")" != "$SNAPSHOT_FORMAT" ]]; then
        log_error "the baseline saved at $BASELINE_FILE predates the snapshot format $SNAPSHOT_FORMAT (Deployment generations, no revisions or ReplicaSets) and is not compared"
        log_error "next: compare by hand: the Deployment uids and deployment.kubernetes.io/revision annotations, the ReplicaSets and the pods in $ESO_NAMESPACE (k3s kubectl -n $ESO_NAMESPACE get deploy,rs,pods -o wide) against the rows in that file"
        return 1
    fi
    SAVED_BASELINE=$(cat "$BASELINE_FILE")
}

verify_saved_and_exit() {
    load_saved_baseline || exit "$EXIT_FAILED"
    log_info "VERIFY against the baseline saved at $BASELINE_FILE"
    if ! verify "$SAVED_BASELINE"; then
        explain_verify_outcome
        exit "$EXIT_FAILED"
    fi
    print_success
    exit "$EXIT_OK"
}

stop_after_take_over() {
    local app="$1"
    local phase
    phase=$(field_of phase "$app")
    case "$phase" in
        Succeeded)
            if already_taken_over "$app"; then
                log_info "an earlier --apply took over: its operation Succeeded and the Application is Synced and Healthy"
                verify_saved_and_exit
            fi
            load_saved_baseline || exit "$EXIT_FAILED"
            log_info "VERIFY against the baseline saved at $BASELINE_FILE"
            if ! verify "$SAVED_BASELINE"; then
                explain_verify_outcome
                exit "$EXIT_FAILED"
            fi
            explain_unsynced "$app"
            ;;
        Failed | Error) explain_failed "$app" ;;
        *)
            log_error "the take-over operation of an earlier run is ${phase:-pending}; no second sync is sent"
            log_info "next: rerun with --site $SITE --apply once it has ended; it verifies against $BASELINE_FILE"
            ;;
    esac
    log_error "an earlier --apply already sent the take-over sync (baseline $BASELINE_FILE); nothing was patched"
    exit "$EXIT_STOPPED"
}

baseline_dir() {
    if [[ -n "${ESO_TAKEOVER_DIR:-}" ]]; then
        echo "$ESO_TAKEOVER_DIR"
        return
    fi
    echo "$(dirname "$(git -C "$OPS_DIR" rev-parse --path-format=absolute --git-common-dir)")/.claude/reports/eso-takeover"
}

save_baseline() {
    mkdir -p "$(dirname "$BASELINE_FILE")"
    printf '%s\n' "$1" >"$BASELINE_FILE.tmp"
    mv -f "$BASELINE_FILE.tmp" "$BASELINE_FILE"
}

print_success() {
    log_info "taken over and verified; next: .claude/skills/ops/bin/ops verify fleet --site $SITE, then the other site"
}

main() {
    local mode="dry-run" output app baseline
    SITE=""
    OUTCOME=unfinished
    OUTCOME_APP=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --site)
                [[ $# -ge 2 ]] || { usage >&2; exit "$EXIT_USAGE"; }
                SITE="$2"
                shift
                ;;
            --dry-run) mode="dry-run" ;;
            --apply) mode="apply" ;;
            -h | --help) usage; exit "$EXIT_OK" ;;
            *) usage >&2; exit "$EXIT_USAGE" ;;
        esac
        shift
    done
    REGION=$(site_region "$SITE") || { usage >&2; exit "$EXIT_USAGE"; }
    require_cmd jq
    require_cmd aws
    require_aws_session
    CP_ID=$(control_plane_id "$TF_ROOT/$SITE")
    BASELINE_FILE="$(baseline_dir)/$SITE-baseline.txt"

    log_info "PRECHECK on the $SITE control plane $CP_ID ($REGION)"
    output=$(run_phase "$APP_STATE_READ"$'\n'"$PRECHECK") || exit "$EXIT_FAILED"
    app=$(section_of APP "$output")
    if [[ -f "$BASELINE_FILE" && "$(field_of initiator "$app")" == "$INITIATED_BY" ]]; then
        stop_after_take_over "$app"
    fi
    if already_taken_over "$app"; then
        if [[ ! -f "$BASELINE_FILE" ]]; then
            log_warn "already taken over; this run verified nothing (no saved baseline at $BASELINE_FILE)"
            log_info "next: compare the before rows a person saved by hand with: k3s kubectl -n $ESO_NAMESPACE get deploy,rs,pods -o wide; with none saved, read the ExternalSecrets' Ready conditions and the operator pods' ages: k3s kubectl -n $APP_NAMESPACE get externalsecret; k3s kubectl -n $ESO_NAMESPACE get pods"
            exit "$EXIT_UNVERIFIED"
        fi
        log_info "already-taken-over: Application $APP_NAME is Synced, Healthy, last operation Succeeded"
        verify_saved_and_exit
    fi
    if ! report_prechecks "$output"; then
        log_error "a pre-check stopped the run"
        print_stopped_action
        exit "$EXIT_STOPPED"
    fi

    log_info "BASELINE"
    output=$(run_phase "$SNAPSHOT_READ") || exit "$EXIT_FAILED"
    baseline=$(section_of SNAPSHOT "$output")
    printf '%s\n' "$baseline"
    if [[ "$mode" == "dry-run" ]]; then
        log_info "pre-checks passed; rerun with --site $SITE --apply to sync"
        exit "$EXIT_OK"
    fi
    if [[ -f "$BASELINE_FILE" ]]; then
        load_saved_baseline || exit "$EXIT_FAILED"
        baseline="$SAVED_BASELINE"
        log_info "an earlier run saved a baseline at $BASELINE_FILE; this run verifies against it, not the rows above"
    else
        save_baseline "$baseline"
        log_info "baseline saved to $BASELINE_FILE"
    fi

    trap 'after_patch_exit $?' EXIT
    trap 'exit "$EXIT_INTERRUPTED"' INT
    trap 'exit "$EXIT_TERMINATED"' TERM
    log_info "SYNC: $(sync_patch)"
    run_phase "$SYNC" >/dev/null || exit "$EXIT_FAILED"
    log_info "WAIT"
    if wait_until_synced "$(field_of started "$app")"; then
        log_info "VERIFY"
        verify "$baseline" || exit "$EXIT_FAILED"
        print_success
        return
    fi
    if [[ "$OUTCOME" == comparing ]]; then
        log_info "VERIFY"
        verify "$baseline" || exit "$EXIT_FAILED"
        OUTCOME=unsynced
    fi
    exit "$EXIT_FAILED"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi

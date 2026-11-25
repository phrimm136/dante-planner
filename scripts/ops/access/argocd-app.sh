#!/usr/bin/env bash
set -euo pipefail

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib/common.sh
source "$OPS_DIR/lib/common.sh"
# shellcheck source=../lib/control-plane.sh
source "$OPS_DIR/lib/control-plane.sh"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/lib/constants.sh"

TF_ROOT="$OPS_DIR/../../terraform"
NAME_PREFIX="danteplanner"
END_MARKER="=== END ==="

remote_application() {
    printf 'APP=%q\n' "$NAME_PREFIX-$1"
    cat <<'REMOTE'
k3s kubectl -n argocd get applications.argoproj.io "$APP" -o jsonpath='path{"\t"}{.spec.source.path}{"\n"}values{"\t"}{.spec.source.helm.valueFiles[*]}{"\n"}sync{"\t"}{.status.sync.status}{"\n"}health{"\t"}{.status.health.status}{"\n"}automated{"\t"}{.spec.syncPolicy.automated}{"\n"}{range .status.resources[*]}resource{"\t"}{.status}{"\t"}{.kind}{"\t"}{.name}{"\n"}{end}{range .status.conditions[*]}condition{"\t"}{.type}{"\t"}{.message}{"\n"}{end}' || exit 1
printf '%s\n' "=== END ==="
REMOTE
}

report_application() {
    awk -F '\t' '
        $1 == "automated" {
            off = ($2 == "" || $2 ~ /"enabled":[ ]*false/ || $2 ~ /enabled:false/)
            print "automated sync  " (off ? "off" : "on"); next }
        $1 == "path" { print "source path     " $2; next }
        $1 == "values" { print "values file     " ($2 == "" ? "-" : $2); next }
        $1 == "sync" { print "sync            " ($2 == "" ? "-" : $2); next }
        $1 == "health" { print "health          " ($2 == "" ? "-" : $2); next }
        $1 == "resource" { if ($2 != "Synced") print "not synced      " ($2 == "" ? "-" : $2) " " $3 " " $4; next }
        $1 == "condition" { print "condition       " $2 ": " $3; next }
        { print "                " $0 }' <<<"$1"
}

main() {
    local site="${1:-oregon}" region cp_id output
    region=$(site_region "$site") || { echo "usage: argocd-app.sh [oregon|seoul]" >&2; exit 2; }
    require_cmd jq
    require_aws_session
    cp_id=$(control_plane_id "$TF_ROOT/$site")
    output=$(run_on_control_plane "$region" "$cp_id" "$(remote_application "$site")")
    if [[ "${output##*$'\n'}" != "$END_MARKER" ]]; then
        report_application "$output"
        log_error "the Application read from the control plane is incomplete" >&2
        exit 1
    fi
    report_application "${output%$'\n'"$END_MARKER"}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi

# shellcheck shell=bash

ETCD_INTERVAL_VARIABLES="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)/terraform/modules/fleet/variables.tf"

etcd_snapshot_interval_hours() {
    local hours
    hours=$(awk '/^variable "etcd_snapshot_interval_hours"/ { inside = 1 }
        inside && /^[[:space:]]*default[[:space:]]*=/ { print $3; exit }' "$ETCD_INTERVAL_VARIABLES")
    if [[ ! "$hours" =~ ^[1-9][0-9]*$ ]]; then
        echo "no whole-number default for etcd_snapshot_interval_hours in $ETCD_INTERVAL_VARIABLES" >&2
        return 1
    fi
    echo "$hours"
}

etcd_snapshot_interval_seconds() {
    local hours
    hours=$(etcd_snapshot_interval_hours) || return 1
    echo $((hours * 3600))
}

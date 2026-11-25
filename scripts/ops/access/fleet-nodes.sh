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
JOIN_BOUND_SECONDS=600
EXIT_UNREADABLE=2
END_MARKER="=== END ==="

remote_nodes() {
    cat <<'REMOTE'
k3s kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.addresses[?(@.type=="InternalIP")].address}{"\t"}{.status.conditions[?(@.type=="Ready")].status}{"\t"}{.metadata.labels.role}{"\t"}{.metadata.labels.node-role\.kubernetes\.io/control-plane}{"\n"}{end}' || exit 1
printf '%s\n' "=== END ==="
REMOTE
}

site_instances() {
    aws ec2 describe-instances --region "$1" \
        --filters "Name=tag:Name,Values=$NAME_PREFIX-$2-*" \
        "Name=tag:Role,Values=app,data,ingress,control-plane" \
        "Name=instance-state-name,Values=pending,running,stopping,stopped" \
        --query 'Reservations[].Instances[]' --output json
}

group_members() {
    aws autoscaling describe-auto-scaling-groups --region "$1" --auto-scaling-group-names "$2" \
        --query 'AutoScalingGroups[].Instances[]' --output json
}

join_rows() {
    jq -nc --argjson now "$1" --argjson bound "$JOIN_BOUND_SECONDS" --argjson instances "$2" \
        --argjson members "$3" --arg nodes "$4" '
        def role_name: if . == "control-plane" then "cp" else . end;
        def tag($name): (.Tags // []) | map(select(.Key == $name).Value) | first // "";
        ($nodes | split("\n") | map(select(length > 0) | split("\t")
            | {name: .[0], ip: .[1], ready: (.[2] == "True"),
               role: (if .[4] == "true" then "cp" elif .[3] == "" then "-" else .[3] end)})) as $cluster
        | ($members | map({key: .InstanceId, value: .LifecycleState}) | from_entries) as $group
        | ($instances | map(
            .PrivateIpAddress as $ip
            | (.LaunchTime | sub("\\.[0-9]+"; "") | sub("\\+00:00$"; "Z") | fromdateiso8601) as $launched
            | {id: .InstanceId, role: (tag("Role") | role_name), surge: (tag("Surge") == "true"),
               member: (.InstanceId as $id | $group | has($id)), lifecycle: ($group[.InstanceId] // .State.Name),
               launched: $launched, ip: ($ip // "-"),
               node: ($cluster | map(select($ip != null and .ip == $ip)) | first)}
            | .state = (if .node then "joined" elif $now - $launched < $bound then "joining" else "NOT JOINED" end)
            )) as $rows
        | ($rows | sort_by(.role, .id))[],
          ($cluster[] | select(.ip as $ip | any($rows[]; .ip == $ip) | not)
            | {id: "-", role: .role, surge: false, member: false, lifecycle: "-", launched: null, ip: .ip,
               node: ., state: "gone"})'
}

row_lines() {
    jq -r --argjson now "$1" '[
        .state, .role, .id, .lifecycle,
        (if .launched then "\(.launched | todate) (\(($now - .launched) / 60 | floor)m)" else "-" end),
        .ip,
        (if .node then "\(.node.name) \(if .node.ready then "Ready" else "NotReady" end)" else "no node" end),
        (if .surge then "surge" else empty end)] | join("  ")'
}

action_lines() {
    jq -r --arg region "$1" --arg group "$2" --arg profile "${AWS_PROFILE:+AWS_PROFILE=$AWS_PROFILE }" '
        select(.state == "NOT JOINED")
        | if .surge | not then "\(.id): no terminate command, it has no Surge=true tag"
          elif .member | not then "\(.id): no terminate command, it is not in the ASG \($group)"
          elif .lifecycle | startswith("Terminating") then "\(.id): no terminate command, the ASG is terminating it"
          else "! \($profile)aws autoscaling terminate-instance-in-auto-scaling-group --region \($region) --instance-id \(.id) --no-should-decrement-desired-capacity"
          end'
}

exit_code_of() {
    jq -rs 'map(.state) as $states
        | if any($states[]; . == "NOT JOINED" or . == "gone") then 1
          elif any($states[]; . == "joining") then 4
          else 0 end'
}

main() {
    local site="${1:-oregon}" region group cp_id nodes instances members rows now
    region=$(site_region "$site") || { echo "usage: fleet-nodes.sh [oregon|seoul]" >&2; exit "$EXIT_UNREADABLE"; }
    require_cmd jq
    require_aws_session || exit "$EXIT_UNREADABLE"
    group="$NAME_PREFIX-$site-app"
    cp_id=$(control_plane_id "$TF_ROOT/$site") || exit "$EXIT_UNREADABLE"
    nodes=$(run_on_control_plane "$region" "$cp_id" "$(remote_nodes)") || exit "$EXIT_UNREADABLE"
    if [[ "${nodes##*$'\n'}" != "$END_MARKER" ]]; then
        log_error "the node list from the control plane is incomplete" >&2
        exit "$EXIT_UNREADABLE"
    fi
    instances=$(site_instances "$region" "$site") || exit "$EXIT_UNREADABLE"
    members=$(group_members "$region" "$group") || exit "$EXIT_UNREADABLE"
    now=$(date +%s)
    rows=$(join_rows "$now" "$instances" "$members" "${nodes%"$END_MARKER"}") || exit "$EXIT_UNREADABLE"
    if [[ -z "$rows" ]]; then
        log_error "no instance and no node found for $site" >&2
        exit "$EXIT_UNREADABLE"
    fi
    row_lines "$now" <<<"$rows"
    action_lines "$region" "$group" <<<"$rows"
    exit "$(exit_code_of <<<"$rows")"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi

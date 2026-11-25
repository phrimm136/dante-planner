# shellcheck shell=bash

CHECK_BOUND_SECONDS=180
POLL_SECONDS=5

site_region() {
    case "$1" in
        oregon) echo us-west-2 ;;
        seoul) echo ap-northeast-2 ;;
        *) return 1 ;;
    esac
}

control_plane_id() {
    if [[ -n "${CP_INSTANCE_ID:-}" ]]; then
        echo "$CP_INSTANCE_ID"
        return
    fi
    terraform -chdir="$1" output -raw cp_instance_id
}

# The remote script is JSON-encoded line-by-line with jq so quoting inside the commands
# (the pod's own $MYSQL_HOST, kubectl -o custom-columns) survives the send-command boundary.
run_on_control_plane() {
    local region="$1" cp_id="$2" bound="${4:-$CHECK_BOUND_SECONDS}" params cmd_id status waited=0
    params=$(jq -n --arg s "$3" '{commands: ($s | split("\n"))}') || return 1
    cmd_id=$(aws ssm send-command --region "$region" \
        --instance-ids "$cp_id" \
        --document-name "AWS-RunShellScript" \
        --parameters "$params" \
        --query 'Command.CommandId' --output text) || return 1
    while :; do
        status=$(aws ssm get-command-invocation --region "$region" --command-id "$cmd_id" \
            --instance-id "$cp_id" --query 'Status' --output text 2>/dev/null || echo Pending)
        case "$status" in
            Pending | InProgress | Delayed) ;;
            *) break ;;
        esac
        if [[ "$waited" -ge "$bound" ]]; then
            log_error "the control plane did not answer within ${bound} seconds; status $status" >&2
            return 1
        fi
        sleep "$POLL_SECONDS"
        waited=$((waited + POLL_SECONDS))
    done
    aws ssm get-command-invocation --region "$region" \
        --command-id "$cmd_id" --instance-id "$cp_id" \
        --query 'StandardOutputContent' --output text || return 1
    if [[ "$status" != Success ]]; then
        log_error "the check command on the control plane ended with status $status" >&2
        aws ssm get-command-invocation --region "$region" \
            --command-id "$cmd_id" --instance-id "$cp_id" \
            --query 'StandardErrorContent' --output text >&2 || true
        return 1
    fi
}

#!/usr/bin/env bash
set -euo pipefail

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib/common.sh
source "$OPS_DIR/lib/common.sh"
# shellcheck source=../lib/control-plane.sh
source "$OPS_DIR/lib/control-plane.sh"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/lib/constants.sh"

main() {
    local instance_id="${1:-}" region
    region=$(site_region "${2:-oregon}") || region=""
    if [[ ! "$instance_id" =~ ^i-[0-9a-f]+$ || -z "$region" ]]; then
        echo "usage: boot-log.sh <instance-id> [oregon|seoul]" >&2
        exit 2
    fi
    require_aws_session
    aws ec2 get-console-output --region "$region" --instance-id "$instance_id" --latest \
        --query Output --output text | tr -d '\r'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi

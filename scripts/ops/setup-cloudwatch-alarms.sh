#!/bin/bash
# CloudWatch Alarms and SNS Setup for DantePlanner
# Idempotent - safe to run multiple times.
#
# Thin entrypoint: sources the per-concern libraries under lib/ and runs main().
# Deployed via .github/workflows/setup-cloudwatch.yml (SSM after a full git clone),
# so lib/ ships alongside this file.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$SCRIPT_DIR/lib"

# shellcheck source=lib/common.sh
source "$LIB_DIR/common.sh"
# shellcheck source=lib/iam.sh
source "$LIB_DIR/iam.sh"
# shellcheck source=lib/retention.sh
source "$LIB_DIR/retention.sh"
# shellcheck source=lib/sns.sh
source "$LIB_DIR/sns.sh"
# shellcheck source=lib/metric-filters.sh
source "$LIB_DIR/metric-filters.sh"
# shellcheck source=lib/alarms.sh
source "$LIB_DIR/alarms.sh"
# shellcheck source=lib/s3.sh
source "$LIB_DIR/s3.sh"
# shellcheck source=lib/dashboard.sh
source "$LIB_DIR/dashboard.sh"
# shellcheck source=lib/insights.sh
source "$LIB_DIR/insights.sh"
# shellcheck source=lib/summary.sh
source "$LIB_DIR/summary.sh"

# Main execution
main() {
    log_info "Starting CloudWatch setup for DantePlanner"

    validate_params
    setup_cloudwatch_logs_iam   # must run before awslogs driver can deliver logs

    # setup_sns_topic sets TOPIC_ARN as global variable
    setup_sns_topic || { log_error "SNS setup failed"; exit 1; }

    # Validate TOPIC_ARN before proceeding
    if [[ ! "$TOPIC_ARN" =~ ^arn:aws:sns: ]]; then
        log_error "Invalid TOPIC_ARN: [$TOPIC_ARN]"
        exit 1
    fi

    setup_metric_filter
    setup_error_metric_filters
    setup_heartbeat_filter
    setup_log_retention
    create_alarms "$TOPIC_ARN"
    setup_s3_logging
    setup_dashboard
    setup_insights_queries

    print_summary
}

main "$@"

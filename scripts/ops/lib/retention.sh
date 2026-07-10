# shellcheck shell=bash
# CloudWatch log-group retention policy.

# Set 30-day retention on all /ecs/danteplanner/* log groups (idempotent)
setup_log_retention() {
    log_info "Setting CloudWatch log retention: 30 days"
    local LOG_GROUPS=(
        "/ecs/danteplanner/nginx"
        "/ecs/danteplanner/mysql"
    )
    for LOG_GROUP in "${LOG_GROUPS[@]}"; do
        aws logs put-retention-policy \
            --log-group-name "$LOG_GROUP" \
            --retention-in-days 30 \
            --region "$AWS_REGION" 2>/dev/null || log_warn "Could not set retention on $LOG_GROUP (may not exist yet)"
        log_info "Retention: $LOG_GROUP → 30 days"
    done

    aws logs put-retention-policy \
        --log-group-name "/ecs/danteplanner/backend" \
        --retention-in-days 90 \
        --region "$AWS_REGION" 2>/dev/null || log_warn "Could not set retention on /ecs/danteplanner/backend (may not exist yet)"
    log_info "Retention: /ecs/danteplanner/backend → 90 days"
}

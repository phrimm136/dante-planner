# shellcheck shell=bash
# Log-derived metric filters (nginx access log + backend JSON log).

# Create metric filter for HTTP 5xx errors from nginx logs
setup_metric_filter() {
    log_info "Setting up HTTP 5xx metric filter on nginx logs"

    LOG_GROUP="/ecs/danteplanner/nginx"

    # Check if log group exists
    if ! aws logs describe-log-groups \
        --log-group-name-prefix "$LOG_GROUP" \
        --region "$AWS_REGION" \
        --query "logGroups[?logGroupName=='$LOG_GROUP']" \
        --output text | grep -q "$LOG_GROUP"; then
        log_warn "Log group $LOG_GROUP not found. Will be created on first deploy."
        return 0
    fi

    aws logs put-metric-filter \
        --log-group-name "$LOG_GROUP" \
        --filter-name "HTTP5xxErrors" \
        --filter-pattern '{ $.status > 499 }' \
        --metric-transformations \
            metricName=HTTP5xxCount,metricNamespace=$NAMESPACE,metricValue=1,defaultValue=0 \
        --region "$AWS_REGION"

    # RequestCount — every access-logged request (JSON lines carry $.status).
    # Backs the traffic-drop alarm and the request-rate-vs-memory overlay.
    aws logs put-metric-filter \
        --log-group-name "$LOG_GROUP" \
        --filter-name "RequestCount" \
        --filter-pattern '{ $.status = * }' \
        --metric-transformations \
            metricName=RequestCount,metricNamespace=$NAMESPACE,metricValue=1,defaultValue=0 \
        --region "$AWS_REGION"

    # HTTP429Count — rate-limit rejections (bucket4j). Backs the 429-spike alarm.
    aws logs put-metric-filter \
        --log-group-name "$LOG_GROUP" \
        --filter-name "HTTP429Errors" \
        --filter-pattern '{ $.status = 429 }' \
        --metric-transformations \
            metricName=HTTP429Count,metricNamespace=$NAMESPACE,metricValue=1,defaultValue=0 \
        --region "$AWS_REGION"

    log_info "Metric filters created: HTTP5xxCount, RequestCount, HTTP429Count"
}

# Create metric filters for ERROR and WARN events from JSON backend logs.
# Targets /ecs/danteplanner/backend (LogstashEncoder JSON, INFO+, via awslogs).
# Uses CloudWatch JSON pattern syntax: { $.field = "value" }
setup_error_metric_filters() {
    log_info "Setting up ERROR/WARN metric filters on /ecs/danteplanner/backend"

    local LOG_GROUP="/ecs/danteplanner/backend"

    if ! aws logs describe-log-groups \
        --log-group-name-prefix "$LOG_GROUP" \
        --region "$AWS_REGION" \
        --query "logGroups[?logGroupName=='$LOG_GROUP']" \
        --output text | grep -q "$LOG_GROUP"; then
        log_warn "Log group $LOG_GROUP not found. Will be created on first deploy."
        return 0
    fi

    aws logs put-metric-filter \
        --log-group-name "$LOG_GROUP" \
        --filter-name "BackendErrors" \
        --filter-pattern '{ $.level = "ERROR" }' \
        --metric-transformations \
            metricName=BackendErrorCount,metricNamespace=$NAMESPACE,metricValue=1,defaultValue=0 \
        --region "$AWS_REGION"
    log_info "Metric filter created: BackendErrorCount"

    aws logs put-metric-filter \
        --log-group-name "$LOG_GROUP" \
        --filter-name "BackendWarnings" \
        --filter-pattern '{ $.level = "WARN" }' \
        --metric-transformations \
            metricName=BackendWarnCount,metricNamespace=$NAMESPACE,metricValue=1,defaultValue=0 \
        --region "$AWS_REGION"
    log_info "Metric filter created: BackendWarnCount"
}

# Heartbeat metric filter on JSON log group — counts every log event.
# Used by the BackendSilence alarm: if the server stops logging for 5 minutes,
# treat-missing-data=breaching fires the alarm even with no data points.
setup_heartbeat_filter() {
    log_info "Setting up heartbeat metric filter on /ecs/danteplanner/backend"

    local LOG_GROUP="/ecs/danteplanner/backend"

    if ! aws logs describe-log-groups \
        --log-group-name-prefix "$LOG_GROUP" \
        --region "$AWS_REGION" \
        --query "logGroups[?logGroupName=='$LOG_GROUP']" \
        --output text | grep -q "$LOG_GROUP"; then
        log_warn "Log group $LOG_GROUP not found. Will be created on first deploy."
        return 0
    fi

    # Empty filter-pattern matches every log event
    aws logs put-metric-filter \
        --log-group-name "$LOG_GROUP" \
        --filter-name "BackendHeartbeat" \
        --filter-pattern "" \
        --metric-transformations \
            metricName=BackendLogCount,metricNamespace=$NAMESPACE,metricValue=1,defaultValue=0 \
        --region "$AWS_REGION"

    log_info "Metric filter created: BackendLogCount (heartbeat)"
}

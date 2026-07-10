# shellcheck shell=bash
# CloudWatch Logs Insights saved queries (backend / nginx / mysql).

# Upsert a CloudWatch Insights saved query (create or update).
# Usage: upsert_query "QueryName" "logGroup1 logGroup2" 'query string'
# Looks up existing query by name to get its ID; passes --query-definition-id on update.
upsert_query() {
    local QUERY_NAME="$1"
    local LOG_GROUPS="$2"
    local QUERY_STRING="$3"

    local EXISTING_ID
    EXISTING_ID=$(aws logs describe-query-definitions \
        --query-definition-name-prefix "$QUERY_NAME" \
        --region "$AWS_REGION" \
        --query "queryDefinitions[?name=='$QUERY_NAME'].queryDefinitionId | [0]" \
        --output text 2>/dev/null || echo "None")

    local ID_ARG=""
    if [ -n "$EXISTING_ID" ] && [ "$EXISTING_ID" != "None" ]; then
        ID_ARG="--query-definition-id $EXISTING_ID"
    fi

    aws logs put-query-definition \
        --name "$QUERY_NAME" \
        --log-group-names $LOG_GROUPS \
        --query-string "$QUERY_STRING" \
        --region "$AWS_REGION" \
        $ID_ARG
}

# Create saved CloudWatch Insights queries for JSON backend logs.
# Log group /ecs/danteplanner/backend receives LogstashEncoder JSON via awslogs driver.
# CloudWatch Insights auto-parses top-level JSON fields — no parse() needed.
setup_insights_queries() {
    log_info "Creating CloudWatch Insights saved queries for /ecs/danteplanner/backend"

    local LOG_GROUP="/ecs/danteplanner/backend"

    upsert_query "DantePlanner/Backend/RecentErrors" "$LOG_GROUP" \
        'fields @timestamp, level, message, userId, path, logger_name
| filter level = "ERROR"
| sort @timestamp desc
| limit 100'

    upsert_query "DantePlanner/Backend/ExceptionsWithStackTrace" "$LOG_GROUP" \
        'fields @timestamp, message, stack_trace, userId, path
| filter ispresent(stack_trace)
| sort @timestamp desc
| limit 50'

    upsert_query "DantePlanner/Backend/ErrorsByEndpoint" "$LOG_GROUP" \
        'filter level = "ERROR"
| stats count(*) as errorCount by path
| sort errorCount desc'

    upsert_query "DantePlanner/Backend/RecentWarnings" "$LOG_GROUP" \
        'fields @timestamp, message, userId, path
| filter level = "WARN"
| sort @timestamp desc
| limit 100'

    upsert_query "DantePlanner/Backend/UserActivity" "$LOG_GROUP" \
        'filter ispresent(userId) and userId != "guest"
| stats count(*) as requestCount by userId, path
| sort requestCount desc'

    upsert_query "DantePlanner/Backend/ErrorWarningRateOverTime" "$LOG_GROUP" \
        'filter level = "ERROR" or level = "WARN"
| stats count(*) as total,
        sum(level = "ERROR") as errors,
        sum(level = "WARN") as warnings
    by bin(5m)
| sort @timestamp desc'

    upsert_query "DantePlanner/Backend/AuthSecurityEvents" "$LOG_GROUP" \
        'filter logger_name like /[Ss]ecurity/ or logger_name like /[Aa]uth/ or logger_name like /[Jj]wt/
| fields @timestamp, level, message, userId, path, logger_name
| sort @timestamp desc
| limit 100'

    upsert_query "DantePlanner/Backend/DatabaseErrors" "$LOG_GROUP" \
        'filter logger_name like /hibernate/ or logger_name like /jpa/ or logger_name like /datasource/ or logger_name like /jdbc/
| fields @timestamp, level, message, logger_name
| filter level = "ERROR" or level = "WARN"
| sort @timestamp desc
| limit 100'

    upsert_query "DantePlanner/Backend/LogGapTimeline" "$LOG_GROUP" \
        'stats count(*) as events by bin(1m)
| sort @timestamp asc'

    log_info "Saved 9 Insights queries under DantePlanner/Backend/"

    # ── Nginx (JSON access log, /ecs/danteplanner/nginx) ──────────────────────
    # Mixed log group: JSON access lines + plain-text error lines.
    # ispresent(status) restricts each query to JSON access log events only.
    local NGINX_LOG_GROUP="/ecs/danteplanner/nginx"

    upsert_query "DantePlanner/Nginx/HTTP5xxErrors" "$NGINX_LOG_GROUP" \
        'filter ispresent(status) and status > 499
| fields @timestamp, status, method, uri, remote_addr, upstream_response_time
| sort @timestamp desc
| limit 100'

    upsert_query "DantePlanner/Nginx/SlowestEndpoints" "$NGINX_LOG_GROUP" \
        'filter ispresent(request_time)
| filter uri not like "/events" and uri not like "/api/sse/"
| stats avg(request_time) as avg_ms,
        max(request_time) as max_ms,
        count(*) as requests
    by uri
| sort avg_ms desc
| limit 20'

    upsert_query "DantePlanner/Nginx/TrafficByEndpoint" "$NGINX_LOG_GROUP" \
        'filter ispresent(status)
| stats count(*) as requests, sum(bytes_sent) as total_bytes by uri, method
| sort requests desc
| limit 30'

    upsert_query "DantePlanner/Nginx/StatusCodeDistribution" "$NGINX_LOG_GROUP" \
        'filter ispresent(status)
| stats count(*) as requests by status
| sort status asc'

    log_info "Saved 4 Insights queries under DantePlanner/Nginx/"

    # ── MySQL (plain-text error log, /ecs/danteplanner/mysql) ─────────────────
    # MySQL JSON logging (log_sink_json) is not used: the component writes to
    # a hostname-named file, not stderr, so it does not reach the awslogs driver.
    local MYSQL_LOG_GROUP="/ecs/danteplanner/mysql"

    upsert_query "DantePlanner/MySQL/Errors" "$MYSQL_LOG_GROUP" \
        'filter @message like /\[ERROR\]/
| fields @timestamp, @message
| sort @timestamp desc
| limit 100'

    upsert_query "DantePlanner/MySQL/Warnings" "$MYSQL_LOG_GROUP" \
        'filter @message like /\[Warning\]/
| fields @timestamp, @message
| sort @timestamp desc
| limit 100'

    log_info "Saved 2 Insights queries under DantePlanner/MySQL/"
}

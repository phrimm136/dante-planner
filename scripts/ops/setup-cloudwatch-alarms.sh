#!/bin/bash
# CloudWatch Alarms and SNS Setup for DantePlanner
# Idempotent - safe to run multiple times
set -euo pipefail

# Configuration
AWS_REGION="${AWS_REGION:-us-west-2}"
INSTANCE_ID="${EC2_INSTANCE_ID:-}"
ALERT_EMAIL="${ALERT_EMAIL:-}"
S3_BACKUP_BUCKET="${S3_BACKUP_BUCKET:-danteplanner-backups}"
S3_LOGS_BUCKET="${S3_LOGS_BUCKET:-danteplanner-logs}"

# Namespace matches cloudwatch-agent-config.json
NAMESPACE="DantePlanner"
SNS_TOPIC_NAME="danteplanner-alerts"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Validate required parameters
validate_params() {
    if [ -z "$INSTANCE_ID" ]; then
        log_error "EC2_INSTANCE_ID is required"
        exit 1
    fi
    log_info "Instance ID: $INSTANCE_ID"
    log_info "Region: $AWS_REGION"
    log_info "Alert Email: ${ALERT_EMAIL:-'(not configured - no email notifications)'}"
}

# TODO(human): Configure alarm thresholds
# These thresholds determine when you get alerted. Consider:
# - CPU: t3.small can burst to 20% baseline, 70% is conservative
# - Memory: 2GB total, 200MB free is tight but catches OOM early
# - Disk: 85% leaves buffer for logs/temp files
# - 5xx errors: 10 in 5min might be too sensitive for low-traffic periods
get_alarm_thresholds() {
    # Return values: CPU_THRESHOLD MEM_THRESHOLD_BYTES DISK_THRESHOLD HTTP5XX_THRESHOLD
    # Implement your threshold logic here
    echo "70 209715200 85 10"
}

# Ensure EC2 instance role has CloudWatch Logs write permissions for awslogs driver.
# Without this, the Docker daemon silently drops container logs — no error surfaced.
setup_cloudwatch_logs_iam() {
    log_info "Configuring IAM permissions for CloudWatch Logs (awslogs driver)"

    ROLE_NAME=$(aws ec2 describe-instances \
        --instance-ids "$INSTANCE_ID" \
        --region "$AWS_REGION" \
        --query 'Reservations[0].Instances[0].IamInstanceProfile.Arn' \
        --output text 2>/dev/null | sed 's|.*/||' | sed 's/-instance-profile$//' || true)

    if [ -z "$ROLE_NAME" ] || [ "$ROLE_NAME" = "None" ]; then
        log_error "No IAM role attached to instance $INSTANCE_ID"
        log_error "Attach an IAM role with CloudWatch Logs permissions, then re-run"
        exit 1
    fi

    log_info "Instance role: $ROLE_NAME"

    # CloudWatchAgentServerPolicy covers both awslogs driver and CloudWatch Agent metrics
    aws iam attach-role-policy \
        --role-name "$ROLE_NAME" \
        --policy-arn "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy" \
        --region "$AWS_REGION" 2>/dev/null \
        && log_info "CloudWatchAgentServerPolicy attached" \
        || log_info "CloudWatchAgentServerPolicy already attached (idempotent)"
}

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

# Create SNS topic (idempotent)
setup_sns_topic() {
    log_info "Setting up SNS topic: $SNS_TOPIC_NAME"

    TOPIC_ARN=$(aws sns create-topic \
        --name "$SNS_TOPIC_NAME" \
        --region "$AWS_REGION" \
        --query 'TopicArn' --output text 2>/dev/null) || true

    # Validate ARN format (must start with arn:aws:sns)
    if [[ ! "$TOPIC_ARN" =~ ^arn:aws:sns: ]]; then
        log_warn "Failed to create SNS topic. Checking if it already exists..."

        # Try to get existing topic ARN
        ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
        TOPIC_ARN="arn:aws:sns:${AWS_REGION}:${ACCOUNT_ID}:${SNS_TOPIC_NAME}"

        # Verify topic exists
        if ! aws sns get-topic-attributes --topic-arn "$TOPIC_ARN" --region "$AWS_REGION" &>/dev/null; then
            log_error "SNS topic does not exist and cannot be created. Add SNS permissions to EC2 role."
            log_error "Run: aws sns create-topic --name $SNS_TOPIC_NAME --region $AWS_REGION"
            return 1
        fi

        log_info "Using existing SNS topic: $TOPIC_ARN"
    else
        log_info "SNS Topic ARN: $TOPIC_ARN"
    fi

    # Sanitize TOPIC_ARN - remove any whitespace/newlines
    TOPIC_ARN=$(echo "$TOPIC_ARN" | tr -d '[:space:]')

    # Subscribe email if provided (requires manual confirmation)
    if [ -n "$ALERT_EMAIL" ]; then
        # Check if subscription already exists
        EXISTING=$(aws sns list-subscriptions-by-topic \
            --topic-arn "$TOPIC_ARN" \
            --region "$AWS_REGION" \
            --query "Subscriptions[?Endpoint=='$ALERT_EMAIL'].SubscriptionArn" \
            --output text)

        if [ -z "$EXISTING" ] || [ "$EXISTING" = "None" ] || [ "$EXISTING" = "PendingConfirmation" ]; then
            aws sns subscribe \
                --topic-arn "$TOPIC_ARN" \
                --protocol email \
                --notification-endpoint "$ALERT_EMAIL" \
                --region "$AWS_REGION"
            log_warn "Email subscription created. Check inbox for confirmation link."
        else
            log_info "Email subscription already exists: $EXISTING"
        fi
    fi

    echo "$TOPIC_ARN"
}

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

    log_info "Metric filter created for HTTP 5xx errors"
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

# Create CloudWatch alarms
create_alarms() {
    local TOPIC_ARN="$1"

    # Get thresholds from configuration function
    read CPU_THRESHOLD MEM_THRESHOLD DISK_THRESHOLD HTTP5XX_THRESHOLD <<< $(get_alarm_thresholds)

    log_info "Creating alarms with thresholds: CPU>${CPU_THRESHOLD}%, Mem<${MEM_THRESHOLD}B, Disk>${DISK_THRESHOLD}%, 5xx>${HTTP5XX_THRESHOLD}"

    # Alarm 1: High CPU Usage
    log_info "Creating High CPU alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighCPU" \
        --alarm-description "CPU usage exceeds ${CPU_THRESHOLD}% for 5 minutes" \
        --namespace "$NAMESPACE" \
        --metric-name "cpu_usage_user" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" \
        --statistic Average \
        --period 300 \
        --threshold "$CPU_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 2: Low Memory
    log_info "Creating Low Memory alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-LowMemory" \
        --alarm-description "Available memory below 200MB" \
        --namespace "$NAMESPACE" \
        --metric-name "mem_available" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" \
        --statistic Average \
        --period 300 \
        --threshold "$MEM_THRESHOLD" \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 3: Disk Usage High
    log_info "Creating High Disk Usage alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighDisk" \
        --alarm-description "Disk usage exceeds ${DISK_THRESHOLD}%" \
        --namespace "$NAMESPACE" \
        --metric-name "disk_used_percent" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=path,Value="/" Name=fstype,Value="xfs" \
        --statistic Average \
        --period 300 \
        --threshold "$DISK_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 4: HTTP 5xx Errors
    log_info "Creating HTTP 5xx alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HTTP5xx" \
        --alarm-description "More than ${HTTP5XX_THRESHOLD} HTTP 5xx errors in 5 minutes" \
        --namespace "$NAMESPACE" \
        --metric-name "HTTP5xxCount" \
        --statistic Sum \
        --period 300 \
        --threshold "$HTTP5XX_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 5: Any Backend Error — fires immediately on a single ERROR event
    log_info "Creating backend error alarm (immediate)..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-BackendErrors" \
        --alarm-description "Any backend application ERROR event" \
        --namespace "$NAMESPACE" \
        --metric-name "BackendErrorCount" \
        --statistic Sum \
        --period 60 \
        --threshold 0 \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --datapoints-to-alarm 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 6: Successive Backend Warnings — fires when WARNs appear in 3 of 5
    # consecutive 1-minute windows, avoiding noise from isolated warnings.
    log_info "Creating successive backend warnings alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-BackendWarnings" \
        --alarm-description "Backend WARN events in 3 of the last 5 one-minute periods" \
        --namespace "$NAMESPACE" \
        --metric-name "BackendWarnCount" \
        --statistic Sum \
        --period 60 \
        --threshold 0 \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 5 \
        --datapoints-to-alarm 3 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 7: Backend Silence — fires when server produces no logs for 5 minutes.
    # treat-missing-data=breaching means a CW Agent outage also triggers this alarm,
    # which is correct: both cases (app crash and agent failure) need attention.
    log_info "Creating backend silence alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-BackendSilence" \
        --alarm-description "No backend log events for 5 minutes — possible crash or CW Agent failure" \
        --namespace "$NAMESPACE" \
        --metric-name "BackendLogCount" \
        --statistic Sum \
        --period 300 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    log_info "All alarms created successfully"
}

# Enable S3 access logging
setup_s3_logging() {
    log_info "Setting up S3 access logging for $S3_BACKUP_BUCKET"

    # Check if logs bucket exists, create if not
    if ! aws s3api head-bucket --bucket "$S3_LOGS_BUCKET" 2>/dev/null; then
        log_info "Creating logs bucket: $S3_LOGS_BUCKET"
        aws s3 mb "s3://$S3_LOGS_BUCKET" --region "$AWS_REGION"
    fi

    # Get AWS account ID for bucket policy
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

    # Set bucket policy for S3 log delivery
    log_info "Setting bucket policy for log delivery..."
    aws s3api put-bucket-policy --bucket "$S3_LOGS_BUCKET" --policy "{
        \"Version\": \"2012-10-17\",
        \"Statement\": [
            {
                \"Effect\": \"Allow\",
                \"Principal\": {\"Service\": \"logging.s3.amazonaws.com\"},
                \"Action\": \"s3:PutObject\",
                \"Resource\": \"arn:aws:s3:::$S3_LOGS_BUCKET/*\",
                \"Condition\": {
                    \"StringEquals\": {\"aws:SourceAccount\": \"$ACCOUNT_ID\"},
                    \"ArnLike\": {\"aws:SourceArn\": \"arn:aws:s3:::$S3_BACKUP_BUCKET\"}
                }
            }
        ]
    }"

    # Enable logging on backup bucket
    log_info "Enabling access logging on $S3_BACKUP_BUCKET..."
    aws s3api put-bucket-logging --bucket "$S3_BACKUP_BUCKET" --bucket-logging-status "{
        \"LoggingEnabled\": {
            \"TargetBucket\": \"$S3_LOGS_BUCKET\",
            \"TargetPrefix\": \"s3-access-logs/$S3_BACKUP_BUCKET/\"
        }
    }"

    log_info "S3 access logging enabled"
}

# Create CloudWatch Dashboard with metric and log insights widgets
# Layout (24-col grid):
#   Row 1 (y=0, h=6): CPU & Memory (w=12) | Error & Warning Rates (w=12)
#   Row 2 (y=6, h=4): Alarm Status bar     (w=24)
#   Row 3 (y=10,h=12): Log Insights — recent ERROR/WARN entries (w=24)
setup_dashboard() {
    log_info "Creating CloudWatch dashboard: DantePlanner"

    local ACCOUNT_ID
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

    aws cloudwatch put-dashboard \
        --dashboard-name "DantePlanner" \
        --region "$AWS_REGION" \
        --dashboard-body "$(cat <<EOF
{
  "widgets": [
    {
      "type": "metric",
      "x": 0, "y": 0, "width": 12, "height": 6,
      "properties": {
        "title": "CPU & Memory",
        "metrics": [
          ["$NAMESPACE", "cpu_usage_user",   "InstanceId", "$INSTANCE_ID"],
          ["$NAMESPACE", "mem_used_percent", "InstanceId", "$INSTANCE_ID"]
        ],
        "period": 300,
        "stat": "Average",
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "max": 100 } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 0, "width": 12, "height": 6,
      "properties": {
        "title": "Error & Warning Rates",
        "metrics": [
          ["$NAMESPACE", "BackendErrorCount", { "color": "#d62728", "label": "Errors" }],
          ["$NAMESPACE", "BackendWarnCount",  { "color": "#ff7f0e", "label": "Warnings" }],
          ["$NAMESPACE", "HTTP5xxCount",      { "color": "#9467bd", "label": "HTTP 5xx" }]
        ],
        "period": 60,
        "stat": "Sum",
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0 } }
      }
    },
    {
      "type": "alarm",
      "x": 0, "y": 6, "width": 24, "height": 4,
      "properties": {
        "title": "Alarm Status",
        "alarms": [
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighCPU",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-LowMemory",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighDisk",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HTTP5xx",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-BackendErrors",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-BackendWarnings",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-BackendSilence"
        ]
      }
    },
    {
      "type": "log",
      "x": 0, "y": 10, "width": 24, "height": 12,
      "properties": {
        "title": "Recent Backend Errors & Warnings",
        "query": "SOURCE '/ecs/danteplanner/backend' | fields @timestamp, level, message, userId, method, path | filter level = \"ERROR\" or level = \"WARN\" | sort @timestamp desc | limit 50",
        "region": "$AWS_REGION",
        "view": "table"
      }
    }
  ]
}
EOF
)"

    log_info "Dashboard created: DantePlanner"
    log_info "View: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner"
}

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

# Print summary
print_summary() {
    echo ""
    echo "=========================================="
    echo "  CloudWatch Setup Complete"
    echo "=========================================="
    echo ""
    echo "Created Resources:"
    echo "  - SNS Topic: $SNS_TOPIC_NAME"
    echo "  - Alarms: HighCPU, LowMemory, HighDisk, HTTP5xx, BackendErrors, BackendWarnings, BackendSilence"
    echo "  - Metric Filters: HTTP5xxErrors (nginx), BackendErrors, BackendWarnings, BackendHeartbeat (backend)"
    echo "  - Dashboard: DantePlanner (metrics + 7 alarms + log insights from /ecs/danteplanner/backend)"
    echo "  - Insights Queries (15): 9 backend, 4 nginx, 2 mysql"
    echo "  - S3 Logging: $S3_BACKUP_BUCKET -> $S3_LOGS_BUCKET"
    echo "  - IAM: CloudWatchAgentServerPolicy attached to instance role"
    echo "  - Log Retention: backend 90 days, nginx/mysql 30 days"
    echo ""
    echo "View in AWS Console:"
    echo "  Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner"
    echo "  Alarms:    https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#alarmsV2:"
    echo ""
    if [ -n "$ALERT_EMAIL" ]; then
        echo "IMPORTANT: Confirm email subscription in your inbox"
    fi
}

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

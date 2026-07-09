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

# Alarm thresholds — these determine when you get alerted. Consider:
# - CPU: t3.small can burst to 20% baseline, 70% is conservative
# - Memory: 2GB total, 200MB free is tight but catches OOM early
# - Disk: 85% leaves buffer for logs/temp files
# - 5xx errors: 10 in 5min might be too sensitive for low-traffic periods
get_alarm_thresholds() {
    # CPU% MEM_BYTES DISK% HTTP5xx NET_IN_BYTES NET_OUT_BYTES DISKIO_WRITE_BYTES
    # Network/diskio thresholds are 5-minute Sum totals.
    # Tuned for t3.medium serving JSON API only (static assets on Cloudflare),
    # ~500 DAU / 100 peak concurrent. Revisit after observing baselines.
    echo "70 209715200 85 10 52428800 209715200 524288000"
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

# Create CloudWatch alarms
create_alarms() {
    local TOPIC_ARN="$1"

    # Get thresholds from configuration function
    read CPU_THRESHOLD MEM_THRESHOLD DISK_THRESHOLD HTTP5XX_THRESHOLD NET_IN_THRESHOLD NET_OUT_THRESHOLD DISKIO_WRITE_THRESHOLD <<< $(get_alarm_thresholds)

    log_info "Creating alarms with thresholds: CPU>${CPU_THRESHOLD}%, Mem<${MEM_THRESHOLD}B, Disk>${DISK_THRESHOLD}%, NetIn>${NET_IN_THRESHOLD}B/5m, NetOut>${NET_OUT_THRESHOLD}B/5m, DiskIOWrite>${DISKIO_WRITE_THRESHOLD}B/5m, 5xx>${HTTP5XX_THRESHOLD}"

    # Alarm 1: High CPU Usage
    # cpu=cpu-total dimension required — CW Agent publishes aggregated series under this key when totalcpu: true
    log_info "Creating High CPU alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighCPU" \
        --alarm-description "CPU > ${CPU_THRESHOLD}% for 5 min. Check per-process CPU (java/mysqld) and Request Rate for a traffic spike or runaway loop. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "cpu_usage_user" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=cpu,Value=cpu-total \
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
    # 3-of-3 (15 min sustained) to stop threshold flapping — mem_available oscillates
    # right at the 200 MiB line, so a 1-of-1 evaluation pages every few minutes.
    log_info "Creating Low Memory alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-LowMemory" \
        --alarm-description "mem_available < 200 MiB sustained 15 min (3x5m). Likely the traffic-correlated JVM off-heap growth — check Per-Process RSS + JVM Heap panels and correlate with Request Rate. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "mem_available" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" \
        --statistic Average \
        --period 300 \
        --threshold "$MEM_THRESHOLD" \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 3 \
        --datapoints-to-alarm 3 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 3: Disk Usage High
    log_info "Creating High Disk Usage alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighDisk" \
        --alarm-description "Disk > ${DISK_THRESHOLD}%. Likely log/table growth (MySQL slow_log TABLE, mysql-data volume) - check the disk trend and prune if needed. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
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

    # Alarm 4: High Network In — catches scraping / brute force against API
    log_info "Creating High Network In alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighNetworkIn" \
        --alarm-description "Inbound > ${NET_IN_THRESHOLD}B over 5 min - possible scraping or brute-force against the API. Check Request Rate + HTTP Status Distribution. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "net_bytes_recv" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=interface,Value=ens5 \
        --statistic Sum \
        --period 300 \
        --threshold "$NET_IN_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 5: High Network Out — catches data exfiltration / runaway API responses
    log_info "Creating High Network Out alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighNetworkOut" \
        --alarm-description "Outbound > ${NET_OUT_THRESHOLD}B over 5 min - possible data exfiltration or runaway response sizes. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "net_bytes_sent" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=interface,Value=ens5 \
        --statistic Sum \
        --period 300 \
        --threshold "$NET_OUT_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 6: High Disk IO Write — catches log flooding or runaway DB writes
    # name=nvme0n1 is the default EBS root device on t3.medium; update if the instance has more volumes
    log_info "Creating High Disk IO Write alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighDiskIOWrite" \
        --alarm-description "Disk writes > ${DISKIO_WRITE_THRESHOLD}B over 5 min - possible log flooding or runaway DB writes. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "diskio_write_bytes" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=name,Value=nvme0n1 \
        --statistic Sum \
        --period 300 \
        --threshold "$DISKIO_WRITE_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 7: HTTP 5xx Errors
    log_info "Creating HTTP 5xx alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HTTP5xx" \
        --alarm-description "More than ${HTTP5XX_THRESHOLD} HTTP 5xx in 5 min. Check Recent Backend Errors + Slowest Endpoints. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
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

    # Alarm 8: Any Backend Error — fires immediately on a single ERROR event
    log_info "Creating backend error alarm (immediate)..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-BackendErrors" \
        --alarm-description "A backend ERROR was logged. See the Recent Backend Errors panel - logger_name pinpoints the source class. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
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

    # Alarm 9: Successive Backend Warnings — fires when WARNs appear in 3 of 5
    # consecutive 1-minute windows, avoiding noise from isolated warnings.
    log_info "Creating successive backend warnings alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-BackendWarnings" \
        --alarm-description "Backend WARNs in 3 of the last 5 minutes. Check the Recent Backend Errors panel. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
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

    # Alarm 10: Backend Silence — fires when server produces no logs for 5 minutes.
    # treat-missing-data=breaching means a CW Agent outage also triggers this alarm,
    # which is correct: both cases (app crash and agent failure) need attention.
    log_info "Creating backend silence alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-BackendSilence" \
        --alarm-description "No backend logs for 5 min - app crash or CW Agent failure (backend runs restart:'no', so a crash is a hard-down). Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
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

    # Alarm 11: MySQL Backup Silence — daily S3 dump must emit BackupSuccess once per 24h.
    # breaching = missing datapoint treated as alarm, so a failed/absent cron fires immediately.
    log_info "Creating MySQL backup silence alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-MysqlBackupSilence" \
        --alarm-description "No successful MySQL backup in 24h - the daily S3 dump cron failed or did not run. Check the /danteplanner/backup log group. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "BackupSuccess" \
        --dimensions Name=Job,Value=MysqlDump \
        --statistic Sum \
        --period 86400 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 12: CloudWatch Log Export Silence — daily CW→S3 export heartbeat.
    log_info "Creating CloudWatch export silence alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-CwExportSilence" \
        --alarm-description "No successful CloudWatch to S3 log export in 24h - check the export cron (exec-bit fixed; verify IAM/S3 next run). Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "BackupSuccess" \
        --dimensions Name=Job,Value=CwLogExport \
        --statistic Sum \
        --period 86400 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 13: HTTP 429 spike — bucket4j rate limiting may be blocking legitimate users.
    log_info "Creating HTTP 429 spike alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HTTP429" \
        --alarm-description "More than 20 HTTP 429s in 5 min — bucket4j rate limiting may be blocking legitimate users (watch the SSE reconnect-on-refresh case). Check HTTP Status Distribution panel. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "HTTP429Count" \
        --statistic Sum \
        --period 300 \
        --threshold 20 \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 14: Traffic drop — fewer than 1 request in 5 min for 2 periods. This app has
    # 24/7 global traffic, so a sustained zero means an outage. Complements BackendSilence.
    log_info "Creating traffic-drop alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-TrafficDrop" \
        --alarm-description "Fewer than 1 request in 5 min for 2 consecutive periods — probable outage or upstream/DNS break. Complements BackendSilence. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "RequestCount" \
        --statistic Sum \
        --period 300 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 2 \
        --datapoints-to-alarm 2 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 15: Cloudflare IP Update Silence — daily SG allowlist refresh heartbeat.
    # breaching = a missing/failed run fires, matching the other cron-silence alarms.
    log_info "Creating Cloudflare IP update silence alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-CloudflareIpSilence" \
        --alarm-description "No successful Cloudflare IP allowlist update in the last 24 hours — the security-group ingress rules may be stale, so origin access could break or over-expose. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "BackupSuccess" \
        --dimensions Name=Job,Value=CloudflareIpUpdate \
        --statistic Sum \
        --period 86400 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarms 16-17 (JVM heap > 90% of max, HikariCP pending sustained) land with the
    # backend Prometheus scrape — their metric names are fixed by the scrape's EMF config.
    # Added there so they aren't created in INSUFFICIENT_DATA against non-existent metrics.

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

# Create CloudWatch Dashboard, organized by concern (24-col grid).
# Sections are text-widget headers; SEARCH expressions auto-populate widgets for
# collectors that deploy later (procstat) without needing a dashboard edit.
#   System / Host        : CPU/disk/net, memory-vs-alarm, per-process RSS & threads
#   Latency / API        : per-endpoint p50/p90/p99 (nginx logs), HTTP 5xx
#   Database             : mysqld memory (procstat), Hikari pool + query digests
#                          (prometheus scrape + emit-mysql-query-stats.sh cron)
#   Spring / Application : error+warn rates, JVM threads, recent error log
#   Alarms               : alarm status strip
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
      "type": "text",
      "x": 0, "y": 0, "width": 24, "height": 1,
      "properties": { "markdown": "# System / Host — CPU, memory, disk, network, per-process" }
    },
    {
      "type": "metric",
      "x": 0, "y": 1, "width": 12, "height": 6,
      "properties": {
        "title": "CPU / Disk / Network",
        "metrics": [
          ["$NAMESPACE", "cpu_usage_user",    "InstanceId", "$INSTANCE_ID", "cpu", "cpu-total",          { "label": "CPU %",  "yAxis": "left",  "stat": "Average" }],
          ["$NAMESPACE", "disk_used_percent", "InstanceId", "$INSTANCE_ID", "path", "/", "fstype", "xfs", { "label": "Disk %", "yAxis": "left",  "stat": "Average" }],
          ["$NAMESPACE", "net_bytes_recv",    "InstanceId", "$INSTANCE_ID", "interface", "ens5",          { "label": "Net In (B/5m)",  "yAxis": "right", "stat": "Sum" }],
          ["$NAMESPACE", "net_bytes_sent",    "InstanceId", "$INSTANCE_ID", "interface", "ens5",          { "label": "Net Out (B/5m)", "yAxis": "right", "stat": "Sum" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": {
          "left":  { "min": 0, "max": 100, "label": "Percent" },
          "right": { "min": 0, "label": "Bytes/5m" }
        }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 1, "width": 12, "height": 6,
      "properties": {
        "title": "Memory Available vs Low-Memory Alarm",
        "metrics": [
          ["$NAMESPACE", "mem_available", "InstanceId", "$INSTANCE_ID", { "label": "Available", "stat": "Minimum", "color": "#1f77b4" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes" } },
        "annotations": { "horizontal": [ { "label": "LowMemory alarm (200 MiB)", "value": 209715200, "color": "#d62728" } ] }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 7, "width": 12, "height": 6,
      "properties": {
        "title": "Per-Process Memory (RSS)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"procstat_memory_rss\"', 'Average')", "label": "", "id": "rss" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 7, "width": 12, "height": 6,
      "properties": {
        "title": "Per-Process Threads",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"procstat_num_threads\"', 'Average')", "label": "", "id": "thr" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Threads" } }
      }
    },
    {
      "type": "text",
      "x": 0, "y": 13, "width": 24, "height": 1,
      "properties": { "markdown": "# Latency / API — response times, throughput, HTTP status" }
    },
    {
      "type": "log",
      "x": 0, "y": 14, "width": 16, "height": 7,
      "properties": {
        "title": "Slowest Endpoints — p50 / p90 / p99 (nginx request_time, SSE excluded)",
        "query": "SOURCE '/ecs/danteplanner/nginx' | filter ispresent(request_time) | filter uri not like \"/events\" and uri not like \"/api/sse/\" | stats pct(request_time, 50) as p50, pct(request_time, 90) as p90, pct(request_time, 99) as p99, count(*) as reqs by uri | sort p99 desc | limit 20",
        "region": "$AWS_REGION",
        "view": "table"
      }
    },
    {
      "type": "metric",
      "x": 16, "y": 14, "width": 8, "height": 7,
      "properties": {
        "title": "HTTP 5xx Rate",
        "metrics": [
          ["$NAMESPACE", "HTTP5xxCount", { "color": "#9467bd", "label": "HTTP 5xx" }]
        ],
        "period": 60,
        "stat": "Sum",
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0 } }
      }
    },
    {
      "type": "log",
      "x": 0, "y": 21, "width": 12, "height": 6,
      "properties": {
        "title": "Request Rate (req / min)",
        "query": "SOURCE '/ecs/danteplanner/nginx' | stats count(*) as requests by bin(1m)",
        "region": "$AWS_REGION",
        "view": "timeSeries"
      }
    },
    {
      "type": "log",
      "x": 12, "y": 21, "width": 12, "height": 6,
      "properties": {
        "title": "HTTP Status Distribution",
        "query": "SOURCE '/ecs/danteplanner/nginx' | stats count(*) as n by bin(5m), status",
        "region": "$AWS_REGION",
        "view": "timeSeries"
      }
    },
    {
      "type": "text",
      "x": 0, "y": 27, "width": 24, "height": 1,
      "properties": { "markdown": "# Database — MySQL memory, connection pool & query performance" }
    },
    {
      "type": "metric",
      "x": 0, "y": 28, "width": 12, "height": 6,
      "properties": {
        "title": "MySQL Process Memory (RSS)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"procstat_memory_rss\" exe=\"mysqld\"', 'Average')", "label": "mysqld", "id": "dbrss" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 28, "width": 12, "height": 6,
      "properties": {
        "title": "Connection Pool (HikariCP)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"hikaricp_connections_active\"', 'Average')", "label": "active", "id": "pa" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"hikaricp_connections_pending\"', 'Average')", "label": "pending", "id": "pp" } ]
        ],
        "period": 60,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Connections" } }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 34, "width": 24, "height": 6,
      "properties": {
        "title": "Query Latency & Lock Time by Statement (perf_schema digests)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_query_latency_avg_ms\"', 'Average')", "label": "", "id": "ql" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_query_lock_avg_ms\"', 'Average')", "label": "", "id": "qlk", "yAxis": "right" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "latency ms" }, "right": { "min": 0, "label": "lock ms" } }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 40, "width": 12, "height": 6,
      "properties": {
        "title": "InnoDB Lock Contention",
        "metrics": [
          [ { "expression": "RATE(SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_innodb_deadlocks\"', 'Maximum'))*300", "label": "deadlocks /5m", "id": "dl" } ],
          [ { "expression": "RATE(SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_innodb_lock_timeouts\"', 'Maximum'))*300", "label": "lock timeouts /5m", "id": "lt" } ],
          [ { "expression": "RATE(SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_innodb_row_lock_waits\"', 'Maximum'))*300", "label": "row-lock waits /5m", "id": "rlw" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_innodb_row_lock_time_avg_ms\"', 'Average')", "label": "row-lock time avg (ms)", "id": "rlt", "yAxis": "right" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "events / 5m" }, "right": { "min": 0, "label": "ms" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 40, "width": 12, "height": 6,
      "properties": {
        "title": "Transactions & Blocking",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_blocked_sessions\"', 'Maximum')", "label": "blocked sessions", "id": "bs" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_active_transactions\"', 'Maximum')", "label": "active trx", "id": "at" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_threads_running\"', 'Maximum')", "label": "threads running", "id": "trn" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_longest_transaction_s\"', 'Maximum')", "label": "longest trx (s)", "id": "ltx", "yAxis": "right" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Count" }, "right": { "min": 0, "label": "Seconds" } }
      }
    },
    {
      "type": "text",
      "x": 0, "y": 46, "width": 24, "height": 1,
      "properties": { "markdown": "# Spring / Application — errors, JVM, throughput" }
    },
    {
      "type": "metric",
      "x": 0, "y": 47, "width": 12, "height": 6,
      "properties": {
        "title": "Error & Warning Rates",
        "metrics": [
          ["$NAMESPACE", "BackendErrorCount", { "color": "#d62728", "label": "Errors" }],
          ["$NAMESPACE", "BackendWarnCount",  { "color": "#ff7f0e", "label": "Warnings" }]
        ],
        "period": 60,
        "stat": "Sum",
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0 } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 47, "width": 12, "height": 6,
      "properties": {
        "title": "JVM Heap Memory (used / committed / max)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"jvm_memory_used_bytes\" area=\"heap\"', 'Average')", "label": "used", "id": "hu" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"jvm_memory_committed_bytes\" area=\"heap\"', 'Average')", "label": "committed", "id": "hc" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"jvm_memory_max_bytes\" area=\"heap\"', 'Average')", "label": "max", "id": "hm" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes" } }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 53, "width": 12, "height": 6,
      "properties": {
        "title": "JVM GC Pause",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"jvm_gc_pause_seconds_max\"', 'Average')", "label": "", "id": "gc" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Seconds" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 53, "width": 12, "height": 6,
      "properties": {
        "title": "Backend JVM Threads",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"procstat_num_threads\" exe=\"java\"', 'Average')", "label": "java", "id": "jvmthr" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Threads" } }
      }
    },
    {
      "type": "log",
      "x": 0, "y": 59, "width": 24, "height": 8,
      "properties": {
        "title": "Recent Backend Errors & Warnings",
        "query": "SOURCE '/ecs/danteplanner/backend' | fields @timestamp, level, logger_name, message, method, path, userId, thread_name | filter level = \"ERROR\" or level = \"WARN\" | sort @timestamp desc | limit 50",
        "region": "$AWS_REGION",
        "view": "table"
      }
    },
    {
      "type": "text",
      "x": 0, "y": 67, "width": 24, "height": 1,
      "properties": { "markdown": "# Alarms" }
    },
    {
      "type": "alarm",
      "x": 0, "y": 68, "width": 24, "height": 4,
      "properties": {
        "title": "Alarm Status",
        "alarms": [
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighCPU",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-LowMemory",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighDisk",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighNetworkIn",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighNetworkOut",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighDiskIOWrite",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HTTP5xx",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-BackendErrors",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-BackendWarnings",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-BackendSilence",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HTTP429",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-TrafficDrop",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-CloudflareIpSilence"
        ]
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

# Print summary
print_summary() {
    echo ""
    echo "=========================================="
    echo "  CloudWatch Setup Complete"
    echo "=========================================="
    echo ""
    echo "Created Resources:"
    echo "  - SNS Topic: $SNS_TOPIC_NAME"
    echo "  - Alarms: HighCPU, LowMemory, HighDisk, HighNetworkIn, HighNetworkOut, HighDiskIOWrite, HTTP5xx, BackendErrors, BackendWarnings, BackendSilence, MysqlBackupSilence, CwExportSilence"
    echo "  - Metric Filters: HTTP5xxErrors (nginx), BackendErrors, BackendWarnings, BackendHeartbeat (backend)"
    echo "  - Dashboard: DantePlanner (System Resources widget + 10 alarms + log insights from /ecs/danteplanner/backend)"
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

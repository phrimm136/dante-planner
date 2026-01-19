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
        --filter-pattern '[ip, id, user, timestamp, request, status=5*, size, ...]' \
        --metric-transformations \
            metricName=HTTP5xxCount,metricNamespace=$NAMESPACE,metricValue=1,defaultValue=0 \
        --region "$AWS_REGION"

    log_info "Metric filter created for HTTP 5xx errors"
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

# Print summary
print_summary() {
    echo ""
    echo "=========================================="
    echo "  CloudWatch Setup Complete"
    echo "=========================================="
    echo ""
    echo "Created Resources:"
    echo "  - SNS Topic: $SNS_TOPIC_NAME"
    echo "  - Alarms: HighCPU, LowMemory, HighDisk, HTTP5xx"
    echo "  - Metric Filter: HTTP5xxErrors on nginx logs"
    echo "  - S3 Logging: $S3_BACKUP_BUCKET -> $S3_LOGS_BUCKET"
    echo ""
    echo "View in AWS Console:"
    echo "  https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#alarmsV2:"
    echo ""
    if [ -n "$ALERT_EMAIL" ]; then
        echo "IMPORTANT: Confirm email subscription in your inbox"
    fi
}

# Main execution
main() {
    log_info "Starting CloudWatch setup for DantePlanner"

    validate_params

    # setup_sns_topic sets TOPIC_ARN as global variable
    setup_sns_topic || { log_error "SNS setup failed"; exit 1; }

    # Validate TOPIC_ARN before proceeding
    if [[ ! "$TOPIC_ARN" =~ ^arn:aws:sns: ]]; then
        log_error "Invalid TOPIC_ARN: [$TOPIC_ARN]"
        exit 1
    fi

    setup_metric_filter
    create_alarms "$TOPIC_ARN"
    setup_s3_logging

    print_summary
}

main "$@"

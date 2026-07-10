# shellcheck shell=bash
# Shared configuration, colors, logging helpers, and param validation.
# Sourced first by setup-cloudwatch-alarms.sh; every other lib depends on these.

# Configuration
AWS_REGION="${AWS_REGION:-us-west-2}"
INSTANCE_ID="${EC2_INSTANCE_ID:-}"
ALERT_EMAIL="${ALERT_EMAIL:-}"
S3_BACKUP_BUCKET="${S3_BACKUP_BUCKET:-danteplanner-backups}"
S3_LOGS_BUCKET="${S3_LOGS_BUCKET:-danteplanner-logs}"

# Namespace matches cloudwatch-agent-config.json
NAMESPACE="DantePlanner"
SNS_TOPIC_NAME="danteplanner-alerts"

# RDS instance identifier (AWS/RDS namespace dimension). Matches terraform
# aws_db_instance.this.identifier = "${name_prefix}-mysql".
RDS_INSTANCE_ID="${RDS_INSTANCE_ID:-danteplanner-mysql}"

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

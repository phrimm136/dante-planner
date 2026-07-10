# shellcheck shell=bash
# S3 access logging for the backup bucket.

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

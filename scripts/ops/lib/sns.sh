# shellcheck shell=bash
# SNS alert topic + email subscription. Sets the global TOPIC_ARN consumed by main().

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

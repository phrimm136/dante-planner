# shellcheck shell=bash
# IAM setup for the awslogs driver + CloudWatch Agent.

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

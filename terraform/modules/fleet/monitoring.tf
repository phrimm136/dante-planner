# --- Billing alarm (~$200) --------------------------------------------------
# EstimatedCharges is published only in us-east-1, so this alarm uses the
# us_east_1 provider alias regardless of the fleet region.
resource "aws_cloudwatch_metric_alarm" "billing" {
  provider            = aws.us_east_1
  alarm_name          = "${var.name_prefix}-oregon-billing"
  alarm_description   = "Estimated monthly AWS charges exceeded threshold (steady state ~$145-190/mo)"
  namespace           = "AWS/Billing"
  metric_name         = "EstimatedCharges"
  statistic           = "Maximum"
  period              = 21600
  evaluation_periods  = 1
  threshold           = var.billing_alarm_threshold
  comparison_operator = "GreaterThanThreshold"
  dimensions          = { Currency = "USD" }
  alarm_actions       = var.alarm_sns_topic_arn != "" ? [var.alarm_sns_topic_arn] : []
  tags                = var.tags
}

# --- EC2 auto-recovery for the pets (CP, ingress, data) ---------------------
# App nodes are cattle (the ASG replaces them); the singleton infra nodes
# recover in place on a failed system status check. No autonomous writer
# promotion is involved — recovery restarts the same instance, same identity.
locals {
  pet_instances = {
    cp      = aws_instance.cp.id
    ingress = aws_instance.ingress.id
    data    = aws_instance.data.id
  }
}

resource "aws_cloudwatch_metric_alarm" "auto_recovery" {
  for_each            = local.pet_instances
  alarm_name          = "${var.name_prefix}-oregon-${each.key}-recover"
  alarm_description   = "Auto-recover the ${each.key} node on a failed system status check"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_System"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  dimensions          = { InstanceId = each.value }
  alarm_actions = concat(
    ["arn:aws:automate:${var.region}:ec2:recover"],
    var.alarm_sns_topic_arn != "" ? [var.alarm_sns_topic_arn] : []
  )
  tags = var.tags
}

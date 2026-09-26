# Budgets and Cost Anomaly Detection are global services whose API lives in us-east-1; the
# provider routes them there regardless of its region. The topic stays in the provider region.

resource "aws_sns_topic" "cost_alerts" {
  name = "${var.name_prefix}-cost-alerts"
  tags = var.tags
}

data "aws_iam_policy_document" "cost_alerts_publish" {
  statement {
    sid       = "AwsBillingServicesPublish"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.cost_alerts.arn]

    principals {
      type        = "Service"
      identifiers = ["budgets.amazonaws.com", "costalerts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [var.aws_account_id]
    }
  }
}

resource "aws_sns_topic_policy" "cost_alerts" {
  arn    = aws_sns_topic.cost_alerts.arn
  policy = data.aws_iam_policy_document.cost_alerts_publish.json
}

# Email subscriptions stay "pending confirmation" until the link in AWS's email is clicked;
# the topic delivers nothing to this endpoint before then.
resource "aws_sns_topic_subscription" "cost_alerts_email" {
  topic_arn = aws_sns_topic.cost_alerts.arn
  protocol  = "email"
  endpoint  = var.operator.email
}

# --- Monthly budget: the slow-drift catch --------------------------------------------

resource "aws_budgets_budget" "organization_monthly" {
  name         = "${var.name_prefix}-organization-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator       = "GREATER_THAN"
    threshold                 = 100
    threshold_type            = "PERCENTAGE"
    notification_type         = "ACTUAL"
    subscriber_sns_topic_arns = [aws_sns_topic.cost_alerts.arn]
  }

  notification {
    comparison_operator       = "GREATER_THAN"
    threshold                 = 100
    threshold_type            = "PERCENTAGE"
    notification_type         = "FORECASTED"
    subscriber_sns_topic_arns = [aws_sns_topic.cost_alerts.arn]
  }

  depends_on = [aws_sns_topic_policy.cost_alerts]
}

# --- Anomaly detection: the new-line-item catch --------------------------------------

# One services-dimension monitor exists per account; AWS creates it with the account, so
# this stack adopts that one rather than creating a second, which the API refuses.
import {
  to = aws_ce_anomaly_monitor.services
  id = var.anomaly_monitor_arn
}

resource "aws_ce_anomaly_monitor" "services" {
  name              = "${var.name_prefix}-services"
  monitor_type      = "DIMENSIONAL"
  monitor_dimension = "SERVICE"
  tags              = var.tags
}

resource "aws_ce_anomaly_subscription" "cost_alerts" {
  name             = "${var.name_prefix}-cost-anomalies"
  frequency        = "IMMEDIATE"
  monitor_arn_list = [aws_ce_anomaly_monitor.services.arn]
  tags             = var.tags

  subscriber {
    type    = "SNS"
    address = aws_sns_topic.cost_alerts.arn
  }

  threshold_expression {
    and {
      dimension {
        key           = "ANOMALY_TOTAL_IMPACT_ABSOLUTE"
        match_options = ["GREATER_THAN_OR_EQUAL"]
        values        = ["5"]
      }
    }
    and {
      dimension {
        key           = "ANOMALY_TOTAL_IMPACT_PERCENTAGE"
        match_options = ["GREATER_THAN_OR_EQUAL"]
        values        = ["20"]
      }
    }
  }

  depends_on = [aws_sns_topic_policy.cost_alerts]
}

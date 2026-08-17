# The Grafana Cloud CloudWatch datasource authenticates as this read-only IAM
# user. Config-driven import adopts the console-created user and its inline
# policy into state (same pattern as the replicated-secrets import above); the
# access key stays deliberately unmanaged so no key material enters state.
# Policy statements follow Grafana's documented CloudWatch datasource set:
# metrics + resource discovery + Performance Insights, plus the Logs statements
# that back log-widget queries (RDS slowquery export).
# Gated because it is a production monitoring concern, not an application secret: an
# environment with no Grafana datasource has nothing here to adopt, and an unconditional
# import fails the plan outright on a non-existent object. for_each rather than count,
# because an import block accepts only the former.
locals {
  grafana_datasource_user = toset(
    var.manage_grafana_datasource_user ? ["grafana-cloudwatch-readonly"] : []
  )
}

import {
  for_each = local.grafana_datasource_user
  to       = aws_iam_user.grafana_cloudwatch_readonly[each.key]
  id       = each.key
}

resource "aws_iam_user" "grafana_cloudwatch_readonly" {
  for_each = local.grafana_datasource_user
  name     = each.key
}

import {
  for_each = local.grafana_datasource_user
  to       = aws_iam_user_policy.grafana_cloudwatch_readonly[each.key]
  id       = "${each.key}:grafana-cloudwatch-metrics-readonly"
}

resource "aws_iam_user_policy" "grafana_cloudwatch_readonly" {
  for_each = local.grafana_datasource_user

  name = "grafana-cloudwatch-metrics-readonly"
  user = aws_iam_user.grafana_cloudwatch_readonly[each.key].name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowReadingMetricsFromCloudWatch"
        Effect = "Allow"
        Action = [
          "cloudwatch:DescribeAlarmsForMetric",
          "cloudwatch:DescribeAlarmHistory",
          "cloudwatch:DescribeAlarms",
          "cloudwatch:ListMetrics",
          "cloudwatch:GetMetricData",
          "cloudwatch:GetInsightRuleReport",
        ]
        Resource = "*"
      },
      {
        Sid    = "AllowReadingLogsFromCloudWatch"
        Effect = "Allow"
        Action = [
          "logs:DescribeLogGroups",
          "logs:GetLogGroupFields",
          "logs:StartQuery",
          "logs:StopQuery",
          "logs:GetQueryResults",
          "logs:GetLogEvents",
        ]
        Resource = "*"
      },
      {
        Sid      = "AllowReadingTagsInstancesRegionsFromEC2"
        Effect   = "Allow"
        Action   = ["ec2:DescribeTags", "ec2:DescribeInstances", "ec2:DescribeRegions"]
        Resource = "*"
      },
      {
        Sid      = "AllowReadingResourcesForTags"
        Effect   = "Allow"
        Action   = "tag:GetResources"
        Resource = "*"
      },
      {
        Sid      = "AllowReadingResourceMetricsFromPerformanceInsights"
        Effect   = "Allow"
        Action   = "pi:GetResourceMetrics"
        Resource = "*"
      },
    ]
  })
}

# Adding for_each re-addresses both resources. Terraform detects that automatically for count
# but not here, and without these it plans to destroy the live user and adopt it again.
moved {
  from = aws_iam_user.grafana_cloudwatch_readonly
  to   = aws_iam_user.grafana_cloudwatch_readonly["grafana-cloudwatch-readonly"]
}

moved {
  from = aws_iam_user_policy.grafana_cloudwatch_readonly
  to   = aws_iam_user_policy.grafana_cloudwatch_readonly["grafana-cloudwatch-readonly"]
}

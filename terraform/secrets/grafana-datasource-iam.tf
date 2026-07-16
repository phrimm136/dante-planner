# The Grafana Cloud CloudWatch datasource authenticates as this read-only IAM
# user. Config-driven import adopts the console-created user and its inline
# policy into state (same pattern as the replicated-secrets import above); the
# access key stays deliberately unmanaged so no key material enters state.
# Policy statements follow Grafana's documented CloudWatch datasource set:
# metrics + resource discovery + Performance Insights, plus the Logs statements
# that back log-widget queries (RDS slowquery export).
import {
  to = aws_iam_user.grafana_cloudwatch_readonly
  id = "grafana-cloudwatch-readonly"
}

resource "aws_iam_user" "grafana_cloudwatch_readonly" {
  name = "grafana-cloudwatch-readonly"
}

import {
  to = aws_iam_user_policy.grafana_cloudwatch_readonly
  id = "grafana-cloudwatch-readonly:grafana-cloudwatch-metrics-readonly"
}

resource "aws_iam_user_policy" "grafana_cloudwatch_readonly" {
  name = "grafana-cloudwatch-metrics-readonly"
  user = aws_iam_user.grafana_cloudwatch_readonly.name

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

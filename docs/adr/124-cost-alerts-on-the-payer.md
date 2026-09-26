# 124 cost-alerts-on-the-payer
epic: none · pr: none

## Decisions
- @cost @alerts — The management (payer) account carries one monthly AWS Budget with actual and forecast alerts and one Cost Anomaly Detection monitor over services, both publishing to an SNS topic with an email subscription to the operator. RDS Extended Support for MySQL 8.0 was billed for a full month before anyone saw it (ADR 092): the deprecation notice arrived by email and was misjudged, and the invoice was the first number, because no alert of any kind existed on spend.
  REJECTED: a CloudWatch billing alarm — one metric, one threshold, published only to the payer account in us-east-1 and evaluated a few times a day; it cannot tell a new charge type from ordinary growth, so at a threshold loose enough not to nag it fires late or never.
  REJECTED: a budget alone — catches drift but not a new line item that stays under the monthly ceiling; Extended Support would have taken most of the month to trip it.
  REJECTED: anomaly detection alone — catches a new line item in its first days but is silent on slow, expected-looking growth.
  REJECTED: Grafana alerting — the dashboard has no CloudWatch datasource, and adding one for billing puts a second credential and a scrape path in front of a signal AWS emits natively.
  REJECTED: Discord or Slack delivery — needs a Lambda or Chatbot between SNS and the webhook, one more thing to maintain for a channel read no more often than email.
- @cost @alerts @delivery — The email subscription is inert until its confirmation link is clicked, and SNS deletes a subscription left unconfirmed for three days while Terraform state keeps recording it; a budget in ALARM with a topic of zero confirmed subscribers alerts nobody. Budgets evaluate a few times a day and anomaly detection daily, so the floor on detection is about one day, not one hour.
- @cost @alerts @scope — Both resources are organization-scoped by living in the payer account, so a new linked account is covered without a change here.

## Takeaway
- takeaway: an alert is a delivery path as much as a threshold; a correct budget in front of an unconfirmed inbox alerts nobody, and no review of the threshold will see it.

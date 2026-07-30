locals {
  approved_regions = ["us-west-2", "ap-northeast-2", "us-east-1"]

  guardrails = {
    DenyRootUserActions         = data.aws_iam_policy_document.deny_root_user.json
    DenyLeaveOrganization       = data.aws_iam_policy_document.deny_leave_organization.json
    DenyDisableSecurityServices = data.aws_iam_policy_document.deny_disable_security.json
    DenyOutsideApprovedRegions  = data.aws_iam_policy_document.deny_outside_regions.json
  }
}

# Every document here is Deny-only. Detaching any of them can therefore only widen access, which
# is what makes rollback a single command that cannot break a working path.

data "aws_iam_policy_document" "deny_root_user" {
  statement {
    sid       = "DenyRootUserActions"
    effect    = "Deny"
    actions   = ["*"]
    resources = ["*"]

    condition {
      test     = "StringLike"
      variable = "aws:PrincipalArn"
      values   = ["arn:aws:iam::*:root"]
    }
  }
}

data "aws_iam_policy_document" "deny_leave_organization" {
  statement {
    sid       = "DenyLeaveOrganization"
    effect    = "Deny"
    actions   = ["organizations:LeaveOrganization"]
    resources = ["*"]
  }
}

data "aws_iam_policy_document" "deny_disable_security" {
  statement {
    sid    = "DenyDisableSecurityServices"
    effect = "Deny"
    actions = [
      "cloudtrail:DeleteTrail",
      "cloudtrail:PutEventSelectors",
      "cloudtrail:StopLogging",
      "cloudtrail:UpdateTrail",
      "config:DeleteConfigurationRecorder",
      "config:DeleteDeliveryChannel",
      "config:StopConfigurationRecorder",
      "guardduty:DeleteDetector",
      "guardduty:DisassociateFromMasterAccount",
      "guardduty:UpdateDetector",
    ]
    resources = ["*"]
  }
}

data "aws_iam_policy_document" "deny_outside_regions" {
  statement {
    sid    = "DenyOutsideApprovedRegions"
    effect = "Deny"

    # Global services answer in a region the caller did not choose, so denying them by
    # aws:RequestedRegion locks the account out of IAM, Organizations and its own billing.
    not_actions = [
      "account:*",
      "aws-marketplace:*",
      "aws-portal:*",
      "budgets:*",
      "ce:*",
      "cloudfront:*",
      "cloudtrail:*",
      "config:*",
      "cur:*",
      "globalaccelerator:*",
      "health:*",
      "iam:*",
      "identitystore:*",
      "organizations:*",
      "route53:*",
      "route53domains:*",
      "s3:GetAccountPublicAccessBlock",
      "s3:ListAllMyBuckets",
      "s3:PutAccountPublicAccessBlock",
      "shield:*",
      "sso:*",
      "sso-directory:*",
      "sts:*",
      "support:*",
      "trustedadvisor:*",
      "waf-regional:*",
      "waf:*",
      "wafv2:*",
    ]
    resources = ["*"]

    condition {
      test     = "StringNotEquals"
      variable = "aws:RequestedRegion"
      values   = local.approved_regions
    }
  }
}

resource "aws_organizations_policy" "guardrail" {
  for_each = local.guardrails

  name    = each.key
  type    = "SERVICE_CONTROL_POLICY"
  content = each.value
  tags    = var.tags

  # Creating a policy of this type fails until the type is enabled on the root, and nothing
  # in the arguments above establishes that edge.
  depends_on = [aws_organizations_organization.this]
}

resource "aws_organizations_policy_attachment" "workloads" {
  for_each = aws_organizations_policy.guardrail

  policy_id = each.value.id
  target_id = aws_organizations_organizational_unit.top["Workloads"].id
}

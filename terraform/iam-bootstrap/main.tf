data "aws_caller_identity" "current" {}

# --- GitHub Actions OIDC provider -------------------------------------------
# Lets CI exchange its short-lived GitHub OIDC token for AWS creds
# (sts:AssumeRoleWithWebIdentity) — no long-lived access keys in GitHub secrets.
#
# thumbprint_list is intentionally omitted: on aws provider ~> 5.0 it is
# Optional for this URL. AWS secures the well-known token.actions.githubusercontent.com
# endpoint with its own trusted-CA library and no longer verifies a caller-supplied
# thumbprint, so pinning one only invites a stale-fingerprint outage. Do NOT hardcode one.
resource "aws_iam_openid_connect_provider" "github" {
  count          = var.create_github_oidc_provider ? 1 : 0
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  tags           = var.tags
}

# When the provider already exists in the account (create_github_oidc_provider = false),
# reference it instead of creating a duplicate (AWS allows only one per URL).
data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1
  url   = "https://token.actions.githubusercontent.com"
}

locals {
  github_oidc_provider_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn

  account_id = data.aws_caller_identity.current.account_id

  # The exact node roles/instance-profiles terraform/oregon creates
  # (oregon/iam.tf: ${name_prefix}-oregon-{cp,ingress,data,app}). The provisioning
  # policy's iam:* and iam:PassRole are scoped to these ARN patterns so a
  # compromised provisioner cannot touch unrelated roles in the account.
  oregon_role_arn_pattern             = "arn:aws:iam::${local.account_id}:role/${var.name_prefix}-oregon-*"
  oregon_instance_profile_arn_pattern = "arn:aws:iam::${local.account_id}:instance-profile/${var.name_prefix}-oregon-*"
}

# --- Trust policy: two assumption paths -------------------------------------
data "aws_iam_policy_document" "assume" {
  # (1) Human admin from a laptop, for the initial `terraform apply`.
  statement {
    sid     = "AdminAssumeFromLaptop"
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "AWS"
      identifiers = [var.trusted_admin_principal_arn]
    }
  }

  # (2) GitHub Actions via OIDC, for applying changes in CI.
  statement {
    sid     = "GitHubActionsOIDC"
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    # StringLike so github_oidc_subject may carry a wildcard (e.g. a branch or
    # environment glob); an exact subject matches identically.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [var.github_oidc_subject]
    }
  }
}

resource "aws_iam_role" "provisioner" {
  name                 = var.role_name
  description          = "Least-privilege identity that provisions the Oregon fleet (terraform/oregon). Assumable by an admin laptop (STS) and GitHub Actions CI (OIDC). No instance profile - neither runner is an EC2 instance."
  assume_role_policy   = data.aws_iam_policy_document.assume.json
  max_session_duration = 3600
  tags                 = var.tags
}

# --- Provisioning policy: scoped by service to what terraform/oregon builds --
# NOT AdministratorAccess. Grouped by service; only IAM and S3 can be
# resource-scoped (below). ec2/autoscaling/ecr/ssm-read/cloudwatch/logs Describe*
# and most create/tag actions do not support resource-level ARNs, so those
# statements use Resource="*" — the scoping there is the action set + service.
data "aws_iam_policy_document" "provisioning" {
  # VPC, subnets, SGs, IGW, route tables, instances, launch templates, tags.
  statement {
    sid       = "Ec2Fleet"
    effect    = "Allow"
    actions   = ["ec2:*"]
    resources = ["*"] # EC2 Describe*/RunInstances tag-on-create do not support ARN scoping account-wide.
  }

  # App ASG (min/desired/max = Spring pod count).
  statement {
    sid       = "Autoscaling"
    effect    = "Allow"
    actions   = ["autoscaling:*"]
    resources = ["*"] # autoscaling Describe* actions do not support resource-level ARNs.
  }

  # A fresh account needs the autoscaling service-linked role before the first ASG.
  # Its ARN (role/aws-service-role/autoscaling.amazonaws.com/...) is outside the
  # oregon-* scope, so it gets its own tightly conditioned statement.
  statement {
    sid       = "AutoscalingServiceLinkedRole"
    effect    = "Allow"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "iam:AWSServiceName"
      values   = ["autoscaling.amazonaws.com"]
    }
  }

  # Backend image registry (repo + lifecycle + scan config).
  statement {
    sid       = "Ecr"
    effect    = "Allow"
    actions   = ["ecr:*"]
    resources = ["*"] # ecr:GetAuthorizationToken and the registry-level calls do not accept a repo ARN.
  }

  # SSM: read the public AL2023 AMI parameter, and create/manage the SecureString
  # k3s join token (oregon/cp.tf: /${name_prefix}/oregon/k3s-join-token).
  statement {
    sid    = "SsmParameters"
    effect = "Allow"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:DescribeParameters",
      "ssm:PutParameter",
      "ssm:DeleteParameter",
      "ssm:DeleteParameters",
      "ssm:AddTagsToResource",
      "ssm:RemoveTagsFromResource",
      "ssm:ListTagsForResource",
    ]
    resources = ["*"] # ssm:DescribeParameters is account-level only; parameter reads target public AWS-owned paths.
  }

  # SecureString join token uses the AWS-managed aws/ssm key; the ViaService
  # condition confines these KMS grants to SSM operations only.
  statement {
    sid    = "SsmSecureStringKms"
    effect = "Allow"
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
      "kms:GenerateDataKey",
      "kms:DescribeKey",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.region}.amazonaws.com"]
    }
  }

  # etcd-snapshot bucket: create/tag the bucket and manage its config. Scoped to
  # the oregon bucket name pattern (oregon/s3.tf: ${name_prefix}-oregon-etcd-snapshots-<acct>).
  statement {
    sid     = "S3SnapshotBucket"
    effect  = "Allow"
    actions = ["s3:*"]
    resources = [
      "arn:aws:s3:::${var.name_prefix}-oregon-*",
      "arn:aws:s3:::${var.name_prefix}-oregon-*/*",
    ]
  }

  # Billing + instance auto-recovery metric alarms.
  statement {
    sid       = "CloudWatch"
    effect    = "Allow"
    actions   = ["cloudwatch:*"]
    resources = ["*"] # cloudwatch:PutMetricAlarm supports alarm ARNs but Describe* is account-level; grouped by service.
  }

  # Log groups (fleet/app logs).
  statement {
    sid       = "Logs"
    effect    = "Allow"
    actions   = ["logs:*"]
    resources = ["*"] # logs Describe* is account-level.
  }

  # IAM: manage ONLY the oregon-* node roles + instance profiles and PassRole
  # them to EC2. Resource-scoped to the exact name patterns terraform/oregon
  # creates so this identity cannot mint or alter privileged roles elsewhere.
  statement {
    sid    = "OregonNodeRolesAndProfiles"
    effect = "Allow"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:GetInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:UntagInstanceProfile",
      "iam:PassRole",
    ]
    resources = [
      local.oregon_role_arn_pattern,
      local.oregon_instance_profile_arn_pattern,
    ]
  }
}

resource "aws_iam_policy" "provisioning" {
  name        = "${var.role_name}-policy"
  description = "Service-scoped provisioning permissions for terraform/oregon (EC2/ASG/ECR/SSM/S3/CloudWatch/Logs + oregon-* IAM)."
  policy      = data.aws_iam_policy_document.provisioning.json
  tags        = var.tags
}

resource "aws_iam_role_policy_attachment" "provisioning" {
  role       = aws_iam_role.provisioner.name
  policy_arn = aws_iam_policy.provisioning.arn
}

# --- Reproducible grant to the external terraform/rds role ------------------
# It needs ec2 route actions to add the fleet <-> RDS peering return route.
# Managed as code (vs a one-off put-role-policy); gated on the role name, which
# comes from a variable so it never lands in a committed file.
data "aws_iam_policy_document" "rds_fleet_peering" {
  count = var.rds_provisioner_role_name != "" ? 1 : 0
  statement {
    sid       = "FleetPeeringRoutes"
    effect    = "Allow"
    actions   = ["ec2:CreateRoute", "ec2:DeleteRoute", "ec2:ReplaceRoute", "ec2:DescribeRouteTables", "ec2:ModifySecurityGroupRules", "ec2:AuthorizeSecurityGroupIngress", "ec2:RevokeSecurityGroupIngress", "ec2:DescribeSecurityGroupRules", "ec2:UpdateSecurityGroupRuleDescriptionsIngress"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "rds_fleet_peering" {
  count  = var.rds_provisioner_role_name != "" ? 1 : 0
  name   = "fleet-peering"
  role   = var.rds_provisioner_role_name
  policy = data.aws_iam_policy_document.rds_fleet_peering[0].json
}

# --- CI deploy-surge grant (github-actions-deploy user) ----------------------
# Zero-downtime deploys on the DaemonSet app tier need a second NODE per region
# (node count = pod count; two Spring heaps do not fit one 2GiB app node), so
# the deploy workflow scales each app ASG 1->2 around the rollout and verifies
# completion over SSM on each region's CP. Scoped to the two app ASGs and to
# CP instances by Name tag. The user itself predates terraform and stays
# unmanaged; only this grant is code-managed.
data "aws_iam_policy_document" "deploy_surge" {
  statement {
    sid     = "SurgeScale"
    actions = ["autoscaling:SetDesiredCapacity"]
    resources = [
      "arn:aws:autoscaling:us-west-2:${data.aws_caller_identity.current.account_id}:autoScalingGroup:*:autoScalingGroupName/${var.name_prefix}-oregon-app",
      "arn:aws:autoscaling:ap-northeast-2:${data.aws_caller_identity.current.account_id}:autoScalingGroup:*:autoScalingGroupName/${var.name_prefix}-seoul-app",
    ]
  }

  statement {
    sid       = "SurgeObserve"
    actions   = ["autoscaling:DescribeAutoScalingGroups", "ec2:DescribeInstances", "ssm:GetCommandInvocation"]
    resources = ["*"] # Describe*/Get* do not support resource-level ARNs.
  }

  statement {
    sid       = "SurgeSsmDocument"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:*::document/AWS-RunShellScript"]
  }

  statement {
    sid       = "SurgeSsmTargets"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ec2:*:${data.aws_caller_identity.current.account_id}:instance/*"]
    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/Name"
      values   = ["${var.name_prefix}-oregon-cp", "${var.name_prefix}-seoul-cp"]
    }
  }
}

resource "aws_iam_policy" "deploy_surge" {
  name   = "${var.name_prefix}-deploy-surge"
  policy = data.aws_iam_policy_document.deploy_surge.json
  tags   = var.tags
}

resource "aws_iam_user_policy_attachment" "deploy_surge" {
  user       = "github-actions-deploy"
  policy_arn = aws_iam_policy.deploy_surge.arn
}

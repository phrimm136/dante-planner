# Identity Center is enabled by hand in the console; no API turns it on, so this reads the
# instance rather than creating it. It is one per organization and its region cannot be
# changed without deleting every permission set and assignment below.
data "aws_ssoadmin_instances" "this" {}

locals {
  sso_instance_arn  = tolist(data.aws_ssoadmin_instances.this.arns)[0]
  identity_store_id = tolist(data.aws_ssoadmin_instances.this.identity_store_ids)[0]

  # Session duration is how long a stolen laptop stays useful, so it shortens as the
  # permission widens.
  permission_sets = {
    AdministratorAccess = { session = "PT1H", policy = "arn:aws:iam::aws:policy/AdministratorAccess" }
    PowerUserAccess     = { session = "PT8H", policy = "arn:aws:iam::aws:policy/PowerUserAccess" }
    ReadOnlyAccess      = { session = "PT12H", policy = "arn:aws:iam::aws:policy/ReadOnlyAccess" }
  }

  # The organization's accounts attribute is refreshed at read time, so it does not contain an
  # account created by this same apply. Accounts this stack owns are merged in from their own
  # resources, which are known, so a new account is assignable in the apply that creates it.
  account_ids_by_name = merge(
    { for a in aws_organizations_organization.this.accounts : a.name => a.id },
    { for name, a in aws_organizations_account.security : name => a.id },
    { for name, a in aws_organizations_account.nonprod : name => a.id },
    { for name, a in aws_organizations_account.prod : name => a.id },
  )
}

resource "aws_ssoadmin_permission_set" "this" {
  for_each = local.permission_sets

  name             = each.key
  instance_arn     = local.sso_instance_arn
  session_duration = each.value.session
  tags             = var.tags
}

resource "aws_ssoadmin_managed_policy_attachment" "this" {
  for_each = local.permission_sets

  instance_arn       = local.sso_instance_arn
  managed_policy_arn = each.value.policy
  permission_set_arn = aws_ssoadmin_permission_set.this[each.key].arn
}

# Assignments target a group even for a single operator: the assignment set is then keyed by
# account rather than by person, so a second human is a group membership instead of a
# re-run across every account.
resource "aws_identitystore_group" "operators" {
  identity_store_id = local.identity_store_id
  display_name      = var.operator_group_name
}

resource "aws_identitystore_user" "operator" {
  identity_store_id = local.identity_store_id
  user_name         = var.operator.user_name
  display_name      = var.operator.display_name

  name {
    given_name  = var.operator.given_name
    family_name = var.operator.family_name
  }

  emails {
    value   = var.operator.email
    primary = true
  }
}

resource "aws_identitystore_group_membership" "operator" {
  identity_store_id = local.identity_store_id
  group_id          = aws_identitystore_group.operators.group_id
  member_id         = aws_identitystore_user.operator.user_id
}

resource "aws_ssoadmin_account_assignment" "operators" {
  for_each = var.assignments

  instance_arn       = local.sso_instance_arn
  permission_set_arn = aws_ssoadmin_permission_set.this[each.value].arn
  principal_id       = aws_identitystore_group.operators.group_id
  principal_type     = "GROUP"
  target_id          = local.account_ids_by_name[each.key]
  target_type        = "AWS_ACCOUNT"
}

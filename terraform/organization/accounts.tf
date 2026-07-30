# Removing an entry here does NOT close the account: close_on_deletion stays false because a
# closure cannot be undone, takes 90 days to complete, and keeps the address claimed throughout,
# so an account leaves the organization deliberately through the console or not at all.
#
# role_name is read only at creation. It is ignored afterwards so a later provider default cannot
# plan a replacement of a live account.
resource "aws_organizations_account" "security" {
  for_each = var.security_accounts

  name              = each.key
  email             = each.value
  parent_id         = aws_organizations_organizational_unit.top["Security"].id
  role_name         = "OrganizationAccountAccessRole"
  close_on_deletion = false
  tags              = var.tags

  lifecycle {
    ignore_changes = [role_name]
  }
}

resource "aws_organizations_account" "nonprod" {
  for_each = var.nonprod_accounts

  name              = each.key
  email             = each.value
  parent_id         = aws_organizations_organizational_unit.workload["NonProd"].id
  role_name         = "OrganizationAccountAccessRole"
  close_on_deletion = false
  tags              = var.tags

  lifecycle {
    ignore_changes = [role_name]
  }
}

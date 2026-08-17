locals {
  top_level_units = ["Security", "Infrastructure", "Workloads", "Sandbox", "Suspended"]
  workload_units  = ["Prod", "NonProd"]
}

resource "aws_organizations_organizational_unit" "top" {
  for_each = toset(local.top_level_units)

  name = each.value
  # roots is computed, so the root id comes from the adopted organization rather than a
  # separate data source that would race the import on a first apply.
  parent_id = aws_organizations_organization.this.roots[0].id
  tags      = var.tags
}

resource "aws_organizations_organizational_unit" "workload" {
  for_each = toset(local.workload_units)

  name      = each.value
  parent_id = aws_organizations_organizational_unit.top["Workloads"].id
  tags      = var.tags
}

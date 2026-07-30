output "root_id" {
  description = "Organization root id. Policy attachments and top-level unit placement target it."
  value       = aws_organizations_organization.this.roots[0].id
}

output "security_account_ids" {
  description = "Security-unit account name to id. The log-archive id is the target the organization trail's destination bucket is created in."
  value       = { for name, a in aws_organizations_account.security : name => a.id }
}

output "organizational_unit_ids" {
  description = "Unit name to id, both levels. Account vending reads NonProd; the guardrail stack reads Workloads."
  value = merge(
    { for name, ou in aws_organizations_organizational_unit.top : name => ou.id },
    { for name, ou in aws_organizations_organizational_unit.workload : name => ou.id },
  )
}

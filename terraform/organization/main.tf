# The organization already exists; this stack adopts it rather than creating one. Creating
# an organization converts the calling account into the management account irreversibly, so
# the resource carries prevent_destroy: a destroy here calls DeleteOrganization.
#
# aws_service_access_principals is AUTHORITATIVE. Anything enabled out of band and omitted
# below is DISABLED on the next apply, which is how an apparently additive change takes
# Identity Center down. Read the live set before editing this list:
#   aws organizations list-aws-service-access-for-organization
import {
  to = aws_organizations_organization.this
  id = var.organization_id
}

resource "aws_organizations_organization" "this" {
  feature_set = "ALL"

  enabled_policy_types = [
    "SERVICE_CONTROL_POLICY",
    "TAG_POLICY",
  ]

  aws_service_access_principals = [
    "sso.amazonaws.com",
    "cloudtrail.amazonaws.com",
  ]

  lifecycle {
    prevent_destroy = true
  }
}

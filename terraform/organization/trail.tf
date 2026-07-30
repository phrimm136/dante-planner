locals {
  # Computed from the account this stack created rather than handed over as an input or read from
  # the archive's state: the name is deterministic, and a mismatch fails loudly at creation because
  # CloudTrail validates the destination policy before the trail exists.
  trail_bucket = "${var.name_prefix}-org-trail-${aws_organizations_account.security["log-archive"].id}"
}

resource "aws_cloudtrail" "organization" {
  name                  = var.trail_name
  s3_bucket_name        = local.trail_bucket
  is_organization_trail = true

  # A single-region trail records only its own region, which would leave the second region's
  # activity unrecorded and the gap invisible.
  is_multi_region_trail         = true
  include_global_service_events = true

  # Digest files, so a modified object is detectable rather than merely improbable.
  enable_log_file_validation = true

  tags = var.tags

  depends_on = [aws_organizations_organization.this]
}

output "provisioner_role_arn" {
  description = "ARN of the provisioning role. Use it as the laptop AWS profile's role_arn (source_profile = admin) and as the Oregon CI workflow's role-to-assume (aws-actions/configure-aws-credentials)."
  value       = aws_iam_role.provisioner.arn
}

output "tf_state_bucket" {
  description = "S3 bucket backing every other stack's state. Use it as the `bucket` in their backend blocks; this stack stays on local state."
  value       = var.create_state_bucket ? aws_s3_bucket.tf_state[0].id : local.tf_state_bucket
}

output "github_oidc_provider_arn" {
  description = "ARN of the GitHub Actions OIDC provider trusted by the role (created here or looked up). Reference only — the workflow assumes provisioner_role_arn, not this."
  value       = local.github_oidc_provider_arn
}

output "primary_secret_arns" {
  description = "Primary (us-west-2) ARNs of the now-replicated secrets. The Seoul replica ARN is the same secret in var.replica_region — Seoul's app-node IAM grants GetSecretValue on arn:aws:secretsmanager:<replica_region>:<acct>:secret:<name>-*."
  value       = { for name, s in aws_secretsmanager_secret.replicated : name => s.arn }
}

output "replica_region" {
  description = "Region the secrets are replicated to (Seoul reads the local replica)."
  value       = var.replica_region
}

output "replicated_secret_names" {
  description = "Names now managed here with a cross-region replica attached."
  value       = keys(aws_secretsmanager_secret.replicated)
}

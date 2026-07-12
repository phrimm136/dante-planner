output "rds_endpoint" {
  description = "RDS endpoint hostname (no port). Feed this into SSM MYSQL_HOST for Commit 2."
  value       = aws_db_instance.this.address
}

output "rds_arn" {
  description = "Primary RDS instance ARN — read by terraform/seoul (remote state) as the cross-region read-replica source (replicas require the full ARN)."
  value       = aws_db_instance.this.arn
}

output "ca_cert_identifier" {
  description = "CA bundle id the RDS instance presents. Download the matching bundle from https://truststore.pki.rds.amazonaws.com/<region>/<region>-bundle.pem for the backend's VERIFY_CA trust store."
  value       = aws_db_instance.this.ca_cert_identifier
}

output "rds_port" {
  description = "RDS port."
  value       = aws_db_instance.this.port
}

output "rds_security_group_id" {
  description = "RDS security group id (referenced by the temp replication ingress on the EC2 SG)."
  value       = aws_security_group.rds.id
}

output "master_user_secret_arn" {
  description = "Secrets Manager ARN holding the AWS-managed master password (for admin ops: creating the app user in runbook 0.6b)."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
  sensitive   = true
}

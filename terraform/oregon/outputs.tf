output "vpc_id" {
  description = "Oregon fleet VPC id."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Public subnet ids (app ASG spans these)."
  value       = aws_subnet.public[*].id
}

output "cluster_security_group_id" {
  description = "Cluster SG carried by every fleet node. Add this to the RDS SG allowlist (in terraform/rds's inputs) so app/data nodes can reach RDS on 3306 — this stack does NOT edit the RDS SG."
  value       = aws_security_group.cluster.id
}

output "ingress_public_ip" {
  description = "Ingress node public IP — the Global Accelerator endpoint target in Phase 14."
  value       = aws_instance.ingress.public_ip
}

output "cp_private_ip" {
  description = "Control-plane node private IP (k3s server / agent join target)."
  value       = aws_instance.cp.private_ip
}

output "backend_ecr_repository_url" {
  description = "ECR repository URL for the backend image. Feed into the CI workflow's push target and the kustomize overlay image newName."
  value       = aws_ecr_repository.backend.repository_url
}

output "etcd_snapshot_bucket" {
  description = "S3 bucket holding CP embedded-etcd snapshots."
  value       = aws_s3_bucket.etcd_snapshots.bucket
}

output "k3s_join_token_parameter" {
  description = "SSM SecureString parameter name holding the k3s join token."
  value       = aws_ssm_parameter.k3s_token.name
}

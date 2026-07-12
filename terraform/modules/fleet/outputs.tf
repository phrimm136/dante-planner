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

output "ingress_eip" {
  description = "Stable public IP associated with the ingress (empty until a durable EIP is allocated in terraform/oregon-edge and var.ingress_eip_allocation_id is set). Point Cloudflare's api A-record HERE, not at ingress_public_ip (which changes on ingress replacement)."
  value       = var.ingress_eip_allocation_id != "" ? data.aws_eip.ingress[0].public_ip : ""
}

output "cp_private_ip" {
  description = "Control-plane node private IP (k3s server / agent join target)."
  value       = aws_instance.cp.private_ip
}

output "cp_instance_id" {
  description = "Control-plane instance id — the `aws ssm send-command` target for scripts/ops/oregon-verify.sh."
  value       = aws_instance.cp.id
}

output "kubeconfig_ssm_parameter" {
  description = "SSM SecureString holding the admin kubeconfig (server = CP private IP, in-VPC only). Fetch: aws ssm get-parameter --with-decryption --region us-west-2 --name <this> --query Parameter.Value --output text > ~/.kube/dante-oregon"
  value       = aws_ssm_parameter.kubeconfig.name
}

output "backend_ecr_repository_url" {
  description = "ECR repository URL for the backend image. Feed into the CI workflow's push target and the kustomize overlay image newName."
  value       = data.aws_ecr_repository.backend.repository_url
}

output "etcd_snapshot_bucket" {
  description = "S3 bucket holding CP embedded-etcd snapshots."
  value       = aws_s3_bucket.etcd_snapshots.bucket
}

output "k3s_join_token_parameter" {
  description = "SSM SecureString parameter name holding the k3s join token."
  value       = aws_ssm_parameter.k3s_token.name
}

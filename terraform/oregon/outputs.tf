output "vpc_id" {
  description = "Oregon fleet VPC id."
  value       = module.fleet.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet ids (app ASG spans these)."
  value       = module.fleet.public_subnet_ids
}

output "public_route_table_id" {
  description = "Oregon public route table id — read by terraform/seoul (remote state) for the Seoul→Oregon return route."
  value       = module.fleet.public_route_table_id
}

output "vpc_cidr" {
  description = "Oregon fleet VPC CIDR — read by terraform/seoul (remote state) for the Seoul→Oregon route destination."
  value       = module.fleet.vpc_cidr
}

output "internal_zone_id_for_seoul" {
  description = "Private hosted zone id — Seoul reads this via remote_state to associate its VPC (resolve redis-auth.oregon.danteplanner.internal)."
  value       = aws_route53_zone.internal.zone_id
}

output "ingress_instance_id" {
  description = "Oregon ingress EC2 instance id — read by terraform/global-accelerator (remote state) as the Oregon GA endpoint."
  value       = module.fleet.ingress_instance_id
}

output "cluster_security_group_id" {
  description = "Cluster SG carried by every fleet node. Add this to the RDS SG allowlist (in terraform/rds's inputs) so app/data nodes can reach RDS on 3306 — this stack does NOT edit the RDS SG."
  value       = module.fleet.cluster_security_group_id
}

output "ingress_public_ip" {
  description = "Ingress node public IP — the Global Accelerator endpoint target in Phase 14."
  value       = module.fleet.ingress_public_ip
}

output "ingress_eip" {
  description = "Stable public IP associated with the ingress (empty until a durable EIP is allocated in terraform/oregon-edge and var.ingress_eip_allocation_id is set). Point Cloudflare's api A-record HERE, not at ingress_public_ip (which changes on ingress replacement)."
  value       = module.fleet.ingress_eip
}

output "cp_private_ip" {
  description = "Control-plane node private IP (k3s server / agent join target)."
  value       = module.fleet.cp_private_ip
}

output "cp_instance_id" {
  description = "Control-plane instance id — the `aws ssm send-command` target for scripts/ops/oregon-verify.sh."
  value       = module.fleet.cp_instance_id
}

output "kubeconfig_ssm_parameter" {
  description = "SSM SecureString holding the admin kubeconfig (server = CP private IP, in-VPC only). Fetch: aws ssm get-parameter --with-decryption --region us-west-2 --name <this> --query Parameter.Value --output text > ~/.kube/dante-oregon"
  value       = module.fleet.kubeconfig_ssm_parameter
}

output "backend_ecr_repository_url" {
  description = "ECR repository URL for the backend image. Feed into the CI workflow's push target and the kustomize overlay image newName."
  value       = module.fleet.backend_ecr_repository_url
}

output "etcd_snapshot_bucket" {
  description = "S3 bucket holding CP embedded-etcd snapshots."
  value       = module.fleet.etcd_snapshot_bucket
}

output "k3s_join_token_parameter" {
  description = "SSM SecureString parameter name holding the k3s join token."
  value       = module.fleet.k3s_join_token_parameter
}

output "rds_peering_connection_id" {
  description = "Feed into terraform/rds var.fleet_peering_connection_id to add the return route."
  value       = module.fleet.rds_peering_connection_id
}

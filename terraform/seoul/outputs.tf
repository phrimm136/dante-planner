output "ingress_public_ip" {
  description = "Seoul ingress node public IP (Global Accelerator endpoint target)."
  value       = module.fleet.ingress_public_ip
}

output "cp_instance_id" {
  description = "Seoul control-plane instance id (scripts/ops verify target)."
  value       = module.fleet.cp_instance_id
}

output "ingress_instance_id" {
  description = "Seoul ingress EC2 instance id — feed into terraform/global-accelerator seoul_ingress_instance_id to add the Seoul GA endpoint group."
  value       = module.fleet.ingress_instance_id
}

output "kubeconfig_ssm_parameter" {
  description = "Seoul admin kubeconfig SSM parameter (in ap-northeast-2)."
  value       = module.fleet.kubeconfig_ssm_parameter
}

output "cluster_security_group_id" {
  description = "Seoul cluster SG. Not referenced by terraform/rds: its Seoul 3306 rule is CIDR-based on vpc_cidr, since an SG reference cannot cross regions."
  value       = module.fleet.cluster_security_group_id
}

output "rds_peering_connection_id" {
  description = "Cross-region peering id (Seoul↔RDS) — read by terraform/rds (remote state) for the return route + 3306 ingress."
  value       = module.fleet.rds_peering_connection_id
}

output "vpc_cidr" {
  description = "Seoul fleet VPC CIDR — read by terraform/rds (remote state) for the RDS-side return route and the CIDR-based 3306 ingress."
  value       = module.fleet.vpc_cidr
}

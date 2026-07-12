output "ingress_public_ip" {
  description = "Seoul ingress node public IP (Global Accelerator endpoint target)."
  value       = module.fleet.ingress_public_ip
}

output "ingress_eip" {
  description = "Stable Seoul ingress IP (empty until a durable EIP is allocated and set)."
  value       = module.fleet.ingress_eip
}

output "cp_instance_id" {
  description = "Seoul control-plane instance id (scripts/ops verify target)."
  value       = module.fleet.cp_instance_id
}

output "kubeconfig_ssm_parameter" {
  description = "Seoul admin kubeconfig SSM parameter (in ap-northeast-2)."
  value       = module.fleet.kubeconfig_ssm_parameter
}

output "cluster_security_group_id" {
  description = "Seoul cluster SG. Add to the RDS SG allowlist (terraform/rds) so Seoul nodes reach the primary RDS on 3306 over the cross-region peering."
  value       = module.fleet.cluster_security_group_id
}

output "rds_peering_connection_id" {
  description = "Cross-region peering id (Seoul↔RDS). Feed into terraform/rds for the return route + SG rule."
  value       = module.fleet.rds_peering_connection_id
}

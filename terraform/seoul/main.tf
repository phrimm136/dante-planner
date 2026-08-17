module "fleet" {
  source = "../modules/fleet"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  region                 = var.region
  region_name_suffix     = "seoul"
  backend_ecr_account_id = var.backend_ecr_account_id
  gitops_target_revision = var.gitops_target_revision
  vpc_cidr               = var.vpc_cidr
  availability_zones     = var.availability_zones

  public_subnet_cidrs   = var.public_subnet_cidrs
  ingress_allowed_cidrs = var.ingress_allowed_cidrs

  # Cross-region peering to the primary RDS VPC: no auto-accept, and the requester
  # carries peer_region so AWS routes the request to us-west-2, where the accepter
  # below completes it.
  rds_vpc_id              = var.rds_vpc_id
  rds_vpc_cidr            = data.terraform_remote_state.rds.outputs.rds_vpc_cidr
  rds_peer_region         = var.rds_region
  rds_peering_auto_accept = false

  # Was hardcoded true while Oregon read a variable defaulting false, so the entry-plane
  # retirement reached one region's config and not the other.
  enable_global_accelerator = var.enable_global_accelerator

  instance_type                   = var.instance_type
  ami_ssm_parameter               = var.ami_ssm_parameter
  ssh_key_name                    = var.ssh_key_name
  app_asg_min_size                = var.app_asg_min_size
  app_asg_desired_capacity        = var.app_asg_desired_capacity
  app_asg_max_size                = var.app_asg_max_size
  backend_image_repo              = var.backend_image_repo
  gitops_repo_url                 = var.gitops_repo_url
  argocd_version                  = var.argocd_version
  gateway_api_version             = var.gateway_api_version
  external_secrets_chart_version  = var.external_secrets_chart_version
  ecr_credential_provider_version = var.ecr_credential_provider_version
  rs256_private_key_secret_name   = var.rs256_private_key_secret_name
  billing_alarm_threshold         = var.billing_alarm_threshold
  alarm_sns_topic_arn             = var.alarm_sns_topic_arn
  etcd_snapshot_retention         = var.etcd_snapshot_retention

  # Deliberately NOT passed symmetrically: only the primary admits the peer fleet to its auth
  # Redis, because the secondary's is a replica and writes travel to the primary.
  redis_cross_region_cidr = var.redis_cross_region_cidr

  tags = var.tags
}

# Accepter for the cross-region peering, in the RDS region. One apply converges
# both sides — the "few manual touch" constraint (no console accept).
resource "aws_vpc_peering_connection_accepter" "seoul_to_rds" {
  provider                  = aws.rds
  vpc_peering_connection_id = module.fleet.rds_peering_connection_id
  auto_accept               = true
  tags                      = merge(var.tags, { Name = "seoul-to-rds-accepter" })
}

# The RDS-side half of this peering lives in terraform/rds, not here: the return route
# (aws_route.rds_to_seoul_fleet) and the 3306 ingress for Seoul's CIDR
# (aws_vpc_security_group_ingress_rule.seoul_fleet_to_rds). A cross-region SG reference is not
# allowed, so that rule is CIDR-based and terraform/rds takes seoul_fleet_cidr explicitly. Both
# stacks must be applied for Seoul→primary-RDS writes to have a return path.

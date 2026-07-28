module "fleet" {
  source = "../modules/fleet"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  region                 = var.region
  region_name_suffix     = "seoul"
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

  enable_global_accelerator = true
  tags                      = var.tags
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

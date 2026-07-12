module "fleet" {
  source = "../modules/fleet"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  region             = var.region
  region_name_suffix = "seoul"
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones

  public_subnet_cidrs   = var.public_subnet_cidrs
  ingress_allowed_cidrs = var.ingress_allowed_cidrs

  # Cross-region peering to the primary RDS VPC: no auto-accept, and the requester
  # carries peer_region so AWS routes the request to us-west-2, where the accepter
  # below completes it.
  rds_vpc_id              = var.rds_vpc_id
  rds_peer_region         = var.rds_region
  rds_peering_auto_accept = false

  ingress_eip_allocation_id = var.ingress_eip_allocation_id
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

# NOTE (cross-stack follow-up, not authored here): the RDS-side return route and
# the 3306 SG rule for Seoul's CIDR live in terraform/rds, which today wires a
# SINGLE fleet peering (var.fleet_peering_connection_id). Adding Seoul's return
# path means terraform/rds must accept a SECOND peering id + CIDR. Until then
# Seoul→primary-RDS write traffic has no return route. Tracked as a terraform/rds
# enhancement; consent-gated apply.

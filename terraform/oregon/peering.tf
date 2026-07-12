# VPC peering fleet VPC <-> RDS (prod) VPC so the private RDS is reachable from
# app/data nodes. Same account + region, so the requester auto-accepts. The RDS
# side (return route + the 3306 SG rule referencing the cluster SG) lives in
# terraform/rds, wired via the outputs below. CIDRs must not overlap.

variable "rds_vpc_id" {
  description = "RDS (prod) VPC id to peer with for private RDS access. Set in terraform.tfvars (gitignored) — do not commit."
  type        = string
}

data "aws_vpc" "rds" {
  id = var.rds_vpc_id
}

resource "aws_vpc_peering_connection" "rds" {
  vpc_id      = aws_vpc.this.id
  peer_vpc_id = var.rds_vpc_id
  auto_accept = true
  tags        = merge(var.tags, { Name = "${var.name_prefix}-oregon-to-rds" })
}

resource "aws_route" "fleet_to_rds" {
  route_table_id            = aws_route_table.public.id
  destination_cidr_block    = data.aws_vpc.rds.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.rds.id
}

output "rds_peering_connection_id" {
  description = "Feed into terraform/rds var.fleet_peering_connection_id to add the return route."
  value       = aws_vpc_peering_connection.rds.id
}

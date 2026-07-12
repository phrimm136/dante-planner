# RDS side of the fleet <-> RDS VPC peering. The peering itself is created and
# auto-accepted by terraform/oregon; here we add the return route from the RDS
# VPC to the fleet and allow the fleet cluster SG to reach RDS on 3306 (an SG
# reference resolves across same-region peering). All guarded so a plain RDS
# apply (no fleet) is unaffected until the three fleet_* values are supplied.

variable "fleet_peering_connection_id" {
  description = "VPC peering connection id from terraform/oregon output rds_peering_connection_id. Empty = no fleet wiring."
  type        = string
  default     = ""
}

variable "fleet_cluster_security_group_id" {
  description = "k3s fleet cluster SG (terraform/oregon output cluster_security_group_id) allowed to reach RDS on 3306."
  type        = string
  default     = ""
}

variable "fleet_vpc_cidr" {
  description = "Fleet VPC CIDR, for the RDS-side return route over the peering."
  type        = string
  default     = ""
}

# Every route table in the RDS VPC needs a route back to the fleet CIDR.
data "aws_route_tables" "rds_vpc" {
  count  = var.fleet_peering_connection_id != "" ? 1 : 0
  vpc_id = var.vpc_id
}

resource "aws_route" "rds_to_fleet" {
  count                     = var.fleet_peering_connection_id != "" ? length(data.aws_route_tables.rds_vpc[0].ids) : 0
  route_table_id            = tolist(data.aws_route_tables.rds_vpc[0].ids)[count.index]
  destination_cidr_block    = var.fleet_vpc_cidr
  vpc_peering_connection_id = var.fleet_peering_connection_id
}

resource "aws_vpc_security_group_ingress_rule" "fleet_to_rds" {
  count                        = var.fleet_cluster_security_group_id != "" ? 1 : 0
  security_group_id            = aws_security_group.rds.id
  description                  = "k3s fleet app/data nodes to RDS over VPC peering"
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
  referenced_security_group_id = var.fleet_cluster_security_group_id
}

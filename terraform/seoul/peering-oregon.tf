# Seoul <-> Oregon VPC peering, for the Redis WRITE path: Seoul blacklist/rotation
# writes must reach the Oregon PRIMARY redis-auth (write-global), and Seoul's
# local redis-auth REPLICAOF-pulls from it over this link. Cross-region, so the
# same requester/accepter split as the RDS peering (accepter in Oregon's region,
# us-west-2 = the aws.rds provider alias). CIDRs must not overlap (Seoul
# 10.30.0.0/16, Oregon 10.20.0.0/16).

variable "oregon_vpc_id" {
  description = "Oregon fleet VPC id (module.fleet vpc_id output of terraform/oregon). Set in terraform.tfvars."
  type        = string
}

variable "oregon_vpc_cidr" {
  description = "Oregon fleet VPC CIDR, for the Seoul-side route toward it."
  type        = string
  default     = "10.20.0.0/16"
}

variable "oregon_route_table_id" {
  description = "Oregon public route table id (module.fleet public_route_table_id output of terraform/oregon), for the Oregon-side return route toward Seoul."
  type        = string
}

resource "aws_vpc_peering_connection" "oregon" {
  vpc_id      = module.fleet.vpc_id
  peer_vpc_id = var.oregon_vpc_id
  peer_region = var.rds_region # Oregon shares us-west-2 with RDS
  auto_accept = false
  tags        = merge(var.tags, { Name = "seoul-to-oregon" })
}

resource "aws_vpc_peering_connection_accepter" "oregon" {
  provider                  = aws.rds # us-west-2
  vpc_peering_connection_id = aws_vpc_peering_connection.oregon.id
  auto_accept               = true
  tags                      = merge(var.tags, { Name = "seoul-to-oregon-accepter" })
}

# Seoul-side route: reach Oregon's CIDR via the peering.
resource "aws_route" "seoul_to_oregon" {
  route_table_id            = module.fleet.public_route_table_id
  destination_cidr_block    = var.oregon_vpc_cidr
  vpc_peering_connection_id = aws_vpc_peering_connection.oregon.id
}

# Oregon-side return route: reach Seoul's CIDR via the peering (in Oregon's region).
resource "aws_route" "oregon_to_seoul" {
  provider                  = aws.rds # us-west-2
  route_table_id            = var.oregon_route_table_id
  destination_cidr_block    = var.vpc_cidr
  vpc_peering_connection_id = aws_vpc_peering_connection.oregon.id
}

output "oregon_peering_connection_id" {
  description = "Seoul↔Oregon peering id."
  value       = aws_vpc_peering_connection.oregon.id
}

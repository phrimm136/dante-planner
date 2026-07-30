# The database network, for accounts that have none. Production's VPC predates this stack and is
# passed in instead, so create_vpc stays false there; an account added later creates its own rather
# than requiring one to be built by hand first.
#
# Private only: no internet gateway and no NAT. The database is reached over VPC peering from a
# fleet, and nothing in it needs egress.

data "aws_availability_zones" "available" {
  count = var.create_vpc ? 1 : 0
  state = "available"
}

resource "aws_vpc" "this" {
  count = var.create_vpc ? 1 : 0

  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true # RDS endpoints resolve by name, and the fleet dials them that way

  tags = merge(var.tags, { Name = "${var.name_prefix}-rds" })
}

# Two subnets in two zones: RDS requires it even for a single-AZ instance, and it is what lets
# multi_az be flipped later without rebuilding the subnet group.
resource "aws_subnet" "db" {
  count = var.create_vpc ? 2 : 0

  vpc_id            = aws_vpc.this[0].id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone = data.aws_availability_zones.available[0].names[count.index]

  tags = merge(var.tags, { Name = "${var.name_prefix}-rds-db-${count.index}" })
}

locals {
  # Whichever side supplied them. The consumers below do not care which, and production keeps
  # passing its existing ids.
  vpc_id        = var.create_vpc ? aws_vpc.this[0].id : var.vpc_id
  db_subnet_ids = var.create_vpc ? aws_subnet.db[*].id : var.db_subnet_ids
}

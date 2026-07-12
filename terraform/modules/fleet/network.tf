# --- VPC --------------------------------------------------------------------
# Dedicated VPC so the region rebuilds from scratch (Done-When: unattended
# bootstrap). Public subnets with no NAT gateway is a deliberate cost choice
# (a NAT gateway is ~$32/mo/AZ against a ~$145-190/mo budget); node security
# groups are the boundary and Cloudflare mTLS is the real origin gate.

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = merge(var.tags, { Name = "${var.name_prefix}-${var.region_name_suffix}" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-${var.region_name_suffix}-igw" })
}

resource "aws_subnet" "public" {
  count                   = length(var.availability_zones)
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true
  tags                    = merge(var.tags, { Name = "${var.name_prefix}-${var.region_name_suffix}-public-${var.availability_zones[count.index]}" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-${var.region_name_suffix}-public" })
}

# Standalone route (not an inline route{} block) so it coexists with the
# aws_route.fleet_to_rds peering route on the same table — inline + standalone
# routes on one route table conflict and clobber each other.
resource "aws_route" "public_igw" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count          = length(var.availability_zones)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# --- Security groups --------------------------------------------------------

# Cluster SG: every node carries it. All intra-cluster traffic (k3s API 6443,
# flannel VXLAN 8472, kubelet 10250, etcd 2379-2380, etc.) flows within the SG;
# a self-referencing rule keeps the port list from rotting as k3s evolves.
resource "aws_security_group" "cluster" {
  name        = "${var.name_prefix}-${var.region_name_suffix}-cluster"
  description = "k3s intra-cluster traffic (self) + egress"
  vpc_id      = aws_vpc.this.id
  tags        = merge(var.tags, { Name = "${var.name_prefix}-${var.region_name_suffix}-cluster" })
}

resource "aws_vpc_security_group_ingress_rule" "cluster_self" {
  security_group_id            = aws_security_group.cluster.id
  description                  = "All intra-cluster traffic between fleet nodes"
  referenced_security_group_id = aws_security_group.cluster.id
  ip_protocol                  = "-1"
}

# Cross-region auth Redis: admit ONLY the peer region's fleet CIDR (over VPC
# peering) on the redis-auth NodePort published by
# deploy/overlays/oregon/redis-auth-nodeport.yaml. The auth Redis holds
# revocation state (blacklist/rotation/tombstones), so exposure is opt-in:
# the default empty redis_cross_region_cidr renders count=0 and leaves the
# single-region SG surface unchanged.
resource "aws_vpc_security_group_ingress_rule" "redis_auth_cross_region" {
  count             = var.redis_cross_region_cidr == "" ? 0 : 1
  security_group_id = aws_security_group.cluster.id
  description       = "Auth Redis NodePort from the peer region fleet CIDR (REPLICAOF + cross-region auth writes)"
  cidr_ipv4         = var.redis_cross_region_cidr
  from_port         = var.redis_auth_nodeport
  to_port           = var.redis_auth_nodeport
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "cluster_all" {
  security_group_id = aws_security_group.cluster.id
  description       = "Egress: image pulls, SSM, S3, Secrets Manager, RDS, cross-region write path"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# Ingress SG: only the ingress node carries it (it is the Global Accelerator
# endpoint in Phase 14). 443 from Cloudflare + GA-health ranges only.
resource "aws_security_group" "ingress" {
  name        = "${var.name_prefix}-${var.region_name_suffix}-ingress"
  description = "Public 443 to the Traefik ingress node (GA endpoint)"
  vpc_id      = aws_vpc.this.id
  tags        = merge(var.tags, { Name = "${var.name_prefix}-${var.region_name_suffix}-ingress" })
}

resource "aws_vpc_security_group_ingress_rule" "ingress_https" {
  for_each          = toset(var.ingress_allowed_cidrs)
  security_group_id = aws_security_group.ingress.id
  description       = "HTTPS from Cloudflare edge ranges"
  cidr_ipv4         = each.value
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

# Global Accelerator health-checks the ingress DIRECTLY on 443 from AWS's GA IP
# ranges (not via Cloudflare), so its source must be allowlisted or every endpoint
# reads unhealthy and GA routes nowhere. Uses the AWS-managed GA prefix list rather
# than hardcoded CIDRs (AWS keeps it current). Gated on enable_global_accelerator
# so a fleet with no GA (single-region default) has an unchanged SG surface.
data "aws_ec2_managed_prefix_list" "global_accelerator" {
  count = var.enable_global_accelerator ? 1 : 0
  name  = "com.amazonaws.global.globalaccelerator"
}

resource "aws_vpc_security_group_ingress_rule" "ingress_ga_health" {
  count             = var.enable_global_accelerator ? 1 : 0
  security_group_id = aws_security_group.ingress.id
  description       = "HTTPS health check from the Global Accelerator managed prefix list"
  prefix_list_id    = data.aws_ec2_managed_prefix_list.global_accelerator[0].id
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

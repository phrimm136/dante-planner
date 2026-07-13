# Durable ingress Elastic IP. Allocated once and held for the life of the service
# so the fleet's destroy+apply rebuild never changes the public IP Cloudflare
# targets. The fleet stack consumes .id via var.ingress_eip_allocation_id and
# manages only the aws_eip_association (re-bound on every rebuild).
#
# This EIP is a transitional Oregon-first entry primitive: once Phase 14's Global
# Accelerator lands (anycast IPs, endpoints = the ingress EC2s directly), GA
# becomes the front door and this address stops being the client entry.
resource "aws_eip" "ingress" {
  domain = "vpc"
  tags = {
    Name    = "${var.name_prefix}-oregon-ingress"
    Project = "danteplanner"
    Layer   = "edge-durable"
  }
}

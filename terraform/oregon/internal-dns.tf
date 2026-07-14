# Private DNS so a peer region (Seoul) reaches Oregon's auth-redis by a STABLE
# NAME, not the data node's IP — the data node is a pet, but a replacement changes
# its IP; a name re-points via this A record instead of re-wiring Seoul. The zone
# is associated with the Oregon VPC here and with the Seoul VPC from the Seoul
# stack (aws_route53_zone_association there, over the peering), so Seoul pods
# resolve it. Applied once with the fleet; the record follows the data node IP.
resource "aws_route53_zone" "internal" {
  name = "danteplanner.internal"
  vpc {
    vpc_id = module.fleet.vpc_id
  }
  # Seoul associates its VPC out-of-band (route53_zone_association in terraform/
  # seoul); without ignore, each apply would try to disassociate it.
  lifecycle {
    ignore_changes = [vpc]
  }
  tags = var.tags
}

resource "aws_route53_record" "redis_auth" {
  zone_id = aws_route53_zone.internal.id
  name    = "redis-auth.oregon.danteplanner.internal"
  type    = "A"
  ttl     = 60
  records = [module.fleet.data_node_private_ip]
}

output "internal_zone_id" {
  description = "Private hosted zone id — the Seoul stack associates its VPC with this to resolve redis-auth.oregon.danteplanner.internal over the peering."
  value       = aws_route53_zone.internal.zone_id
}

output "redis_auth_internal_fqdn" {
  description = "Stable internal name for Oregon auth-redis (reached on the NodePort over peering)."
  value       = aws_route53_record.redis_auth.fqdn
}

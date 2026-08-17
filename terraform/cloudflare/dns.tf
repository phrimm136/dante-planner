# Records for hostnames that enter a tunnel without the load balancer in front. Proxied is
# mandatory: a DNS-only record resolves past the edge, and cfargotunnel.com origins are not
# directly reachable.

locals {
  # hostname -> region, flattened from extra_ingress for per-record for_each addressing.
  extra_ingress_records = merge([
    for region, entries in var.extra_ingress : {
      for entry in entries : entry.hostname => region
    }
  ]...)
}

resource "cloudflare_dns_record" "extra_ingress" {
  for_each = local.extra_ingress_records

  zone_id = var.zone_id
  name    = each.key
  type    = "CNAME"
  content = "${cloudflare_zero_trust_tunnel_cloudflared.region[each.value].id}.cfargotunnel.com"
  proxied = true
  ttl     = 1
}

# The load balancer owns the api_hostname record when it exists; without it the hostname still
# has to resolve, so a plain proxied record points at the primary region's tunnel.
resource "cloudflare_dns_record" "api_without_lb" {
  count = var.enable_load_balancer ? 0 : 1

  zone_id = var.zone_id
  name    = var.api_hostname
  type    = "CNAME"
  content = "${cloudflare_zero_trust_tunnel_cloudflared.region[var.default_pool_order[0]].id}.cfargotunnel.com"
  proxied = true
  ttl     = 1
}

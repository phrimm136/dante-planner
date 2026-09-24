# The load balancer is the front door: attaching it to the API hostname IS the cutover, and
# detaching it is the rollback primitive for as long as the accelerator is still standing.

moved {
  from = cloudflare_load_balancer_monitor.through_tunnel
  to   = cloudflare_load_balancer_monitor.through_tunnel[0]
}

resource "cloudflare_load_balancer_monitor" "through_tunnel" {
  count = var.enable_load_balancer ? 1 : 0

  account_id  = var.account_id
  description = "${var.name_prefix} regional readiness, probed through the tunnel"

  type             = "https"
  method           = "GET"
  path             = var.health_check_path
  expected_codes   = "200"
  interval         = var.monitor_interval_seconds
  retries          = var.monitor_retries
  timeout          = 5
  follow_redirects = false
  allow_insecure   = false

  # The probe reaches a tunnel origin, so it must carry the app hostname explicitly.
  header = { Host = [var.api_hostname] }
}

locals {
  write_pool_order = [
    for region in var.default_pool_order : cloudflare_load_balancer_pool.region[region].id
    if var.enable_load_balancer
  ]
  writes = "not http.request.method in {\"GET\" \"HEAD\" \"OPTIONS\"}"
  write_condition = {
    probe = "${local.writes} and any(http.request.headers[\"x-write-probe\"][*] eq \"1\")"
    all   = local.writes
  }
}

resource "cloudflare_load_balancer_pool" "region" {
  for_each = { for region, config in var.regions : region => config if var.enable_load_balancer }

  account_id      = var.account_id
  name            = "${var.name_prefix}-${each.key}"
  monitor         = cloudflare_load_balancer_monitor.through_tunnel[0].id
  enabled         = true
  minimum_origins = 1

  origins = [{
    name    = each.key
    address = "${cloudflare_zero_trust_tunnel_cloudflared.region[each.key].id}.cfargotunnel.com"
    enabled = true

    # Required: the app hostname cannot itself be a load-balancer endpoint behind a tunnel,
    # so the origin is addressed synthetically and the real Host travels in this header.
    header = { Host = [var.api_hostname] }
  }]
}

moved {
  from = cloudflare_load_balancer.api
  to   = cloudflare_load_balancer.api[0]
}

resource "cloudflare_load_balancer" "api" {
  count = var.enable_load_balancer ? 1 : 0

  zone_id = var.zone_id
  name    = var.api_hostname
  enabled = true

  # Must stay proxied. A DNS-only record resolves past the edge and never enters the tunnel.
  proxied = true

  steering_policy = "geo"
  default_pools = [
    for region in var.default_pool_order : cloudflare_load_balancer_pool.region[region].id
  ]
  fallback_pool = cloudflare_load_balancer_pool.region[var.default_pool_order[0]].id

  # Best-effort only, and deliberately not relied upon: read-your-writes is enforced at the
  # application seam, not by pinning a client to a region.
  session_affinity = "cookie"

  region_pools = {
    for code, preference in var.steering_region_pools :
    code => [for region in preference : cloudflare_load_balancer_pool.region[region].id]
  }

  rules = var.write_forwarding == "off" ? [] : [{
    name       = "writes-to-primary"
    priority   = 0
    terminates = true
    condition  = local.write_condition[var.write_forwarding]
    overrides = {
      session_affinity = "none"
      default_pools    = local.write_pool_order
      # Geo steering reads region_pools before default_pools, so default_pools alone routes nothing.
      region_pools = { for code, _ in var.steering_region_pools : code => local.write_pool_order }
    }
  }]
}

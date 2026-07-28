# One named tunnel per region. The tunnel dials out from the cluster, which is what lets the
# fleet stop accepting public inbound entirely once the accelerator and its security-group CIDR
# rules are torn down.

resource "random_password" "tunnel_secret" {
  for_each = var.regions

  length  = 64
  special = false
}

resource "cloudflare_zero_trust_tunnel_cloudflared" "region" {
  for_each = var.regions

  account_id    = var.account_id
  name          = "${var.name_prefix}-${each.key}"
  tunnel_secret = base64encode(random_password.tunnel_secret[each.key].result)

  # Remotely managed: the ingress rules below live in this state, and the pods carry only a
  # token. A locally-configured tunnel would put routing in a ConfigMap and split the source
  # of truth across two repos.
  config_src = "cloudflare"
}

resource "cloudflare_zero_trust_tunnel_cloudflared_config" "region" {
  for_each = var.regions

  account_id = var.account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.region[each.key].id

  config = {
    ingress = [
      {
        hostname = var.api_hostname
        service  = each.value.origin_service

        origin_request = {
          origin_server_name = var.origin_server_name
          ca_pool            = var.origin_ca_pool_path
          http_host_header   = var.api_hostname
        }
      },
      # cloudflared requires a final catch-all. Anything not matching the hostname above is
      # not ours to serve.
      {
        service = "http_status:404"
      },
    ]
  }
}

# The provider does not expose the token on the tunnel resource; it is served by its own data source.
data "cloudflare_zero_trust_tunnel_cloudflared_token" "region" {
  for_each = var.regions

  account_id = var.account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.region[each.key].id
}

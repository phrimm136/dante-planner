# Cloudflare Access in front of every non-production hostname.
#
# Without this the environment is open: its IdP is a stub that issues a valid token for any
# address handed to it, and the API trusts that IdP, so anyone who learns a hostname gets an
# authenticated session. Access challenges the request at the edge, before the tunnel, so the
# origin never sees unauthenticated traffic and the stub is unreachable from the internet.
#
# The suites authenticate with a service token (CF-Access-Client-Id / CF-Access-Client-Secret);
# a human reaching the app in a browser authenticates with the email policy.

resource "cloudflare_zero_trust_access_service_token" "e2e" {
  count = var.enable_access ? 1 : 0

  account_id = var.account_id
  name       = "${var.name_prefix}-e2e"
  # The secret is readable only at creation; rotating means replacing the token and re-storing it.
  duration   = "8760h"
}

resource "cloudflare_zero_trust_access_application" "guarded" {
  for_each = var.enable_access ? toset(var.access_protected_hostnames) : toset([])

  zone_id          = var.zone_id
  name             = "${var.name_prefix} ${each.value}"
  domain           = each.value
  type             = "self_hosted"
  session_duration = "24h"

  # Every request is evaluated, including the ones a browser would otherwise satisfy from cache.
  policies = [
    {
      id         = cloudflare_zero_trust_access_policy.service_token[0].id
      precedence = 1
    },
    {
      id         = cloudflare_zero_trust_access_policy.humans[0].id
      precedence = 2
    },
  ]
}

resource "cloudflare_zero_trust_access_policy" "service_token" {
  count = var.enable_access ? 1 : 0

  account_id = var.account_id
  name       = "${var.name_prefix}-e2e-service-token"
  decision   = "non_identity"

  include = [
    { service_token = { token_id = cloudflare_zero_trust_access_service_token.e2e[0].id } }
  ]
}

resource "cloudflare_zero_trust_access_policy" "humans" {
  count = var.enable_access ? 1 : 0

  account_id = var.account_id
  name       = "${var.name_prefix}-operators"
  decision   = "allow"

  include = [
    { email = { email = var.access_allowed_email } }
  ]
}

output "access_service_token" {
  description = <<-EOT
    Client id and secret the suites send as CF-Access-Client-Id / CF-Access-Client-Secret. The
    secret is returned only by the creating apply; store it (scripts/ops/provision/staging-urls-secret.sh)
    rather than reading it back later.
  EOT
  value = var.enable_access ? {
    client_id     = cloudflare_zero_trust_access_service_token.e2e[0].client_id
    client_secret = cloudflare_zero_trust_access_service_token.e2e[0].client_secret
  } : null
  sensitive = true
}

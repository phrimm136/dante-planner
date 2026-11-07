# Origin TLS material for the Traefik Gateway, replicated to Seoul so both regions'
# ESO read a local copy. The ExternalSecrets in deploy/base/origin-tls-secrets.yaml
# sync these into the cluster; without them the HTTPS listener is
# InvalidCertificateRef and every route 404s.
#
# origin-tls holds a PRIVATE KEY, so it is NOT managed by value here: the key goes
# STRAIGHT into Secrets Manager (console, or `aws secretsmanager put-secret-value`)
# and never touches terraform state or tfvars. This stack only imports the existing
# container to attach the cross-region replica — same philosophy as main.tf's
# imported prod secrets ("only the container is managed, never a secret_version").
#
# Value today = a self-signed test pair (unblocks routing + GA health, which don't
# validate the chain). For real Cloudflare Full (strict): create an Origin
# Certificate (dashboard → SSL/TLS → Origin Server), put-secret-value the new
# cert+key into danteplanner/origin-tls, revoke the old cert. Terraform (no version)
# does not fight that update. JSON fields tls_crt/tls_key match the ExternalSecret.
# Gated for the same reason as the datasource user: an environment with no edge certificate
# has nothing to adopt, and an unconditional import fails the plan on a missing object.
locals {
  origin_tls_secret = toset(var.manage_origin_tls ? ["danteplanner/origin-tls"] : [])
}

import {
  for_each = local.origin_tls_secret
  to       = aws_secretsmanager_secret.origin_tls[each.key]
  id       = each.key
}

resource "aws_secretsmanager_secret" "origin_tls" {
  for_each = local.origin_tls_secret

  name = each.key
  replica {
    region = var.replica_region
  }
  lifecycle {
    prevent_destroy = true
  }
}

moved {
  from = aws_secretsmanager_secret.origin_tls
  to   = aws_secretsmanager_secret.origin_tls["danteplanner/origin-tls"]
}

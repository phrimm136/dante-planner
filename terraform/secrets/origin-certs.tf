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
import {
  to = aws_secretsmanager_secret.origin_tls
  id = "danteplanner/origin-tls"
}

resource "aws_secretsmanager_secret" "origin_tls" {
  name = "danteplanner/origin-tls"
  replica {
    region = var.replica_region
  }
  lifecycle {
    prevent_destroy = true
  }
}

# origin-client-ca is the Cloudflare Authenticated Origin Pull CA — PUBLIC (same
# for every customer), not secret. So unlike the key above it is safe to codify by
# value: created here from a plain var and replicated. Download the PEM once from
# Cloudflare's docs. Referenced by the TLSOption (VerifyClientCertIfGiven).
resource "aws_secretsmanager_secret" "origin_client_ca" {
  name = "danteplanner/origin-client-ca"
  replica {
    region = var.replica_region
  }
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_secretsmanager_secret_version" "origin_client_ca" {
  secret_id     = aws_secretsmanager_secret.origin_client_ca.id
  secret_string = jsonencode({ tls_ca = var.origin_client_ca })
}

variable "origin_client_ca" {
  description = "Cloudflare Authenticated Origin Pull CA (PEM). PUBLIC (not secret) — safe in tfvars. Download once from Cloudflare's docs."
  type        = string
}

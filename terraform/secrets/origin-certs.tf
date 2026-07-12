# Origin TLS material for the Traefik Gateway, CREATED here (new secrets, unlike
# the imported prod secrets in main.tf) and replicated to Seoul so both regions'
# ESO read a local copy. The ExternalSecrets in deploy/base/origin-tls-secrets.yaml
# sync these into the cluster; without them the HTTPS listener is
# InvalidCertificateRef and every route 404s.
#
# Values come from sensitive tfvars (never committed). Get them from Cloudflare:
#   - origin-tls: dashboard → SSL/TLS → Origin Server → Create Certificate
#     (per-account; save the cert + private key). CF SSL/TLS mode = Full (strict).
#   - origin-client-ca: the PUBLIC, well-known Cloudflare Authenticated Origin Pull
#     CA (same for every customer; download once from Cloudflare's docs).
#
# JSON field names MUST match the ExternalSecret remoteRef.property values:
# tls_crt / tls_key (origin-tls) and tls_ca (origin-client-ca).

variable "origin_tls_cert" {
  description = "Cloudflare Origin Certificate (PEM). Sensitive — set in terraform.tfvars, never commit."
  type        = string
  sensitive   = true
}

variable "origin_tls_key" {
  description = "Private key for the Cloudflare Origin Certificate (PEM). Sensitive."
  type        = string
  sensitive   = true
}

variable "origin_client_ca" {
  description = "Cloudflare Authenticated Origin Pull CA (PEM). Public, not secret, but placed here so the mTLS trust bundle is codified and replicated with the pair."
  type        = string
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

resource "aws_secretsmanager_secret_version" "origin_tls" {
  secret_id = aws_secretsmanager_secret.origin_tls.id
  secret_string = jsonencode({
    tls_crt = var.origin_tls_cert
    tls_key = var.origin_tls_key
  })
}

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

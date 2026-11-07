variable "region" {
  description = "Primary region where the secrets already live (matches terraform/oregon)."
  type        = string
  default     = "us-west-2"
}

variable "replica_region" {
  description = "Region to replicate the secrets to, so Seoul's ESO reads a LOCAL replica (region-specific ARN) instead of a cross-region call. Matches the fleet's second region."
  type        = string
  default     = "ap-northeast-2"
}

variable "secret_names" {
  description = "Exact names of the pre-existing prod secrets to replicate. Confirm the full set against Secrets Manager before applying — a name that does not exist fails the declarative import. Values are NOT managed here (only the container + replica); AWS replicates the versions automatically."
  type        = set(string)
  default = [
    "danteplanner/backend/runtime-config",
    "danteplanner/jwt/material",
    # Telemetry credentials — containers enrolled here; values are injected by
    # scripts/ops/provision/*.sh into the PRIMARY region only (replication
    # carries the versions to Seoul). Both endpoint keys hold region-invariant
    # RDS hostnames, so replication cannot clobber a region-specific value.
    "danteplanner/grafana/cloud",
    "danteplanner/mysqld-exporter/dsn",
    # Human/agent access credentials — container created by
    # scripts/ops/provision/rds-readonly-secrets.sh before enrollment here.
    "danteplanner/rds/readonly",
    # Read by terraform/rds at plan time. Container created by
    # scripts/ops/provision/rds-master-password-secret.sh before enrollment here.
    "danteplanner/rds/master-password",
    # Both regions' tunnel tokens in one entry, keyed by region. Container created by
    # scripts/ops/provision/cloudflare-tunnel-secrets.sh before enrollment here.
    "danteplanner/cloudflare/tunnel-tokens",
  ]
}

variable "aws_account_id" {
  description = "The 12-digit AWS account this stack may apply into."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be the 12-digit AWS account number."
  }
}

variable "manage_grafana_datasource_user" {
  description = "Adopt the Grafana CloudWatch datasource user here. False for an environment with no Grafana datasource, where the import has nothing to adopt and fails the plan."
  type        = bool
  default     = true
}

variable "manage_origin_tls" {
  description = "Adopt the edge origin-certificate container here. False for an environment that has no edge certificate, where the import has nothing to adopt."
  type        = bool
  default     = true
}

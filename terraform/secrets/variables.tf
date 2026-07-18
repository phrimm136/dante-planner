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
    "danteplanner/jwt/rs256-private-key",
    "danteplanner/jwt/rs256-public-key",
    "danteplanner/jwt/encryption-key",
    # Telemetry credentials — containers enrolled here; values are injected by
    # scripts/ops/provision-*.sh into the PRIMARY region only (replication
    # carries the versions to Seoul). Both endpoint entries hold region-invariant
    # RDS hostnames, so replication cannot clobber a region-specific value.
    "danteplanner/grafana/loki-username",
    "danteplanner/grafana/loki-password",
    "danteplanner/grafana/remote-write-username",
    "danteplanner/grafana/remote-write-password",
    "danteplanner/mysqld-exporter/username",
    "danteplanner/mysqld-exporter/password",
    "danteplanner/mysqld-exporter/primary-endpoint",
    "danteplanner/mysqld-exporter/replica-endpoint",
  ]
}

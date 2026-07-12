variable "region" {
  description = "AWS region (already public in docker-compose awslogs config)."
  type        = string
  default     = "us-west-2"
}

variable "name_prefix" {
  description = "Prefix for resource names/identifiers."
  type        = string
  default     = "danteplanner"
}

# --- Networking (operator-supplied via gitignored terraform.tfvars) ---------

variable "vpc_id" {
  description = "VPC ID the EC2 instance and RDS live in."
  type        = string
}

variable "db_subnet_ids" {
  description = "Subnet IDs for the DB subnet group (>= 2 across >= 2 AZs, even for single-AZ)."
  type        = list(string)

  validation {
    condition     = length(var.db_subnet_ids) >= 2
    error_message = "RDS requires >= 2 subnets across >= 2 AZs, even for a single-AZ instance."
  }
}

variable "availability_zone" {
  description = "AZ to pin the single-AZ RDS instance to — MUST match the EC2 instance's AZ."
  type        = string
}

variable "ec2_security_group_id" {
  description = "The backend EC2 instance's security group (source of app→RDS ingress; target of the temp replication rule)."
  type        = string
}

# --- Instance shape ---------------------------------------------------------

variable "instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "engine_version" {
  description = "Fully-qualified MySQL minor (e.g. 8.0.40). Must be >= the source's captured @@version (runbook 0.2); replication only goes low->high. Required — no coarse '8.0' default, which would let AWS pick a minor possibly BELOW the source and silently stall replication."
  type        = string

  validation {
    condition     = can(regex("^8\\.0\\.\\d+$", var.engine_version))
    error_message = "engine_version must be a fully-qualified 8.0.x minor (e.g. 8.0.40), not '8.0' — pin it to match/exceed the source minor."
  }
}

variable "ca_cert_identifier" {
  description = "RDS CA bundle id the client (sslMode=VERIFY_CA) trusts. Set to the live instance's current CA to avoid an unintended rotation on apply; check `aws rds describe-certificates`."
  type        = string
  default     = "rds-ca-rsa2048-g1"
}

variable "allocated_storage" {
  description = "Initial gp3 storage (GiB)."
  type        = number
  default     = 20
}

variable "max_allocated_storage" {
  description = "Storage autoscaling cap (GiB)."
  type        = number
  default     = 100
}

# --- Database -----------------------------------------------------------------

variable "db_name" {
  description = "Initial database name (the dump loads into this schema)."
  type        = string
  default     = "danteplanner"
}

variable "master_username" {
  description = "RDS master user (admin ops only; the app uses the separate 'danteplanner' user created in runbook 0.6b)."
  type        = string
  default     = "admin"
}

variable "sql_mode" {
  description = "Set from the source's captured @@sql_mode (runbook 0.2). Empty = RDS/MySQL 8.0 default."
  type        = string
  default     = "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION"
}

# --- Operations -------------------------------------------------------------

variable "backup_retention_period" {
  description = "Automated backup retention (days). >0 also enables PITR."
  type        = number
  default     = 7
}

variable "backup_window" {
  description = "Daily backup window (UTC). 16:00-17:00 UTC = KR 01-02 / HK 00-01 / SEA 23-00 / US ~08-12 (daytime). Must not overlap maintenance_window."
  type        = string
  default     = "16:00-17:00"
}

variable "maintenance_window" {
  description = "Weekly maintenance window (UTC). Mon:18:00-19:00 UTC = KR/HK/SEA deep night (Tue 01-04 local) AND US daytime/work hours (clear of US prime-time UTC 00-05). Least-bad slot for a KR/HK/SEA-majority + US-minority audience; no window is night on all continents (that's what Multi-AZ is for). DST wobbles the US column +-1h; Asia does not observe DST."
  type        = string
  default     = "Mon:18:00-Mon:19:00"
}

variable "enable_replication_ingress" {
  description = "TEMP toggle: open the EC2 SG to RDS on 3306 so the replica can pull the binlog. true during migration (Zone 0), false at decommission (Zone 4)."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Resource tags."
  type        = map(string)
  default = {
    Project = "danteplanner"
    Phase   = "rds-migration"
  }
}

variable "fleet_peering_connection_id" {
  description = "VPC peering connection id from terraform/oregon output rds_peering_connection_id. Empty = no fleet wiring."
  type        = string
  default     = ""
}

variable "fleet_cluster_security_group_id" {
  description = "k3s fleet cluster SG (terraform/oregon output cluster_security_group_id) allowed to reach RDS on 3306."
  type        = string
  default     = ""
}

variable "fleet_vpc_cidr" {
  description = "Fleet VPC CIDR, for the RDS-side return route over the peering."
  type        = string
  default     = ""
}

variable "seoul_peering_connection_id" {
  description = "Cross-region VPC peering connection id from terraform/seoul (module.fleet output rds_peering_connection_id). Empty = no Seoul wiring."
  type        = string
  default     = ""
}

variable "seoul_fleet_cidr" {
  description = "Seoul fleet VPC CIDR, for the RDS-side return route and the CIDR-based 3306 ingress (cross-region SG references are not allowed)."
  type        = string
  default     = "10.30.0.0/16"
}

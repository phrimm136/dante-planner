terraform {
  # terraform init -backend-config=../backend.hcl
  backend "s3" {
    key                  = "rds/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
    encrypt              = true
    use_lockfile         = true
  }

  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Credentials come from the operator's AWS profile (a dedicated least-privilege
# provisioning identity assumed via STS). No role ARN is hardcoded here so this
# file is safe to publish in a public repo. See docs/runbooks/rds-migration.md.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region
}

# --- Networking (referenced, not owned) -------------------------------------

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-rds"
  subnet_ids = local.db_subnet_ids # >= 2 subnets across >= 2 AZs (RDS requirement, even for single-AZ)
  tags       = var.tags
}

# The RDS VPC, for its CIDR (exported so a cross-region fleet can route to it).
data "aws_vpc" "this" {
  id = local.vpc_id
}

# Container created by scripts/ops/provision/rds-master-password-secret.sh and enrolled in
# terraform/secrets secret_names; apply that stack before this one or the read fails.
#
# Ephemeral, so the value is held only for the duration of a run and never reaches state or a
# plan file. It can therefore be consumed only by a write-only argument.
ephemeral "aws_secretsmanager_secret_version" "master_password" {
  secret_id = var.master_password_secret_name
}

resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds"
  description = "RDS MySQL access"
  vpc_id      = local.vpc_id
  tags        = var.tags
}

resource "aws_vpc_security_group_egress_rule" "rds_all" {
  security_group_id = aws_security_group.rds.id
  description       = "RDS egress (AWS APIs)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# --- Parameter group --------------------------------------------------------

resource "aws_db_parameter_group" "this" {
  name        = "${var.name_prefix}-mysql80"
  family      = "mysql8.0"
  description = "Dante's Planner RDS MySQL 8.0 parameters"
  tags        = var.tags

  # GTID so RDS can act as an external GTID replica (auto-position cutover).
  parameter {
    name         = "gtid-mode"
    value        = "ON"
    apply_method = "pending-reboot"
  }
  parameter {
    name         = "enforce_gtid_consistency"
    value        = "ON"
    apply_method = "pending-reboot"
  }
  # Required by docs/multi-region-request-paths.md §4a, with trackSessionState=true on the app url.
  # RDS types this boolean and rejects the MySQL enum names, so it takes the ordinal: 1 = OWN_GTID.
  # Dynamic, but it seeds a SESSION variable at connect time, so pooled connections keep the old
  # value until they rotate.
  parameter {
    name  = "session_track_gtids"
    value = "1"
  }

  parameter {
    name  = "binlog_format"
    value = "ROW"
  }
  parameter {
    name  = "time_zone"
    value = "UTC"
  }
  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }
  parameter {
    name  = "collation_server"
    value = "utf8mb4_0900_ai_ci"
  }
  # Match the source (case-sensitive, Linux default). CREATION-TIME ONLY on RDS — an
  # already-running instance created with a different value must be recreated, not modified.
  parameter {
    name         = "lower_case_table_names"
    value        = "0"
    apply_method = "pending-reboot"
  }
  # Match the source's captured @@sql_mode (runbook 0.2). Default = MySQL 8.0 stock.
  parameter {
    name  = "sql_mode"
    value = var.sql_mode
  }
  # Refuse any non-TLS client connection (server-side enforcement of the app's
  # sslMode=VERIFY_CA). Dynamic — no reboot. Enable only once the app connects
  # over TLS; flipping it while the app is on a non-TLS path refuses every
  # connection. Does NOT affect RDS's outbound replication pull (that TLS is
  # governed by rds_set_external_master ssl=1).
  parameter {
    name  = "require_secure_transport"
    value = "1" # RDS canonicalizes this boolean to 1/0; "ON" causes a perpetual plan diff
  }
  # Sane slow-query diagnostics (NOT the source's 1ms-to-TABLE setting).
  parameter {
    name  = "slow_query_log"
    value = "1"
  }
  parameter {
    name  = "long_query_time"
    value = "0.1"
  }
  # Adds handler counters, tmp/sort stats, and lock wait detail to every slow
  # log entry — per-execution diagnosis without in-memory instrumentation.
  parameter {
    name  = "log_slow_extra"
    value = "ON"
  }
  parameter {
    name  = "log_output"
    value = "FILE"
  }
}

# --- The instance -----------------------------------------------------------

resource "aws_db_instance" "this" {
  identifier     = "${var.name_prefix}-mysql"
  engine         = "mysql"
  engine_version = var.engine_version
  instance_class = var.instance_class

  # availability_zone is rejected alongside multi_az; AWS owns both placements.
  multi_az            = var.multi_az
  publicly_accessible = false

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  # Pin the CA so the backend's sslMode=VERIFY_CA trusts a known bundle. `terraform plan`
  # reveals a pending cert change if this differs from the live instance's current CA;
  # match it to the ca_cert_identifier output (or `aws rds describe-certificates`).
  ca_cert_identifier = var.ca_cert_identifier

  db_name  = var.db_name # empty schema for the dump to load into
  username = var.master_username
  # manage_master_user_password is deliberately absent, not false: the provider declares it
  # ConflictsWith password, so naming it at all is an error. An instance whose credentials are
  # Secrets-Manager-managed also cannot have a read replica CREATED from it, which would leave
  # the Seoul replica unrebuildable.
  # Raising master_password_version re-sends whatever the secret currently holds. Rotating the
  # secret alone changes nothing here: Terraform stores no copy to compare against, so it cannot
  # see that the value moved.
  password_wo         = ephemeral.aws_secretsmanager_secret_version.master_password.secret_string
  password_wo_version = var.master_password_version

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  port                   = 3306

  # Ship the slow query log (slow_query_log=1 in the parameter group) to
  # CloudWatch Logs as /aws/rds/instance/<id>/slowquery. RDS applies log-export
  # changes immediately — apply_immediately=false does not defer them — and no
  # reboot is involved.
  enabled_cloudwatch_logs_exports = ["slowquery"]

  backup_retention_period    = var.backup_retention_period
  backup_window              = var.backup_window
  maintenance_window         = var.maintenance_window
  auto_minor_version_upgrade = false
  apply_immediately          = false

  # Data-protection guards (invariant I4). prevent_destroy makes `terraform
  # destroy`/replace ERROR instead of deleting the data-bearing instance.
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name_prefix}-mysql-final"

  tags = var.tags

  lifecycle {
    prevent_destroy = true
  }
}

# --- Fleet <-> RDS VPC peering (RDS side) -----------------------------------
# The peering is created + auto-accepted by terraform/oregon; here we add the
# return route from the RDS VPC to the fleet and allow the fleet cluster SG to
# reach RDS on 3306 (an SG reference resolves across same-region peering). All
# guarded so a plain RDS apply (no fleet) is unaffected until fleet_* are set.
data "aws_route_tables" "rds_vpc" {
  count  = var.fleet_peering_connection_id != "" ? 1 : 0
  vpc_id = local.vpc_id
}

resource "aws_route" "rds_to_fleet" {
  count                     = var.fleet_peering_connection_id != "" ? length(data.aws_route_tables.rds_vpc[0].ids) : 0
  route_table_id            = tolist(data.aws_route_tables.rds_vpc[0].ids)[count.index]
  destination_cidr_block    = var.fleet_vpc_cidr
  vpc_peering_connection_id = var.fleet_peering_connection_id
}

resource "aws_vpc_security_group_ingress_rule" "fleet_to_rds" {
  count                        = var.fleet_cluster_security_group_id != "" ? 1 : 0
  security_group_id            = aws_security_group.rds.id
  description                  = "k3s fleet app/data nodes to RDS over VPC peering"
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
  referenced_security_group_id = var.fleet_cluster_security_group_id
}

# --- Seoul fleet <-> RDS VPC peering (RDS side) -------------------------------
# The cross-region peering is created + accepted by terraform/seoul; here we add
# the return route from the RDS VPC to the Seoul fleet and open 3306 to Seoul's
# VPC CIDR. Seoul's cluster SG lives in ap-northeast-2 and SG references do NOT
# resolve across cross-region peering, so this rule is CIDR-based (unlike the
# same-region Oregon rule above). All guarded so a plain RDS apply is unaffected
# until seoul_peering_connection_id is set.
data "aws_route_tables" "rds_vpc_seoul" {
  count  = var.seoul_peering_connection_id != "" ? 1 : 0
  vpc_id = local.vpc_id
}

resource "aws_route" "rds_to_seoul_fleet" {
  count                     = var.seoul_peering_connection_id != "" ? length(data.aws_route_tables.rds_vpc_seoul[0].ids) : 0
  route_table_id            = tolist(data.aws_route_tables.rds_vpc_seoul[0].ids)[count.index]
  destination_cidr_block    = var.seoul_fleet_cidr
  vpc_peering_connection_id = var.seoul_peering_connection_id
}

resource "aws_vpc_security_group_ingress_rule" "seoul_fleet_to_rds" {
  count             = var.seoul_peering_connection_id != "" ? 1 : 0
  security_group_id = aws_security_group.rds.id
  description       = "Seoul k3s fleet (VPC CIDR) to RDS over cross-region peering; SG reference impossible cross-region"
  from_port         = 3306
  to_port           = 3306
  ip_protocol       = "tcp"
  cidr_ipv4         = var.seoul_fleet_cidr
}

terraform {
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
# file is safe to publish in a public repo. See docs/tasks/030-rds-migration/runbook.md.
provider "aws" {
  region = var.region
}

# --- Networking (referenced, not owned) -------------------------------------

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-rds"
  subnet_ids = var.db_subnet_ids # >= 2 subnets across >= 2 AZs (RDS requirement, even for single-AZ)
  tags       = var.tags
}

# RDS-side security group: the backend on EC2 reaches RDS on 3306.
resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds"
  description = "RDS MySQL access"
  vpc_id      = var.vpc_id
  tags        = var.tags
}

resource "aws_vpc_security_group_ingress_rule" "app_to_rds" {
  security_group_id            = aws_security_group.rds.id
  description                  = "Backend (EC2) to RDS MySQL"
  referenced_security_group_id = var.ec2_security_group_id
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "rds_all" {
  security_group_id = aws_security_group.rds.id
  description       = "RDS egress (replication pull to source + AWS APIs)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# TEMPORARY: lets RDS (replica) pull the binlog FROM the on-box source MySQL.
# Toggled on only during the migration (Zone 0) and removed at decommission
# (Zone 4) by setting enable_replication_ingress=false and re-applying.
# Added to the EXISTING EC2 security group so we don't own/replace it.
resource "aws_vpc_security_group_ingress_rule" "rds_to_source" {
  count                        = var.enable_replication_ingress ? 1 : 0
  security_group_id            = var.ec2_security_group_id
  description                  = "TEMP: RDS replica pulls binlog from source MySQL (remove after cutover)"
  referenced_security_group_id = aws_security_group.rds.id
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
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

  # Single-AZ, pinned to the EC2 instance's AZ (avoids cross-AZ latency + transfer).
  availability_zone   = var.availability_zone
  multi_az            = false
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
  # Master password is generated + held in Secrets Manager by AWS; never in state/tfvars.
  manage_master_user_password = true

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
  vpc_id = var.vpc_id
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

# Seoul data layer: a cross-region RDS READ replica of the Oregon primary, and
# the SG/subnet-group it needs. Single-AZ (Multi-AZ is enabled only after a real
# cutover, never during seed). The region-local parameter group carries the
# primary's hardened posture (gtid_mode=ON, enforce_gtid_consistency=ON,
# require_secure_transport=1) so a someday-flip can PROMOTE this replica without
# a config gap. Seoul serves READS from here; WRITES go cross-region to the
# primary (no autonomous promotion — requirements/mechanics §0 FORBIDDEN).

# primary_rds_arn is auto-resolved from the RDS stack's state (remote-state.tf),
# not a tfvars hand-off.

variable "rds_param_group_family" {
  description = "DB parameter group family for the replica; must match the primary's engine family."
  type        = string
  default     = "mysql8.0"
}

variable "rds_instance_class" {
  description = "Replica instance class."
  type        = string
  default     = "db.t4g.micro"
}

# A cross-region read replica of an ENCRYPTED primary must itself be encrypted,
# and KMS keys are region-scoped — the source's us-west-2 key cannot be used in
# ap-northeast-2. Use Seoul's AWS-managed RDS key (no CMK to provision); the
# replica is re-encrypted under it. kms_key_id set => storage_encrypted.
data "aws_kms_alias" "rds" {
  name = "alias/aws/rds"
}

resource "aws_db_subnet_group" "replica" {
  name       = "${var.name_prefix}-seoul-replica"
  subnet_ids = module.fleet.public_subnet_ids
  tags       = var.tags
}

resource "aws_security_group" "replica" {
  name        = "${var.name_prefix}-seoul-rds-replica"
  description = "Seoul RDS read replica: 3306 from the Seoul cluster nodes only."
  vpc_id      = module.fleet.vpc_id
  tags        = var.tags
}

resource "aws_vpc_security_group_ingress_rule" "replica_mysql" {
  security_group_id            = aws_security_group.replica.id
  description                  = "MySQL from Seoul fleet nodes"
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
  referenced_security_group_id = module.fleet.cluster_security_group_id
}

resource "aws_vpc_security_group_egress_rule" "replica_all" {
  security_group_id = aws_security_group.replica.id
  description       = "Replica egress (replication pull from primary + AWS APIs)"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_db_parameter_group" "replica" {
  name        = "${var.name_prefix}-seoul-replica"
  family      = var.rds_param_group_family
  description = "Seoul replica: primary's hardened GTID + TLS posture so a promote is gap-free."

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
    name  = "require_secure_transport"
    value = "1"
  }

  tags = var.tags
}

resource "aws_db_instance" "replica" {
  identifier          = "${var.name_prefix}-mysql-seoul"
  replicate_source_db = data.terraform_remote_state.rds.outputs.rds_arn
  instance_class      = var.rds_instance_class

  # Single-AZ during seed; Multi-AZ is a post-cutover action, not a seed default.
  multi_az            = false
  publicly_accessible = false

  # Required for a cross-region replica of an encrypted source (see the alias above).
  # BOTH must be set explicitly: kms_key_id alone leaves storage_encrypted omitted,
  # which the provider reads as null and plans true->null — a ForceNew that would
  # destroy+recreate the replica (resyncing from the primary, resetting ReplicaLag).
  storage_encrypted = true
  kms_key_id        = data.aws_kms_alias.rds.target_key_arn

  db_subnet_group_name   = aws_db_subnet_group.replica.name
  vpc_security_group_ids = [aws_security_group.replica.id]
  parameter_group_name   = aws_db_parameter_group.replica.name

  # A replica takes no final snapshot (it is reproducible from the primary).
  skip_final_snapshot = true
  apply_immediately   = true

  tags = var.tags
}

output "replica_endpoint" {
  description = "Seoul RDS read-replica endpoint. Seoul reads resolve here; writes stay on the primary."
  value       = aws_db_instance.replica.address
}

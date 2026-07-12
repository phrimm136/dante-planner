# Seoul data layer: a cross-region RDS READ replica of the Oregon primary, and
# the SG/subnet-group it needs. Single-AZ (Multi-AZ is enabled only after a real
# cutover, never during seed). The region-local parameter group carries the
# primary's hardened posture (gtid_mode=ON, enforce_gtid_consistency=ON,
# require_secure_transport=1) so a someday-flip can PROMOTE this replica without
# a config gap. Seoul serves READS from here; WRITES go cross-region to the
# primary (no autonomous promotion — requirements/mechanics §0 FORBIDDEN).

variable "primary_rds_arn" {
  description = "ARN of the Oregon primary aws_db_instance (cross-region replica source). Cross-region replicas require the full ARN, not the identifier. Get it from the terraform/rds stack; set in terraform.tfvars."
  type        = string
}

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
  replicate_source_db = var.primary_rds_arn
  instance_class      = var.rds_instance_class

  # Single-AZ during seed; Multi-AZ is a post-cutover action, not a seed default.
  multi_az            = false
  publicly_accessible = false

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

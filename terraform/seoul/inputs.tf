data "aws_ssm_parameter" "ingress_allowed_cidrs" {
  provider = aws.rds
  name     = "/danteplanner/${var.environment}/seoul/ingress_allowed_cidrs"
}

locals {
  ingress_allowed_cidrs = split(",", data.aws_ssm_parameter.ingress_allowed_cidrs.insecure_value)
}

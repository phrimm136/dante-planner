data "aws_ssm_parameter" "ingress_allowed_cidrs" {
  name = "/danteplanner/${var.environment}/oregon/ingress_allowed_cidrs"
}

locals {
  ingress_allowed_cidrs = split(",", data.aws_ssm_parameter.ingress_allowed_cidrs.insecure_value)
}

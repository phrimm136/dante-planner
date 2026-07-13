data "aws_caller_identity" "current" {}

# Latest AL2023 arm64 AMI, resolved at plan time from the SSM public parameter.
data "aws_ssm_parameter" "node_ami" {
  name = var.ami_ssm_parameter
}

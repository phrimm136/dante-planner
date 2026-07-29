# Auto-resolve cross-stack values from Oregon + RDS state instead of hand-copying them into
# tfvars — honors "few manual touch": no output→tfvars copy step.
locals {
  tf_state_bucket = "${var.name_prefix}-tfstate-${var.aws_account_id}"
}

data "terraform_remote_state" "oregon" {
  backend   = "s3"
  workspace = terraform.workspace
  config = {
    bucket               = local.tf_state_bucket
    key                  = "oregon/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
  }
}

data "terraform_remote_state" "rds" {
  backend   = "s3"
  workspace = terraform.workspace
  config = {
    bucket               = local.tf_state_bucket
    key                  = "rds/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
  }
}

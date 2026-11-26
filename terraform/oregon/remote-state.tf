data "terraform_remote_state" "rds" {
  backend   = "s3"
  workspace = terraform.workspace
  config = {
    bucket               = "${var.name_prefix}-tfstate-${var.aws_account_id}"
    key                  = "rds/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
  }
}

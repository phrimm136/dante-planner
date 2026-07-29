# Auto-resolve the Oregon ingress endpoint from Oregon's state (no tfvars copy).
# Seoul's ingress stays a var (seoul_ingress_instance_id) because GA may be applied
# single-region before Seoul's state exists.
data "terraform_remote_state" "oregon" {
  backend   = "s3"
  workspace = terraform.workspace
  config = {
    bucket               = "${var.name_prefix}-tfstate-${var.aws_account_id}"
    key                  = "oregon/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
  }
}

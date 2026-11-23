terraform {
  # terraform init -backend-config=../backend.<account>.hcl
  #
  # State lives in the bucket this stack creates. Local state is used only for the first apply in
  # a new account, before that bucket exists: apply with a backend_override.tf holding
  # `terraform { backend "local" {} }`, delete it, then move the state into the new bucket with
  # scripts/ops/provision/bootstrap-state-to-s3.sh.
  backend "s3" {
    key                  = "state-backend/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
    encrypt              = true
    use_lockfile         = true
  }

  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.71"
    }
  }
}

# assume_role is empty when applying with credentials already belonging to the target account, and
# set when reaching a member account from the management account.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region

  dynamic "assume_role" {
    for_each = var.assume_role_arn == "" ? [] : [1]
    content {
      role_arn = var.assume_role_arn
    }
  }
}

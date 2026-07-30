terraform {
  # terraform init -backend-config=../backend.log-archive.hcl
  #
  # State lives in this account's own bucket, created by terraform/state-backend. Applying this
  # stack therefore comes after that one, for this account.
  #
  # The backend resolves credentials independently of the provider below, so the provider's
  # assume_role does not reach it. The backend config file carries its own assume_role alongside
  # the bucket; without it, init authenticates as whoever is ambient and is denied.
  backend "s3" {
    key                  = "log-archive/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
    encrypt              = true
    use_lockfile         = true
  }

  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# assume_role is empty when the credentials already belong to the log archive, and set when
# reaching it from the management account.
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

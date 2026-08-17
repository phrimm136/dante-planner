terraform {
  # terraform init -backend-config=../backend.hcl
  backend "s3" {
    key                  = "registry/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
    encrypt              = true
    use_lockfile         = true
  }

  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.60"
    }
  }
}

# Applies in the account that owns the registry. The repositories themselves are created outside
# Terraform and stay that way; only who may pull from them is managed here.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region
}

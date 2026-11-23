terraform {
  # terraform init -backend-config=../backend.management.hcl
  #
  # This stack applies in the management account, which already owns the state bucket it
  # stores state in.
  backend "s3" {
    key                  = "organization/terraform.tfstate"
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

# Organizations is global; the provider region only hosts the API calls.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region
}

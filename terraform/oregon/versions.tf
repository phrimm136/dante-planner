terraform {
  # terraform init -backend-config=../backend.<account>.hcl
  backend "s3" {
    key                  = "oregon/terraform.tfstate"
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
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

# Credentials come from the operator's AWS profile (a dedicated least-privilege
# provisioning identity assumed via STS). No role ARN is hardcoded here so this
# file is safe to publish in a public repo. Matches terraform/rds.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region
}

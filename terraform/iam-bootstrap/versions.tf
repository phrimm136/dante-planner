terraform {
  # terraform init -backend-config=../backend.<account>.hcl
  #
  # State lives in the account's state bucket, which terraform/state-backend creates first.
  backend "s3" {
    key                  = "iam-bootstrap/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
    encrypt              = true
    use_lockfile         = true
  }

  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.71"
    }
  }
}

# This stack creates only global IAM identities, so a single default provider is
# enough (no us-east-1 alias — that exists in terraform/oregon solely for its
# billing alarm). Credentials come from an ADMIN principal's AWS profile: this is
# the one stack an admin applies by hand to mint the provisioning role the other
# stacks assume. No role ARN is hardcoded here, so this file is safe to publish
# in a public repo. Matches terraform/rds and terraform/oregon.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region
}

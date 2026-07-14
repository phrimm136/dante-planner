terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
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
  region = var.region
}

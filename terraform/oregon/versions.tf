terraform {
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
  region = var.region
}

# AWS/Billing EstimatedCharges is published only in us-east-1; the billing alarm
# must watch the metric there regardless of the fleet's region.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}

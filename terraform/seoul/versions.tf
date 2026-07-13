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

# Seoul (second region). The fleet runs here.
provider "aws" {
  region = var.region
}

# Billing alarm's EstimatedCharges metric is us-east-1-only (module passes this
# through). Kept even in the Seoul stack so the module's monitoring.tf resolves.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}

# The RDS (prod) region — us-west-2. Holds the accepter side of the cross-region
# Seoul-VPC <-> RDS-VPC peering, which cannot auto_accept (AWS requires the
# accepter to run in the peer's region). This is what keeps peering a single
# unattended apply instead of a console click.
provider "aws" {
  alias  = "rds"
  region = var.rds_region
}

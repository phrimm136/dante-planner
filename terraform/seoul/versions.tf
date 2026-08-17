terraform {
  # terraform init -backend-config=../backend.hcl
  backend "s3" {
    key                  = "seoul/terraform.tfstate"
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

# Seoul (second region). The fleet runs here.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region
}

# Billing alarm's EstimatedCharges metric is us-east-1-only (module passes this
# through). Kept even in the Seoul stack so the module's monitoring.tf resolves.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  alias               = "us_east_1"
  region              = "us-east-1"
}

# The RDS (prod) region — us-west-2. Holds the accepter side of the cross-region
# Seoul-VPC <-> RDS-VPC peering, which cannot auto_accept (AWS requires the
# accepter to run in the peer's region). This is what keeps peering a single
# unattended apply instead of a console click.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  alias               = "rds"
  region              = var.rds_region
}

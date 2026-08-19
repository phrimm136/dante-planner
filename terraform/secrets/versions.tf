terraform {
  # terraform init -backend-config=../backend.hcl
  backend "s3" {
    key                  = "secrets/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
    encrypt              = true
    use_lockfile         = true
  }

  # >= 1.7 for config-driven import blocks WITH for_each (declarative import runs
  # inside the normal apply — no separate `terraform import` command, so enabling
  # replication stays a single unattended apply).
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.60"
    }
  }
}

# Durable, applied-once stack (like terraform/oregon-edge): it takes ownership of
# the pre-existing prod secrets ONLY to attach a cross-region replica, and is
# never part of the rebuild-disposable fleet lifecycle. prevent_destroy guards
# the production secrets against an accidental destroy.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region
}

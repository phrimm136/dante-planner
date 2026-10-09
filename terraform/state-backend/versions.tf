terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.60"
    }
  }
}

# No backend block: this stack creates the bucket every other stack stores state in, so storing
# its own state there is circular. One workspace per account keeps those local state files apart
# (terraform.tfstate.d/<account>/terraform.tfstate).

# assume_role is empty when applying with credentials already belonging to the target account, and
# set when reaching a member account from the management account.
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

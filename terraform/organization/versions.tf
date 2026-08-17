terraform {
  # terraform init -backend-config=../backend.hcl
  #
  # This stack applies in the management account, which is the account that already owns
  # the state bucket, so it has none of iam-bootstrap's circularity and stores state
  # remotely like every other stack.
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
      version = "~> 6.60"
    }
  }
}

# Organizations is global; the provider region only hosts the API calls.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region
}

terraform {
  # terraform init -backend-config=../backend.hcl
  backend "s3" {
    key                  = "global-accelerator/terraform.tfstate"
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

# Global Accelerator is a global service; the provider region only hosts the API
# calls. Durable, applied-once edge stack (like terraform/oregon-edge): the
# anycast IPs are the two-region front door and must survive either region's
# rebuild, so they live outside the fleet stacks.
provider "aws" {
  allowed_account_ids = [var.aws_account_id]
  region              = var.region
}

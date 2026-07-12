terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Global Accelerator is a global service; the provider region only hosts the API
# calls. Durable, applied-once edge stack (like terraform/oregon-edge): the
# anycast IPs are the two-region front door and must survive either region's
# rebuild, so they live outside the fleet stacks.
provider "aws" {
  region = var.region
}

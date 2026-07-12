terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Separate state from terraform/oregon ON PURPOSE. This is the DURABLE edge layer:
# the fleet stack is rebuild-disposable (destroy+apply must re-converge), so
# anything whose identity must survive a rebuild — the public IP clients reach —
# lives here and is never destroyed by the fleet's lifecycle. Apply this ONCE;
# feed its allocation id into the fleet's var.ingress_eip_allocation_id.
provider "aws" {
  region = var.region
}

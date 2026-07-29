terraform {
  # terraform init -backend-config=../backend.hcl
  backend "s3" {
    key                  = "cloudflare/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
    encrypt              = true
    use_lockfile         = true
  }

  required_version = ">= 1.6"
  required_providers {
    cloudflare = {
      source = "cloudflare/cloudflare"
      # Current major. This stack is greenfield, so it starts here rather than on 4.x:
      # the 4-to-5 schema break is a migration cost, and there is no state to migrate.
      version = "~> 5.22"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

# The edge stack is global and applied once, like the accelerator stack it replaces:
# the tunnels and the load balancer are the two-region front door and must survive
# either region's rebuild, so they live outside the per-region fleet stacks.
provider "cloudflare" {
  api_token = var.cloudflare_api_token
}

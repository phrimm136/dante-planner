# Auto-resolve cross-stack values from Oregon + RDS state instead of hand-copying
# them into tfvars — honors "few manual touch": no output→tfvars copy step. Local
# backend matches the current stacks (terraform.tfstate on disk); switch the
# backend/config block here if the stacks move to a remote (S3) backend.
data "terraform_remote_state" "oregon" {
  backend = "local"
  config = {
    path = "../oregon/terraform.tfstate"
  }
}

data "terraform_remote_state" "rds" {
  backend = "local"
  config = {
    path = "../rds/terraform.tfstate"
  }
}

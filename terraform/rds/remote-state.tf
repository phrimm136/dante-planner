# A fleet state object that was never written fails terraform_remote_state even with defaults, so
# each read is gated on the bucket listing holding that exact key.
locals {
  tf_state_bucket  = "${var.name_prefix}-tfstate-${var.aws_account_id}"
  oregon_state_key = terraform.workspace == "default" ? "oregon/terraform.tfstate" : "env/${terraform.workspace}/oregon/terraform.tfstate"
  seoul_state_key  = terraform.workspace == "default" ? "seoul/terraform.tfstate" : "env/${terraform.workspace}/seoul/terraform.tfstate"

  oregon_pcx_id        = try(data.terraform_remote_state.oregon[0].outputs.rds_peering_connection_id, "")
  oregon_cluster_sg_id = try(data.terraform_remote_state.oregon[0].outputs.cluster_security_group_id, "")
  oregon_cidr          = try(data.terraform_remote_state.oregon[0].outputs.vpc_cidr, "")
  seoul_pcx_id         = try(data.terraform_remote_state.seoul[0].outputs.rds_peering_connection_id, "")
  seoul_cidr           = try(data.terraform_remote_state.seoul[0].outputs.vpc_cidr, "")
}

data "aws_s3_objects" "oregon_state" {
  bucket = local.tf_state_bucket
  prefix = local.oregon_state_key
}

data "aws_s3_objects" "seoul_state" {
  bucket = local.tf_state_bucket
  prefix = local.seoul_state_key
}

data "terraform_remote_state" "oregon" {
  count     = contains(data.aws_s3_objects.oregon_state.keys, local.oregon_state_key) ? 1 : 0
  backend   = "s3"
  workspace = terraform.workspace
  config = {
    bucket               = local.tf_state_bucket
    key                  = "oregon/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
  }
  defaults = {
    rds_peering_connection_id = ""
    cluster_security_group_id = ""
    vpc_cidr                  = ""
  }
}

data "terraform_remote_state" "seoul" {
  count     = contains(data.aws_s3_objects.seoul_state.keys, local.seoul_state_key) ? 1 : 0
  backend   = "s3"
  workspace = terraform.workspace
  config = {
    bucket               = local.tf_state_bucket
    key                  = "seoul/terraform.tfstate"
    region               = "us-west-2"
    workspace_key_prefix = "env"
  }
  defaults = {
    rds_peering_connection_id = ""
    vpc_cidr                  = ""
  }
}

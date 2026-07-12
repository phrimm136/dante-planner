module "fleet" {
  source = "../modules/fleet"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  region                          = var.region
  name_prefix                     = var.name_prefix
  vpc_cidr                        = var.vpc_cidr
  availability_zones              = var.availability_zones
  public_subnet_cidrs             = var.public_subnet_cidrs
  ingress_allowed_cidrs           = var.ingress_allowed_cidrs
  instance_type                   = var.instance_type
  ami_ssm_parameter               = var.ami_ssm_parameter
  ssh_key_name                    = var.ssh_key_name
  app_asg_min_size                = var.app_asg_min_size
  app_asg_desired_capacity        = var.app_asg_desired_capacity
  app_asg_max_size                = var.app_asg_max_size
  backend_image_repo              = var.backend_image_repo
  gitops_repo_url                 = var.gitops_repo_url
  gitops_target_revision          = var.gitops_target_revision
  argocd_version                  = var.argocd_version
  gateway_api_version             = var.gateway_api_version
  external_secrets_chart_version  = var.external_secrets_chart_version
  ecr_credential_provider_version = var.ecr_credential_provider_version
  rs256_private_key_secret_name   = var.rs256_private_key_secret_name
  billing_alarm_threshold         = var.billing_alarm_threshold
  alarm_sns_topic_arn             = var.alarm_sns_topic_arn
  etcd_snapshot_retention         = var.etcd_snapshot_retention
  tags                            = var.tags
  rds_vpc_id                      = var.rds_vpc_id
  ingress_eip_allocation_id       = var.ingress_eip_allocation_id
}

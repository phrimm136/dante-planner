# Address moves from the terraform/oregon -> module.fleet extraction. Every managed
# resource keeps its state entry (move, not destroy+create). Data sources need none.

moved {
  from = aws_autoscaling_group.app
  to   = module.fleet.aws_autoscaling_group.app
}

moved {
  from = aws_cloudwatch_metric_alarm.auto_recovery
  to   = module.fleet.aws_cloudwatch_metric_alarm.auto_recovery
}

moved {
  from = aws_cloudwatch_metric_alarm.billing
  to   = module.fleet.aws_cloudwatch_metric_alarm.billing
}

moved {
  from = aws_eip_association.ingress
  to   = module.fleet.aws_eip_association.ingress
}

moved {
  from = aws_iam_instance_profile.app
  to   = module.fleet.aws_iam_instance_profile.app
}

moved {
  from = aws_iam_instance_profile.cp
  to   = module.fleet.aws_iam_instance_profile.cp
}

moved {
  from = aws_iam_instance_profile.data
  to   = module.fleet.aws_iam_instance_profile.data
}

moved {
  from = aws_iam_instance_profile.ingress
  to   = module.fleet.aws_iam_instance_profile.ingress
}

moved {
  from = aws_iam_role.app
  to   = module.fleet.aws_iam_role.app
}

moved {
  from = aws_iam_role.cp
  to   = module.fleet.aws_iam_role.cp
}

moved {
  from = aws_iam_role.data
  to   = module.fleet.aws_iam_role.data
}

moved {
  from = aws_iam_role.ingress
  to   = module.fleet.aws_iam_role.ingress
}

moved {
  from = aws_iam_role_policy.app_join
  to   = module.fleet.aws_iam_role_policy.app_join
}

moved {
  from = aws_iam_role_policy.app_secrets
  to   = module.fleet.aws_iam_role_policy.app_secrets
}

moved {
  from = aws_iam_role_policy.cp
  to   = module.fleet.aws_iam_role_policy.cp
}

moved {
  from = aws_iam_role_policy.data_join
  to   = module.fleet.aws_iam_role_policy.data_join
}

moved {
  from = aws_iam_role_policy.ingress_join
  to   = module.fleet.aws_iam_role_policy.ingress_join
}

moved {
  from = aws_iam_role_policy_attachment.ecr_read
  to   = module.fleet.aws_iam_role_policy_attachment.ecr_read
}

moved {
  from = aws_iam_role_policy_attachment.ssm_core
  to   = module.fleet.aws_iam_role_policy_attachment.ssm_core
}

moved {
  from = aws_instance.cp
  to   = module.fleet.aws_instance.cp
}

moved {
  from = aws_instance.data
  to   = module.fleet.aws_instance.data
}

moved {
  from = aws_instance.ingress
  to   = module.fleet.aws_instance.ingress
}

moved {
  from = aws_internet_gateway.this
  to   = module.fleet.aws_internet_gateway.this
}

moved {
  from = aws_launch_template.app
  to   = module.fleet.aws_launch_template.app
}

moved {
  from = aws_route.fleet_to_rds
  to   = module.fleet.aws_route.fleet_to_rds
}

moved {
  from = aws_route.public_igw
  to   = module.fleet.aws_route.public_igw
}

moved {
  from = aws_route_table.public
  to   = module.fleet.aws_route_table.public
}

moved {
  from = aws_route_table_association.public
  to   = module.fleet.aws_route_table_association.public
}

moved {
  from = aws_s3_bucket.etcd_snapshots
  to   = module.fleet.aws_s3_bucket.etcd_snapshots
}

moved {
  from = aws_s3_bucket_lifecycle_configuration.etcd_snapshots
  to   = module.fleet.aws_s3_bucket_lifecycle_configuration.etcd_snapshots
}

moved {
  from = aws_s3_bucket_public_access_block.etcd_snapshots
  to   = module.fleet.aws_s3_bucket_public_access_block.etcd_snapshots
}

moved {
  from = aws_s3_bucket_server_side_encryption_configuration.etcd_snapshots
  to   = module.fleet.aws_s3_bucket_server_side_encryption_configuration.etcd_snapshots
}

moved {
  from = aws_s3_bucket_versioning.etcd_snapshots
  to   = module.fleet.aws_s3_bucket_versioning.etcd_snapshots
}

moved {
  from = aws_security_group.cluster
  to   = module.fleet.aws_security_group.cluster
}

moved {
  from = aws_security_group.ingress
  to   = module.fleet.aws_security_group.ingress
}

moved {
  from = aws_ssm_parameter.k3s_token
  to   = module.fleet.aws_ssm_parameter.k3s_token
}

moved {
  from = aws_ssm_parameter.kubeconfig
  to   = module.fleet.aws_ssm_parameter.kubeconfig
}

moved {
  from = aws_subnet.public
  to   = module.fleet.aws_subnet.public
}

moved {
  from = aws_vpc.this
  to   = module.fleet.aws_vpc.this
}

moved {
  from = aws_vpc_peering_connection.rds
  to   = module.fleet.aws_vpc_peering_connection.rds
}

moved {
  from = aws_vpc_security_group_egress_rule.cluster_all
  to   = module.fleet.aws_vpc_security_group_egress_rule.cluster_all
}

moved {
  from = aws_vpc_security_group_ingress_rule.cluster_self
  to   = module.fleet.aws_vpc_security_group_ingress_rule.cluster_self
}

moved {
  from = aws_vpc_security_group_ingress_rule.ingress_https
  to   = module.fleet.aws_vpc_security_group_ingress_rule.ingress_https
}

moved {
  from = random_password.k3s_token
  to   = module.fleet.random_password.k3s_token
}


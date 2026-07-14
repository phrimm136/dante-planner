# Associate the Seoul VPC with Oregon's private hosted zone so Seoul pods resolve
# redis-auth.oregon.danteplanner.internal to the Oregon data node over the peering.
# Zone id comes from Oregon's state (no dynamic value committed); same-account
# cross-region association is direct. The Oregon side ignores vpc changes so this
# association is not fought on its applies.
resource "aws_route53_zone_association" "seoul" {
  zone_id    = data.terraform_remote_state.oregon.outputs.internal_zone_id_for_seoul
  vpc_id     = module.fleet.vpc_id
  vpc_region = var.region
}

# Stable internal name for the Seoul RDS read replica. The committed ConfigMap
# references this alias so the AWS-generated endpoint never appears in the public
# repo, and a replica rebuild re-points here instead of editing manifests. CNAME
# is safe under sslMode=VERIFY_CA (chain-only; no hostname verification).
resource "aws_route53_record" "mysql_replica" {
  zone_id = data.terraform_remote_state.oregon.outputs.internal_zone_id_for_seoul
  name    = "mysql-replica.seoul.danteplanner.internal"
  type    = "CNAME"
  ttl     = 60
  records = [aws_db_instance.replica.address]
}

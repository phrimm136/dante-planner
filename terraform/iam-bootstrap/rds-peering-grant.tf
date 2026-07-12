# Reproducible grant: the externally-created role that applies terraform/rds
# needs ec2 route actions to add the fleet <-> RDS peering return route. Managed
# here as code instead of a one-off `aws iam put-role-policy`, so a rebuild
# reproduces it. Gated on the role name being supplied (empty = skip), and the
# name comes from a variable so it never lands in a committed file.

variable "rds_provisioner_role_name" {
  description = "Name of the existing role that applies terraform/rds. Empty = skip the peering grant. Set in terraform.tfvars (gitignored)."
  type        = string
  default     = ""
}

data "aws_iam_policy_document" "rds_fleet_peering" {
  count = var.rds_provisioner_role_name != "" ? 1 : 0
  statement {
    sid    = "FleetPeeringRoutes"
    effect = "Allow"
    actions = [
      "ec2:CreateRoute",
      "ec2:DeleteRoute",
      "ec2:ReplaceRoute",
      "ec2:DescribeRouteTables",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "rds_fleet_peering" {
  count  = var.rds_provisioner_role_name != "" ? 1 : 0
  name   = "fleet-peering"
  role   = var.rds_provisioner_role_name
  policy = data.aws_iam_policy_document.rds_fleet_peering[0].json
}

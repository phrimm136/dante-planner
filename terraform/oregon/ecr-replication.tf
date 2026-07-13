# ECR cross-region replication: us-west-2 (primary registry) -> ap-northeast-2
# (Seoul), so Seoul app nodes pull the backend image from a LOCAL replica and
# self-heal without reaching Oregon's registry (requirements: "ECR replicated so
# Seoul self-heals without Oregon"). Registry-level config — it replicates the
# shared repo WITHOUT this stack owning the repo (still a data source in the
# module), so the unattended-rebuild guarantee is untouched. Root-only (not the
# reusable module): only the PRIMARY registry replicates outward; Seoul must not
# author a mirror rule back.
data "aws_caller_identity" "current" {}

resource "aws_ecr_replication_configuration" "backend" {
  replication_configuration {
    rule {
      destination {
        region      = var.seoul_region
        registry_id = data.aws_caller_identity.current.account_id
      }
      repository_filter {
        filter      = var.backend_image_repo
        filter_type = "PREFIX_MATCH"
      }
    }
  }
}

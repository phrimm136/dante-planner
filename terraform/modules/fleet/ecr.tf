# Backend image registry — shared with the single-region deploy (deploy.yml),
# created outside this stack, and (in the second region) populated by ECR
# cross-region replication. The fleet never manages it. The URL is COMPUTED from
# account + region + repo name rather than data-sourced: a data lookup would fail
# at plan time in a region where the replicated repo does not exist YET (Seoul
# before the first image replicates), and the module only needs the URL string,
# never the resource. Same value as the old data source in Oregon.
locals {
  backend_ecr_repository_url = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com/${var.backend_image_repo}"
}

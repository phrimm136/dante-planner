# Backend image registry — shared with the single-region deploy (deploy.yml) and
# created outside this stack. The fleet only READS it (data source), so a rebuild
# never tries to create (name collision) or destroy a pre-existing, shared repo —
# that is what makes the unattended rebuild reproducible. The untagged-expiry
# lifecycle policy belongs with the repo's authoritative owner, not this
# ephemeral fleet stack.
data "aws_ecr_repository" "backend" {
  name = var.backend_image_repo
}

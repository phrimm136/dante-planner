# Backend image registry. Same repository name as the existing single-region
# deploy (.github/workflows/deploy.yml). The CI arm64 workflow pushes here.
# Cross-region replication to Seoul is Phase 14, not authored here.
#
# NOTE: if this repository already exists from the task-014 deploy, import it
# (`terraform import aws_ecr_repository.backend danteplanner-backend`) rather
# than letting apply fail on a name collision.
resource "aws_ecr_repository" "backend" {
  name                 = var.backend_image_repo
  image_tag_mutability = "IMMUTABLE"
  tags                 = var.tags

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Expire untagged image layers so the registry does not accumulate cost.
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 14 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 14
        }
        action = { type = "expire" }
      }
    ]
  })
}

# A repository policy governs the repository's own contents. It cannot grant
# ecr:GetAuthorizationToken, which is registry-wide rather than repository-scoped and is therefore
# granted by the pulling account's own node role (modules/fleet attaches the managed read policy).
# Both halves are required: without the token action the pull never authenticates, and without this
# policy it authenticates and is then refused.
data "aws_iam_policy_document" "organization_pull" {
  statement {
    sid    = "AllowPullFromOrganization"
    effect = "Allow"

    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]

    # A wildcard principal narrowed by organization membership rather than an account list: an
    # account vended later inherits the grant, and one removed loses it, with nothing to re-apply.
    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:PrincipalOrgID"
      values   = [var.organization_id]
    }
  }
}

resource "aws_ecr_repository_policy" "organization_pull" {
  for_each = toset(var.pullable_repositories)

  repository = each.value
  policy     = data.aws_iam_policy_document.organization_pull.json
}

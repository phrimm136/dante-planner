locals {
  # CloudTrail conditions on the trail that is writing. The ARN is derived rather than read from
  # the trail resource, which lives in another account's state: without it the bucket policy and
  # the trail would each be waiting for the other.
  trail_arn = "arn:aws:cloudtrail:${var.region}:${var.management_account_id}:trail/${var.trail_name}"
}

resource "aws_s3_bucket" "trail" {
  bucket = "${var.name_prefix}-org-trail-${var.aws_account_id}"
  tags   = merge(var.tags, { Name = "${var.name_prefix}-org-trail" })

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "trail" {
  bucket                  = aws_s3_bucket.trail.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Versioning is what makes an overwrite recoverable; the policy below only stops deletion.
resource "aws_s3_bucket_versioning" "trail" {
  bucket = aws_s3_bucket.trail.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "trail" {
  bucket = aws_s3_bucket.trail.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

data "aws_iam_policy_document" "trail" {
  statement {
    sid       = "AWSCloudTrailAclCheck"
    effect    = "Allow"
    actions   = ["s3:GetBucketAcl"]
    resources = [aws_s3_bucket.trail.arn]

    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.trail_arn]
    }
  }

  statement {
    sid     = "AWSCloudTrailWrite"
    effect  = "Allow"
    actions = ["s3:PutObject"]

    # An organization trail files member records under the organization id and the management
    # account's own under its account id, so both prefixes are grants rather than one.
    resources = [
      "${aws_s3_bucket.trail.arn}/AWSLogs/${var.organization_id}/*",
      "${aws_s3_bucket.trail.arn}/AWSLogs/${var.management_account_id}/*",
    ]

    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.trail_arn]
    }
  }

  # Scoped to object deletion only. Denying bucket-policy actions here would leave no principal
  # able to repair a mistake in this document.
  statement {
    sid       = "DenyObjectDeletionFromOutside"
    effect    = "Deny"
    actions   = ["s3:DeleteObject", "s3:DeleteObjectVersion"]
    resources = ["${aws_s3_bucket.trail.arn}/*"]

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    condition {
      test     = "StringNotEquals"
      variable = "aws:PrincipalAccount"
      values   = [var.aws_account_id]
    }
  }
}

resource "aws_s3_bucket_policy" "trail" {
  bucket = aws_s3_bucket.trail.id
  policy = data.aws_iam_policy_document.trail.json

  depends_on = [aws_s3_bucket_public_access_block.trail]
}

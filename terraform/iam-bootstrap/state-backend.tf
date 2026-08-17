# Remote state backend for every other stack. This one stays on local state — it creates the bucket.
#
# Only the account this stack was originally applied in creates its bucket here; accounts added
# later use terraform/state-backend, which owns the same five resources and nothing else. The
# provisioning policy addresses the bucket by its computed name either way, so the grant does not
# depend on which stack built it.

resource "aws_s3_bucket" "tf_state" {
  count  = var.create_state_bucket ? 1 : 0
  bucket = local.tf_state_bucket
  tags   = merge(var.tags, { Name = "${var.name_prefix}-tfstate" })

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "tf_state" {
  count                   = var.create_state_bucket ? 1 : 0
  bucket                  = aws_s3_bucket.tf_state[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "tf_state" {
  count  = var.create_state_bucket ? 1 : 0
  bucket = aws_s3_bucket.tf_state[0].id
  versioning_configuration {
    status = "Enabled"
  }
}

# State holds the RDS master password and the Cloudflare tunnel secrets in plaintext.
resource "aws_s3_bucket_server_side_encryption_configuration" "tf_state" {
  count  = var.create_state_bucket ? 1 : 0
  bucket = aws_s3_bucket.tf_state[0].id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "tf_state" {
  count  = var.create_state_bucket ? 1 : 0
  bucket = aws_s3_bucket.tf_state[0].id
  rule {
    id     = "expire-noncurrent"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

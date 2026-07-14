# etcd snapshot target for the embedded etcd on the CP node (k3s --etcd-s3).
# The CP node runs k3s server with --cluster-init so it has embedded etcd;
# snapshots ship here on a schedule so a CP rebuild can restore cluster state.
resource "aws_s3_bucket" "etcd_snapshots" {
  bucket = "${var.name_prefix}-${var.region_name_suffix}-etcd-snapshots-${data.aws_caller_identity.current.account_id}"
  tags   = merge(var.tags, { Name = "${var.name_prefix}-${var.region_name_suffix}-etcd-snapshots" })
}

resource "aws_s3_bucket_public_access_block" "etcd_snapshots" {
  bucket                  = aws_s3_bucket.etcd_snapshots.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "etcd_snapshots" {
  bucket = aws_s3_bucket.etcd_snapshots.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "etcd_snapshots" {
  bucket = aws_s3_bucket.etcd_snapshots.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# k3s prunes on its own retention count, but expire noncurrent versions so the
# versioned bucket does not grow unbounded.
resource "aws_s3_bucket_lifecycle_configuration" "etcd_snapshots" {
  bucket = aws_s3_bucket.etcd_snapshots.id
  rule {
    id     = "expire-noncurrent"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

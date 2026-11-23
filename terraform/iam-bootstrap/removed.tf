removed {
  from = aws_s3_bucket.tf_state
  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket_public_access_block.tf_state
  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket_versioning.tf_state
  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket_server_side_encryption_configuration.tf_state
  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket_lifecycle_configuration.tf_state
  lifecycle {
    destroy = false
  }
}

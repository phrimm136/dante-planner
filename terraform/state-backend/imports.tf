import {
  to = aws_s3_bucket.tf_state
  id = local.tf_state_bucket
}

import {
  to = aws_s3_bucket_public_access_block.tf_state
  id = local.tf_state_bucket
}

import {
  to = aws_s3_bucket_versioning.tf_state
  id = local.tf_state_bucket
}

import {
  to = aws_s3_bucket_server_side_encryption_configuration.tf_state
  id = local.tf_state_bucket
}

import {
  to = aws_s3_bucket_lifecycle_configuration.tf_state
  id = local.tf_state_bucket
}

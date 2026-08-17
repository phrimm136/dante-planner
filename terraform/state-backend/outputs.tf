output "tf_state_bucket" {
  description = "Bucket backing every other stack in this account. Write it into the account's backend config file, then init those stacks against it."
  value       = aws_s3_bucket.tf_state.id
}

output "trail_bucket" {
  description = "Destination the organization trail writes to. The trail resource in the management account takes this as its bucket name."
  value       = aws_s3_bucket.trail.id
}

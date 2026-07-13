output "ingress_eip_allocation_id" {
  description = "Feed into terraform/oregon var.ingress_eip_allocation_id (the fleet manages only the association)."
  value       = aws_eip.ingress.id
}

output "ingress_eip_public_ip" {
  description = "The stable public IP. Point Cloudflare's api A-record here."
  value       = aws_eip.ingress.public_ip
}

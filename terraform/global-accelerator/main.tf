# Global Accelerator: two-region entry plane. Proximity routing sends each client
# to its nearest healthy region; on regional failure GA re-steers in ~30s. EC2
# instance endpoints (the ingress nodes) preserve the client IP so Cloudflare's
# and the app's IP logic keep working. Health check probes /healthz-local (LOCAL
# Spring readiness only) so a region limping along on cross-region fallback reads
# as unhealthy and GA routes clients straight to the healthy region.

resource "aws_globalaccelerator_accelerator" "this" {
  name            = "${var.name_prefix}-entry"
  ip_address_type = "IPV4"
  enabled         = true
}

resource "aws_globalaccelerator_listener" "https" {
  accelerator_arn = aws_globalaccelerator_accelerator.this.id
  client_affinity = "SOURCE_IP"
  protocol        = "TCP"

  port_range {
    from_port = 443
    to_port   = 443
  }
}

# Oregon endpoint group (always present).
resource "aws_globalaccelerator_endpoint_group" "oregon" {
  listener_arn                  = aws_globalaccelerator_listener.https.id
  endpoint_group_region         = var.oregon_region
  health_check_protocol         = "HTTPS"
  health_check_port             = 443
  health_check_path             = var.health_check_path
  health_check_interval_seconds = 10
  threshold_count               = 3

  endpoint_configuration {
    endpoint_id                    = data.terraform_remote_state.oregon.outputs.ingress_instance_id
    weight                         = 128
    client_ip_preservation_enabled = true
  }
}

# Seoul endpoint group — only once Seoul's ingress exists (single-region until).
resource "aws_globalaccelerator_endpoint_group" "seoul" {
  count                         = var.seoul_ingress_instance_id != "" ? 1 : 0
  listener_arn                  = aws_globalaccelerator_listener.https.id
  endpoint_group_region         = var.seoul_region
  health_check_protocol         = "HTTPS"
  health_check_port             = 443
  health_check_path             = var.health_check_path
  health_check_interval_seconds = 10
  threshold_count               = 3

  endpoint_configuration {
    endpoint_id                    = var.seoul_ingress_instance_id
    weight                         = 128
    client_ip_preservation_enabled = true
  }
}

output "accelerator_dns_name" {
  description = "GA DNS name. Point Cloudflare's api record at this (or use the GA static anycast IPs) once both regions are healthy."
  value       = aws_globalaccelerator_accelerator.this.dns_name
}

output "accelerator_static_ips" {
  description = "GA static anycast IPs — the durable two-region front door."
  value       = aws_globalaccelerator_accelerator.this.ip_sets[0].ip_addresses
}

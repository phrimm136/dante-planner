output "tunnel_ids" {
  description = "Region -> tunnel id. The pool origin address is <id>.cfargotunnel.com."
  value       = { for region, tunnel in cloudflare_zero_trust_tunnel_cloudflared.region : region => tunnel.id }
}

output "tunnel_tokens" {
  description = <<-EOT
    Region -> tunnel token for the cloudflared Deployment.

    Do not paste these into a manifest. Put each in its region's Secrets Manager entry and let
    the ExternalSecret pull it, matching how the other runtime secrets reach the cluster.
  EOT
  value       = { for region, t in data.cloudflare_zero_trust_tunnel_cloudflared_token.region : region => t.token }
  sensitive   = true
}

output "load_balancer_hostname" {
  description = "The public hostname the load balancer answers on."
  value       = cloudflare_load_balancer.api.name
}

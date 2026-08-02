variable "cloudflare_api_token" {
  description = <<-EOT
    Scoped API token: Tunnel edit, Load Balancer edit, DNS edit. Custody in an untracked
    terraform.tfvars (see terraform/.gitignore) — never a default here.
  EOT
  type        = string
  sensitive   = true
}

variable "account_id" {
  description = "Cloudflare account id owning the tunnels, the monitor, and the pools."
  type        = string
}

variable "zone_id" {
  description = "Cloudflare zone id for the apex domain the API hostname belongs to."
  type        = string
}

variable "name_prefix" {
  description = "Name prefix, matching the fleets."
  type        = string
  default     = "danteplanner"
}

variable "api_hostname" {
  description = <<-EOT
    The public API hostname the load balancer answers on. It is also sent as the Host header
    on every pool origin: a tunnel origin is addressed as <uuid>.cfargotunnel.com, so without
    the override the origin would receive that synthetic name instead of the app hostname.
  EOT
  type        = string
  default     = "api.dante-planner.com"
}

variable "regions" {
  description = <<-EOT
    One entry per region, each with the origin cloudflared forwards to.

    The origin is the ingress node's Traefik listener, not the backend Service: the tunnel
    replaces the accelerator, not the ingress. Traefik terminates TLS with the origin-tls
    certificate, which is why the service is https and why origin_server_name below must match
    that certificate.

    Traefik is a hostNetwork DaemonSet with no Service, so nothing in cluster DNS resolves to it;
    deploy/base/cloudflared.yaml shares that host network and reaches it on the loopback.
  EOT
  type = map(object({
    origin_service = string
  }))
  default = {
    oregon = { origin_service = "https://localhost:443" }
    seoul  = { origin_service = "https://localhost:443" }
  }
}

variable "origin_server_name" {
  description = <<-EOT
    SNI cloudflared presents to Traefik, matching the origin-tls certificate's subject.
    Mismatch here fails the origin handshake and every route answers 502.
  EOT
  type        = string
  default     = "api.dante-planner.com"
}

variable "origin_ca_pool_path" {
  description = <<-EOT
    Path INSIDE the cloudflared container to the CA bundle that signs the origin-tls
    certificate. The file is mounted by the cloudflared Deployment; this value only tells
    cloudflared where to look. Empty disables custom CA verification, which is not acceptable
    for a private origin.
  EOT
  type        = string
  default     = "/etc/cloudflared/origin-ca.pem"
}

variable "health_check_path" {
  description = <<-EOT
    Monitor path, probed through the tunnel. /healthz-local is the region's LOCAL readiness
    through Traefik: it deliberately excludes the cross-region fallback route, so a region that
    can only serve via the other region reports unhealthy and the load balancer steers clients
    to the healthy region directly instead of chaining a hop.
  EOT
  type        = string
  default     = "/healthz-local"
}

variable "monitor_interval_seconds" {
  description = <<-EOT
    Probe interval. 60s is the assumed floor on this plan; confirm the actual minimum the
    subscription offers before relying on the failover window this implies.
  EOT
  type        = number
  default     = 60
}

variable "monitor_retries" {
  description = "Consecutive failed probes before an origin is considered down."
  type        = number
  default     = 2
}

variable "enable_access" {
  description = <<-EOT
    Put Cloudflare Access in front of access_protected_hostnames. Mandatory for any environment
    whose IdP is the stub: it authenticates whoever asks, so an unguarded hostname is an open
    session factory. Left false for production, which fronts a real IdP and real users.
  EOT
  type        = bool
  default     = false
}

variable "access_protected_hostnames" {
  description = "Hostnames Access guards. Keep every non-production hostname here, the IdP's included."
  type        = list(string)
  default     = []
}

variable "access_allowed_email" {
  description = "The operator address allowed to reach the guarded hostnames in a browser."
  type        = string
  default     = ""
}

variable "enable_load_balancer" {
  description = <<-EOT
    Create the monitor, the pools, and the load balancer. When false, api_hostname is served by
    a plain proxied record pointing at the first default_pool_order region's tunnel — every
    hostname still enters through a tunnel, but without the paid front door or its failover.
  EOT
  type        = bool
  default     = true
}

variable "extra_ingress" {
  description = <<-EOT
    Region -> additional hostname routes appended to that region's tunnel ingress ahead of the
    catch-all. Each hostname also gets a proxied DNS record at that region's tunnel, which is
    what makes it a deterministic region pin. The service normally stays the Traefik loopback;
    routing is carried by the per-entry Host header while SNI stays origin_server_name, so the
    origin certificate never needs to name these hosts.
  EOT
  type = map(list(object({
    hostname = string
    service  = string
  })))
  default = {}
}

variable "steering_region_pools" {
  description = <<-EOT
    Cloudflare region code -> ordered pool preference. The first entry serves; the rest are the
    failover chain, which is how a region loss degrades to a cross-region hop rather than to an
    error page. NEAS is Northeast Asia; WNAM and ENAM are western and eastern North America.
  EOT
  type        = map(list(string))
  default = {
    NEAS = ["seoul", "oregon"]
    WNAM = ["oregon", "seoul"]
    ENAM = ["oregon", "seoul"]
  }
}

variable "default_pool_order" {
  description = "Pool preference for traffic from regions not named in steering_region_pools."
  type        = list(string)
  default     = ["oregon", "seoul"]
}

variable "e2e_endpoints" {
  description = <<-EOT
    Environment-variable name -> URL, handed to the suites through Secrets Manager. Declared as a
    variable so the deployed hostnames live only in an untracked tfvars: this repository is public
    and the environment authenticates through a stub IdP, so naming a host in a committed file
    publishes the way in.
  EOT
  type        = map(string)
  default     = {}
}

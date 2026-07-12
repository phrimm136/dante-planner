variable "region" {
  description = "Provider region for the GA API calls (GA itself is global)."
  type        = string
  default     = "us-west-2"
}

variable "name_prefix" {
  description = "Name prefix, matching the fleets."
  type        = string
  default     = "danteplanner"
}

variable "oregon_region" {
  description = "Primary region."
  type        = string
  default     = "us-west-2"
}

variable "seoul_region" {
  description = "Second region."
  type        = string
  default     = "ap-northeast-2"
}

# oregon_ingress_instance_id is auto-resolved from the Oregon stack's state
# (remote-state.tf), not a tfvars hand-off. Seoul stays an optional input because
# its state may not exist yet (single-region-first); reading a not-yet-created
# remote state would error at plan.
variable "seoul_ingress_instance_id" {
  description = "Seoul ingress EC2 instance id (terraform/seoul output ingress_instance_id). Empty until Seoul is provisioned; GA runs single-region until then. The one remaining hand-off, unavoidable because Seoul's state need not exist when GA is first applied."
  type        = string
  default     = ""
}

variable "health_check_path" {
  description = "GA health-check path. /healthz-local = LOCAL Spring readiness through Traefik (the cross-region fallback route is excluded, so a region serving only via fallback looks unhealthy and GA steers clients to the healthy region directly)."
  type        = string
  default     = "/healthz-local"
}

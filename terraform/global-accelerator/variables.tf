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

variable "oregon_ingress_instance_id" {
  description = "Oregon ingress EC2 instance id (terraform/oregon output ingress_instance_id). Set in terraform.tfvars."
  type        = string
}

variable "seoul_ingress_instance_id" {
  description = "Seoul ingress EC2 instance id (terraform/seoul — expose ingress_instance_id). Empty until Seoul is provisioned; GA runs single-region until then."
  type        = string
  default     = ""
}

variable "health_check_path" {
  description = "GA health-check path. /healthz-local = LOCAL Spring readiness through Traefik (the cross-region fallback route is excluded, so a region serving only via fallback looks unhealthy and GA steers clients to the healthy region directly)."
  type        = string
  default     = "/healthz-local"
}

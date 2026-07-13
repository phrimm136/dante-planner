# Seoul-specific values. The fleet SHAPE is the shared module; only these region
# differences are set here (the "Seoul = tfvars" goal). Shared operational
# defaults (instance_type, pinned versions, ASG sizing) fall through to the module.

variable "name_prefix" {
  description = "Prefix for resource names/identifiers (matches the fleet)."
  type        = string
  default     = "danteplanner"
}

variable "region" {
  description = "Seoul region."
  type        = string
  default     = "ap-northeast-2"
}

variable "gitops_target_revision" {
  description = "Git revision Seoul's ArgoCD tracks. MUST match the branch that carries deploy/overlays/seoul (Oregon currently tracks feat/034-oregon-k3s; keep them consistent or merge to main first)."
  type        = string
  default     = "main"
}

variable "availability_zones" {
  description = "Seoul AZs the public subnets span."
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "vpc_cidr" {
  description = "Seoul fleet VPC CIDR. Must NOT overlap Oregon (10.20.0.0/16) or RDS (172.31.0.0/16)."
  type        = string
  default     = "10.30.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Seoul public subnet CIDRs, one per AZ."
  type        = list(string)
  default     = ["10.30.0.0/24", "10.30.1.0/24"]
}

variable "rds_region" {
  description = "Region of the primary RDS VPC (cross-region peer). Seoul reaches the primary for write-global traffic."
  type        = string
  default     = "us-west-2"
}

variable "rds_vpc_id" {
  description = "Primary RDS VPC id to peer with (cross-region). Set in terraform.tfvars — do not commit."
  type        = string
}

variable "ingress_allowed_cidrs" {
  description = "CIDRs allowed to reach the Seoul ingress on 443 (Cloudflare edge + Global Accelerator health-check ranges), same posture as Oregon."
  type        = list(string)
}

variable "tags" {
  description = "Resource tags."
  type        = map(string)
  default = {
    Project = "danteplanner"
    Phase   = "seoul-k3s-fleet"
  }
}

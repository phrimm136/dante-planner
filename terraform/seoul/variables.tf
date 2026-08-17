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
  description = "Git revision Seoul's ArgoCD tracks. MUST match Oregon's gitops_target_revision — both regions sync the same branch (dev pre-promotion, main after)."
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

variable "aws_account_id" {
  description = "The 12-digit AWS account this stack may apply into."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be the 12-digit AWS account number."
  }
}

variable "backend_ecr_account_id" {
  description = "Account owning the backend ECR registry. Empty = the account this stack is applied into."
  type        = string
  default     = ""
}


# Mirrors terraform/oregon so both regions expose the same knobs. Values stay per-region
# where the topology is deliberately asymmetric; the point is that a divergence must now be
# written down rather than arising from one caller being unable to say it.
variable "redis_cross_region_cidr" {
  description = "Peer-region fleet CIDR admitted to the auth Redis NodePort (Seoul's fleet CIDR, 10.30.0.0/16, so its replica can REPLICAOF and its pods can write auth state to the Oregon primary). Empty (default) = no rule; the auth Redis stays region-private. Set in terraform.tfvars when Seoul goes live. Never 0.0.0.0/0."
  type        = string
  default     = ""
}

variable "instance_type" {
  description = "Instance type for every fleet node (arm64/Graviton — CI builds arm64 images)."
  type        = string
  default     = "t4g.small"
}

variable "ami_ssm_parameter" {
  description = "SSM public parameter resolving to the latest Amazon Linux 2023 arm64 AMI. Pinning the parameter (not an AMI id) keeps the fleet current without hardcoding."
  type        = string
  default     = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

variable "ssh_key_name" {
  description = "Optional EC2 key pair name for break-glass SSH. Empty = no key (SSM Session Manager only)."
  type        = string
  default     = ""
}

variable "app_asg_min_size" {
  description = "App ASG minimum. Spring runs as a DaemonSet; node count = pod count."
  type        = number
  default     = 1
}

variable "app_asg_desired_capacity" {
  description = "App ASG desired capacity. Bump to 2 for surge deploys."
  type        = number
  default     = 1
}

variable "app_asg_max_size" {
  description = "App ASG maximum. max=2 doubles as deploy surge and load headroom."
  type        = number
  default     = 2
}

variable "backend_image_repo" {
  description = "ECR repository name for the backend image. Matches the existing single-region deploy (.github/workflows/deploy.yml pushes danteplanner-backend)."
  type        = string
  default     = "danteplanner-backend"
}

variable "gitops_repo_url" {
  description = "Git repository ArgoCD syncs from (this repo). The CP clones it at boot to apply the root Application, which then points ArgoCD at deploy/overlays/oregon."
  type        = string
  default     = "https://github.com/phrimm136/dante-planner.git"
}

variable "argocd_version" {
  description = "Pinned ArgoCD release for the core-install manifest applied at CP bootstrap."
  type        = string
  default     = "v2.13.2"
}

variable "gateway_api_version" {
  description = "Pinned Gateway API CRD release. The CP applies the standard CRDs at bootstrap (k3s ships none; --disable traefik removes the bundled path) so Traefik's Gateway/HTTPRoute resources have their kinds."
  type        = string
  default     = "v1.1.0"
}

variable "external_secrets_chart_version" {
  description = "Pinned External Secrets Operator Helm chart version. The CP installs ESO (CRDs + controller) at bootstrap, pinned to role=app nodes so its SDK-default-chain credential is the app node role granted secretsmanager:GetSecretValue."
  type        = string
  default     = "0.10.4"
}

variable "ecr_credential_provider_version" {
  description = "Pinned cloud-provider-aws ecr-credential-provider release. The kubelet on app nodes calls this binary to exchange the node instance profile for a short-lived ECR token, so containerd can pull the private backend image (no imagePullSecret)."
  type        = string
  default     = "v1.31.0"
}

variable "rs256_private_key_secret_name" {
  description = "AWS Secrets Manager secret name holding the RS256 JWT private key. Read by the ESO controller via the node instance profile (no-IRSA deviation, see README)."
  type        = string
  default     = "danteplanner/jwt/rs256-private-key"
}

variable "billing_alarm_threshold" {
  description = "CloudWatch billing alarm threshold in USD (steady-state bill is ~$145-190/mo)."
  type        = number
  default     = 200
}

variable "alarm_sns_topic_arn" {
  description = "Optional SNS topic ARN for billing + instance auto-recovery alarm notifications. Empty = alarms visible in console but send no notification."
  type        = string
  default     = ""
}

variable "etcd_snapshot_retention" {
  description = "Number of etcd snapshots k3s retains in S3 before pruning."
  type        = number
  default     = 5
}

variable "enable_global_accelerator" {
  description = "Allowlist the Global Accelerator health-check prefix list on the ingress security group. Matches terraform/oregon; false retires the rule."
  type        = bool
  default     = false
}

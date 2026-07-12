variable "region" {
  description = "AWS region for the Oregon k3s fleet (primary region)."
  type        = string
  default     = "us-west-2"
}

variable "name_prefix" {
  description = "Prefix for resource names/identifiers."
  type        = string
  default     = "danteplanner"
}

# --- Network ----------------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR for the dedicated Oregon fleet VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "AZs to spread the public subnets across (app ASG spans both; pets pin to the first)."
  type        = list(string)
  default     = ["us-west-2a", "us-west-2b"]

  validation {
    condition     = length(var.availability_zones) >= 2
    error_message = "Provide >= 2 AZs so the app ASG can place nodes across zones."
  }
}

variable "public_subnet_cidrs" {
  description = "Public subnet CIDRs, one per AZ in availability_zones order. Public (no NAT gateway) is a deliberate cost choice; SGs are the boundary and Cloudflare mTLS is the real gate."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]
}

variable "ingress_allowed_cidrs" {
  description = "CIDRs allowed to reach the ingress node on 443. Production value = Cloudflare edge ranges + Global Accelerator health-check ranges only (requirements: Entry plane). The mTLS Authenticated Origin Pull at the origin is the real gate; this SG is defense-in-depth."
  type        = list(string)
}

# --- Instance shape ---------------------------------------------------------

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

# --- App ASG (the only scaling dial: node count = Spring pod count) ----------

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

# --- Container registry -----------------------------------------------------

variable "backend_image_repo" {
  description = "ECR repository name for the backend image. Matches the existing single-region deploy (.github/workflows/deploy.yml pushes danteplanner-backend)."
  type        = string
  default     = "danteplanner-backend"
}

# --- GitOps (ArgoCD core bootstrap on the CP node) --------------------------

variable "gitops_repo_url" {
  description = "Git repository ArgoCD syncs from (this repo). The CP clones it at boot to apply the root Application, which then points ArgoCD at deploy/overlays/oregon."
  type        = string
  default     = "https://github.com/phrimm136/dante-planner.git"
}

variable "gitops_target_revision" {
  description = "Git revision ArgoCD tracks (branch or tag)."
  type        = string
  default     = "main"
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

# --- Secrets (External Secrets Operator source) -----------------------------

variable "rs256_private_key_secret_name" {
  description = "AWS Secrets Manager secret name holding the RS256 JWT private key. Read by the ESO controller via the node instance profile (no-IRSA deviation, see README)."
  type        = string
  default     = "danteplanner/jwt/rs256-private-key"
}

# --- Observability & ops ----------------------------------------------------

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

variable "tags" {
  description = "Resource tags."
  type        = map(string)
  default = {
    Project = "danteplanner"
    Phase   = "oregon-k3s-fleet"
  }
}

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

variable "region_name_suffix" {
  description = "Short region label baked into resource names, tags, and SSM paths (IAM role names are account-global, so this MUST differ per region to avoid collisions). Oregon = 'oregon' (default keeps existing names byte-identical); Seoul passes 'seoul'."
  type        = string
  default     = "oregon"
}

variable "rds_vpc_cidr" {
  description = "RDS VPC CIDR for the fleet→RDS route. Empty (Oregon, same-region) = data-source it from rds_vpc_id. Set (Seoul, cross-region) = passed explicitly, since a data.aws_vpc lookup only resolves in the provider's own region."
  type        = string
  default     = ""
}

variable "rds_peer_region" {
  description = "Region of the RDS VPC when it is in a DIFFERENT region than this fleet (cross-region peering, e.g. Seoul→us-west-2 RDS). null = same-region peering (Oregon). When set, rds_peering_auto_accept must be false and the caller provides an aws_vpc_peering_connection_accepter in the RDS region."
  type        = string
  default     = null
}

variable "rds_peering_auto_accept" {
  description = "true for same-region peering (Oregon auto-accepts). false for cross-region (Seoul): AWS requires an explicit accepter in the peer's region."
  type        = bool
  default     = true
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

variable "enable_global_accelerator" {
  description = "When true, allowlist the AWS-managed Global Accelerator prefix list on the ingress SG (443) so GA's direct health checks reach /healthz-local. Set alongside applying terraform/global-accelerator; default false keeps the single-region SG surface unchanged."
  type        = bool
  default     = false
}

variable "redis_cross_region_cidr" {
  description = "Peer-region fleet CIDR admitted to the auth Redis NodePort over VPC peering (Oregon passes Seoul's 10.30.0.0/16 so the Seoul replica can REPLICAOF and Seoul pods can write blacklist/rotation/tombstone state). Empty (default) creates NO rule — the auth Redis stays region-private and the single-region SG surface is unchanged. The auth Redis holds revocation state: scope this to the one peer fleet CIDR, never a broad range."
  type        = string
  default     = ""

  validation {
    condition     = var.redis_cross_region_cidr != "0.0.0.0/0"
    error_message = "The auth Redis NodePort must never be open to 0.0.0.0/0; set the peer region's fleet CIDR only."
  }
}

variable "redis_auth_nodeport" {
  description = "NodePort exposing the auth Redis primary on the fleet nodes. MUST match the nodePort in deploy/overlays/oregon/redis-auth-nodeport.yaml."
  type        = number
  default     = 31637
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
  description = "App ASG desired capacity when the group is created. Afterwards the deploy workflow moves it between 1 and 2, and Terraform ignores it."
  type        = number
  default     = 1
}

variable "app_asg_max_size" {
  description = "App ASG maximum. max=2 doubles as deploy surge and load headroom."
  type        = number
  default     = 2
}

# --- Container registry -----------------------------------------------------

variable "backend_ecr_account_id" {
  description = "Account owning the backend ECR registry. Empty = the account this fleet is applied into. Set it when the fleet lives elsewhere (a staging account pulling production images), and grant that account pull access on the repository policy."
  type        = string
  default     = ""
}

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

variable "traefik_version" {
  description = "Pinned Traefik release tag for the traefik.io CRDs installed at CP bootstrap (must match the traefik:<tag> image in deploy/base/traefik-controller.yaml). Enables the mTLS TLSOption."
  type        = string
  default     = "v3.1"
}

variable "external_secrets_chart_version" {
  description = "Pinned External Secrets Operator Helm chart version. The CP installs ESO (CRDs + controller) at bootstrap, pinned to role=app nodes so its SDK-default-chain credential is the app node role granted secretsmanager:GetSecretValue."
  type        = string
  default     = "0.10.4"
}

variable "k3s_version" {
  description = "The k3s release the nodes install. A kubelet may be older than the API server, never newer, and a new app node boots on every deploy, so the release the fleet installs moves only together with the control plane: a new value here takes effect when node_generation.cp is raised, and until then every plan warns that the two differ. Raising node_generation.cp replaces the control plane with an empty cluster today, so an upgrade waits for a control-plane replacement that restores etcd and keeps its address."
  type        = string
  default     = "v1.36.3+k3s1"
}

variable "ecr_credential_provider_version" {
  description = "Pinned cloud-provider-aws ecr-credential-provider release. The kubelet on app nodes calls this binary to exchange the node instance profile for a short-lived ECR token, so containerd can pull the private backend image (no imagePullSecret)."
  type        = string
  default     = "v1.31.0"
}

# --- Observability & ops ----------------------------------------------------

variable "alarm_sns_topic_arn" {
  description = "Optional SNS topic ARN for instance auto-recovery alarm notifications. Empty = alarms visible in console but send no notification."
  type        = string
  default     = ""
}

variable "etcd_snapshot_retention" {
  description = "Number of scheduled etcd snapshots k3s keeps on the CP disk and in S3 before pruning."
  type        = number
  default     = 5
}

variable "etcd_snapshot_interval_hours" {
  description = "Hours between scheduled etcd snapshots. The CP boot script writes the cron `0 */<hours> * * *` into /etc/rancher/k3s/config.yaml, and the etcd-snapshot-deadman alert (deploy/grafana/import-alert-rules.sh) and scripts/ops/fleet-verify.sh read this default through scripts/ops/lib/etcd-interval.sh, so it is set here and nowhere else. A running CP keeps the schedule it booted with."
  type        = number
  default     = 6

  validation {
    condition     = contains([1, 2, 3, 4, 6, 8, 12, 24], var.etcd_snapshot_interval_hours)
    error_message = "etcd_snapshot_interval_hours must divide 24 (1, 2, 3, 4, 6, 8, 12 or 24), so the cron fires at an even interval."
  }
}

variable "tags" {
  description = "Resource tags."
  type        = map(string)
  default = {
    Project = "danteplanner"
    Phase   = "oregon-k3s-fleet"
  }
}

variable "rds_vpc_id" {
  description = "RDS (prod) VPC id to peer with for private RDS access. Set in the stack's gitignored var-file — do not commit."
  type        = string
}

variable "k3s_token_version" {
  description = "Raise to rotate the cluster join token. The token is regenerated every run and written only when this changes; nodes joined under the previous token must rejoin."
  type        = number
  default     = 1
}

variable "app_secret_name_patterns" {
  description = "Secrets Manager name patterns an app node may read, matching the ExternalSecret keys under deploy/. Adding a secret to the cluster without adding it here fails the pull at runtime, not at apply."
  type        = list(string)
  default = [
    "danteplanner/backend/*",
    "danteplanner/cloudflare/tunnel-tokens-*",
    "danteplanner/grafana/cloud-*",
    "danteplanner/jwt/material-*",
    "danteplanner/mysqld-exporter/dsn-*",
    "danteplanner/origin-tls-*",
  ]
}

variable "kubeconfig_seed_version" {
  description = "Raise to overwrite the admin kubeconfig with the bootstrap placeholder, discarding what the CP wrote. Only useful when re-seeding a rebuilt control plane."
  type        = number
  default     = 1
}

variable "node_generation" {
  description = "One number per pet instance. Raising a number replaces that instance, which then boots from the newest AMI and the current boot script. Nothing else replaces a pet for a new image or script."
  type = object({
    cp      = number
    data    = number
    ingress = number
  })
}

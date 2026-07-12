variable "region" {
  description = "AWS region for the provider. IAM is global, so this only sets the API endpoint region; the provisioning policy scopes regional ARNs (SSM/KMS) with var.region too."
  type        = string
  default     = "us-west-2"
}

variable "name_prefix" {
  description = "Resource-name prefix. MUST match terraform/oregon's name_prefix so the provisioning policy's iam:*/PassRole and S3 statements resource-scope to the exact role/instance-profile/bucket names that stack creates (<name_prefix>-oregon-*)."
  type        = string
  default     = "danteplanner"
}

variable "role_name" {
  description = "Name of the provisioning role this stack creates. The Oregon CI workflow and the operator's laptop AWS profile assume this role."
  type        = string
  default     = "dante-oregon-provisioner"
}

variable "trusted_admin_principal_arn" {
  description = "IAM/SSO principal ARN allowed to sts:AssumeRole the provisioning role from a laptop (the human admin who runs the initial `terraform apply` of terraform/oregon). Real value lives in gitignored terraform.tfvars — never commit it. No default (public-repo invariant)."
  type        = string
}

variable "github_oidc_subject" {
  description = "GitHub OIDC `sub` claim the trust policy matches (StringLike, so a wildcard is allowed). Default scopes assumption to the main branch. An Environment-scoped subject like `repo:phrimm136/dante-planner:environment:oregon` is tighter — pair it with a GitHub `oregon` environment so only jobs running in that protected environment can assume the role."
  type        = string
  default     = "repo:phrimm136/dante-planner:ref:refs/heads/main"
}

variable "create_github_oidc_provider" {
  description = "Create the account's GitHub Actions OIDC provider. true = this stack creates it (no provider exists yet). Set false if a token.actions.githubusercontent.com provider is later added elsewhere in the account — the trust policy then looks it up as a data source instead (only one per URL is allowed per account)."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Resource tags."
  type        = map(string)
  default = {
    Project = "danteplanner"
    Phase   = "oregon-iam-bootstrap"
  }
}

variable "rds_provisioner_role_name" {
  description = "Name of the existing role that applies terraform/rds. Empty = skip the peering grant. Set in terraform.tfvars (gitignored)."
  type        = string
  default     = ""
}

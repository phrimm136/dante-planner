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
  description = "Name of the provisioning role this stack creates. The CI workflow and the operator's laptop AWS profile assume this role."
  type        = string
  default     = "danteplanner-provisioner"
}

variable "trusted_admin_principal_arns" {
  description = "Principal ARNs allowed to sts:AssumeRole the provisioning roles from a laptop. A list so an identity-center permission-set role can be admitted alongside the principal it replaces, verified, and the old one then dropped without a window where neither works. Real values live in gitignored terraform.tfvars — never commit them. No default (public-repo invariant)."
  type        = list(string)

  validation {
    condition     = length(var.trusted_admin_principal_arns) > 0
    error_message = "at least one trusted admin principal is required, or the roles become assumable only by CI."
  }
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

variable "rds_master_password_secret_name" {
  description = "Secrets Manager entry the database stack reads at plan time. Must match that stack's master_password_secret_name; a mismatch is a plan that cannot resolve the password."
  type        = string
  default     = "danteplanner/rds/master-password"
}

variable "rds_provisioner_role_name" {
  description = "Name of the existing role that applies terraform/rds. Empty = skip the peering grant. Set in terraform.tfvars (gitignored)."
  type        = string
  default     = ""
}

variable "aws_account_id" {
  description = "The 12-digit AWS account this stack may apply into."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be the 12-digit AWS account number."
  }
}

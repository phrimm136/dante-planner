variable "aws_account_id" {
  description = "The 12-digit AWS account this stack may apply into — the organization's management account."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be the 12-digit AWS account number."
  }
}

variable "organization_id" {
  description = "Id of the existing organization to adopt (aws organizations describe-organization --query Organization.Id). Real value lives in gitignored terraform.tfvars — never commit it."
  type        = string

  validation {
    condition     = can(regex("^o-[a-z0-9]{10,32}$", var.organization_id))
    error_message = "organization_id must look like o-abc123xyz0."
  }
}

variable "region" {
  description = "Provider region for the Organizations API calls (the service itself is global)."
  type        = string
  default     = "us-west-2"
}

variable "security_accounts" {
  description = "Account name to root email for the Security unit. Each address must be globally unique across AWS and reachable, because it is the account's root login and the only recovery path. Real values live in gitignored terraform.tfvars — they are personal data and the repo is public."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for e in values(var.security_accounts) : can(regex("^[^@]+@[^@]+\\.[^@]+$", e))])
    error_message = "each security_accounts value must be an email address."
  }
}

variable "nonprod_accounts" {
  description = "Account name to root email for the non-production unit. Unlike the security accounts these sit under the workloads hierarchy, so they inherit its guardrails."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for e in values(var.nonprod_accounts) : can(regex("^[^@]+@[^@]+\\.[^@]+$", e))])
    error_message = "each nonprod_accounts value must be an email address."
  }
}

variable "prod_accounts" {
  description = "Account name to root email for the production unit. Sits under the workloads hierarchy like the non-production accounts, so it inherits the same guardrails."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for e in values(var.prod_accounts) : can(regex("^[^@]+@[^@]+\\.[^@]+$", e))])
    error_message = "each prod_accounts value must be an email address."
  }
}

variable "operator_group_name" {
  description = "Identity Center group that account assignments target."
  type        = string
  default     = "Operators"
}

variable "operator" {
  description = "The human operator to create in the Identity Center directory. Real values live in gitignored terraform.tfvars — the email is personal data and the repo is public."
  type = object({
    user_name    = string
    display_name = string
    given_name   = string
    family_name  = string
    email        = string
  })
}

variable "assignments" {
  description = "Account NAME (as listed in the organization) to permission-set name. Keyed by name so no account id is written down; an account absent from the organization fails the plan."
  type        = map(string)
  default     = {}

  validation {
    condition = alltrue([
      for ps in values(var.assignments) :
      contains(["AdministratorAccess", "PowerUserAccess", "ReadOnlyAccess"], ps)
    ])
    error_message = "each assignment must name one of AdministratorAccess, PowerUserAccess, ReadOnlyAccess."
  }
}

variable "name_prefix" {
  description = "Resource-name prefix, matching the other stacks. The trail destination is <name_prefix>-org-trail-<log-archive account>, so this must match what terraform/log-archive was applied with."
  type        = string
  default     = "danteplanner"
}

variable "trail_name" {
  description = "Name of the organization trail. The archive's bucket policy admits exactly the ARN built from this name, so the two must agree."
  type        = string
  default     = "danteplanner-organization"
}

variable "tags" {
  description = "Resource tags."
  type        = map(string)
  default = {
    Project = "danteplanner"
  }
}

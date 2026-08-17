variable "aws_account_id" {
  description = "The 12-digit account owning the registry; a plan resolving to any other account fails."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be the 12-digit AWS account number."
  }
}

variable "organization_id" {
  description = "Organization whose accounts may pull. Membership is the whole grant, so an account joining inherits access and an account leaving loses it without this being re-applied."
  type        = string

  validation {
    condition     = can(regex("^o-[a-z0-9]{10,32}$", var.organization_id))
    error_message = "organization_id must look like o-abc123xyz0."
  }
}

variable "pullable_repositories" {
  description = "Repositories other accounts in the organization may pull. Only images a fleet actually runs belong here; a repository absent from this list stays reachable from its own account alone."
  type        = list(string)
  default     = ["danteplanner-backend"]
}

variable "region" {
  description = "Region holding the registry."
  type        = string
  default     = "us-west-2"
}

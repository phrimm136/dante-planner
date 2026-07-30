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

variable "tags" {
  description = "Resource tags."
  type        = map(string)
  default = {
    Project = "danteplanner"
  }
}

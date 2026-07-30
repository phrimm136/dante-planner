variable "aws_account_id" {
  description = "The 12-digit AWS account this stack may apply into, and the account whose state the bucket will hold."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be the 12-digit AWS account number."
  }
}

variable "assume_role_arn" {
  description = "Role to assume in the target account. Empty means the ambient credentials already belong there. Real value lives in gitignored terraform.tfvars — it names an account."
  type        = string
  default     = ""
}

variable "region" {
  description = "Region hosting the state bucket."
  type        = string
  default     = "us-west-2"
}

variable "name_prefix" {
  description = "Resource-name prefix. The bucket is <name_prefix>-tfstate-<account_id>, so changing it orphans an existing bucket rather than renaming one."
  type        = string
  default     = "danteplanner"
}

variable "tags" {
  description = "Resource tags."
  type        = map(string)
  default = {
    Project = "danteplanner"
  }
}

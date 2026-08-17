variable "aws_account_id" {
  description = "The 12-digit account holding the archive; a plan resolving to any other account fails."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be the 12-digit AWS account number."
  }
}

variable "management_account_id" {
  description = "The account the organization trail is created in. It owns the trail ARN the bucket policy admits, and its own records land under a prefix keyed by this id rather than the organization id."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.management_account_id))
    error_message = "management_account_id must be the 12-digit AWS account number."
  }
}

variable "organization_id" {
  description = "Organization whose records land here. Trail objects are written under AWSLogs/<organization_id>/<account_id>/, so this scopes the write grant to one organization."
  type        = string

  validation {
    condition     = can(regex("^o-[a-z0-9]{10,32}$", var.organization_id))
    error_message = "organization_id must look like o-abc123xyz0."
  }
}

variable "assume_role_arn" {
  description = "Role to assume in this account. Empty means the ambient credentials already belong there."
  type        = string
  default     = ""
}

variable "trail_name" {
  description = "Name of the organization trail. The bucket policy admits exactly the trail ARN built from this name, the region and the management account, so it must match the trail resource."
  type        = string
  default     = "danteplanner-organization"
}

variable "region" {
  description = "Region holding the archive bucket and hosting the trail."
  type        = string
  default     = "us-west-2"
}

variable "name_prefix" {
  description = "Resource-name prefix."
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

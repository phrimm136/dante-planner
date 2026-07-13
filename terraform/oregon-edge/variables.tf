variable "region" {
  description = "Region for the durable edge resources. Must match terraform/oregon."
  type        = string
  default     = "us-west-2"
}

variable "name_prefix" {
  description = "Name prefix for tagging, matching the fleet stack."
  type        = string
  default     = "danteplanner"
}

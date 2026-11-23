variable "name" {
  description = "Name prefix, e.g. cloudforge-staging."
  type        = string
}

variable "cidr_block" {
  description = "VPC address range. /16 leaves room for /24 subnets."
  type        = string
  default     = "10.20.0.0/16"
}

variable "enable_flow_logs" {
  description = "Record VPC flow logs in CloudWatch (costs per GB ingested)."
  type        = bool
  default     = false
}

variable "log_retention_days" {
  type    = number
  default = 30
}

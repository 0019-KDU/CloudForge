variable "name" {
  description = "Name prefix, e.g. cloudforge-staging."
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Private subnets in at least two availability zones."
  type        = list(string)
}

variable "client_security_group_id" {
  description = "Security group of the only client allowed to connect (the backend tasks)."
  type        = string
}

variable "engine_version" {
  description = "PostgreSQL major version (minor versions upgrade automatically). Matches the version the integration tests use."
  type        = string
  default     = "17"
}

variable "instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "database_name" {
  type    = string
  default = "cloudforge"
}

variable "username" {
  type    = string
  default = "cloudforge"
}

variable "password_version" {
  description = "Increase to generate and set a new master password."
  type        = number
  default     = 1
}

variable "allocated_storage_gb" {
  type    = number
  default = 20
}

variable "max_allocated_storage_gb" {
  description = "Storage autoscaling ceiling."
  type        = number
  default     = 100
}

variable "multi_az" {
  description = "Standby replica in a second AZ (doubles the instance cost)."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  type    = number
  default = 7
}

variable "deletion_protection" {
  description = "Protect against deletion and keep a final snapshot. Turn on for production."
  type        = bool
  default     = true
}

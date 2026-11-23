variable "environment" {
  description = "Environment name; also the GitHub environment allowed to deploy it."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.environment))
    error_message = "Lowercase letters, digits and hyphens, starting with a letter."
  }
}

variable "github_repository" {
  description = "owner/repository allowed to deploy, e.g. 0019-KDU/CloudForge."
  type        = string
}

variable "image_tag" {
  description = "Image tag (commit SHA) both images must already have in ECR for the first apply."
  type        = string
}

variable "allow_image_push" {
  description = "Whether this environment's deploy role may push images (true only where images are first deployed)."
  type        = bool
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "enable_flow_logs" {
  type    = bool
  default = false
}

variable "database_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "database_multi_az" {
  type    = bool
  default = false
}

variable "database_backup_retention_days" {
  type    = number
  default = 7
}

variable "backend_desired_count" {
  type    = number
  default = 1
}

variable "frontend_desired_count" {
  type    = number
  default = 1
}

variable "use_fargate_spot" {
  type    = bool
  default = false
}

variable "domain_name" {
  type    = string
  default = null
}

variable "acm_certificate_arn" {
  type    = string
  default = null
}

variable "bootstrap_admin_email" {
  type    = string
  default = null
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "deletion_protection" {
  description = "Protect the database and load balancer from deletion; keep a final DB snapshot."
  type        = bool
  default     = true
}

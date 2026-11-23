variable "name" {
  description = "Name prefix, e.g. cloudforge-staging."
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  description = "Subnets for the tasks (public IP for outbound calls; inbound only from the load balancer)."
  type        = list(string)
}

variable "private_subnet_ids" {
  description = "Subnets for the internal load balancer."
  type        = list(string)
}

variable "backend_security_group_id" {
  description = "Created outside this module because the database admits it too."
  type        = string
}

# ---- images ------------------------------------------------------------------------------------

variable "backend_repository_url" {
  type = string
}

variable "frontend_repository_url" {
  type = string
}

variable "image_tag" {
  description = "Image tag (commit SHA) for the first task definitions. Later deployments are done by the CD workflow."
  type        = string
}

# ---- database ----------------------------------------------------------------------------------

variable "database_jdbc_url" {
  type = string
}

variable "database_username" {
  type = string
}

variable "database_password_secret_arn" {
  type = string
}

# ---- sizing ------------------------------------------------------------------------------------

variable "backend_cpu" {
  description = "Fargate CPU units (1024 = 1 vCPU)."
  type        = number
  default     = 512
}

variable "backend_memory" {
  description = "MiB. The JVM uses 75% of it for the heap."
  type        = number
  default     = 1024
}

variable "backend_desired_count" {
  type    = number
  default = 1
}

variable "frontend_cpu" {
  type    = number
  default = 256
}

variable "frontend_memory" {
  type    = number
  default = 512
}

variable "frontend_desired_count" {
  type    = number
  default = 1
}

variable "use_fargate_spot" {
  description = "Run on Fargate Spot (about 70% cheaper; tasks can be interrupted). Fine for staging."
  type        = bool
  default     = false
}

# ---- edge --------------------------------------------------------------------------------------

variable "domain_name" {
  description = "Custom domain (e.g. app.example.com). Null: use the CloudFront address."
  type        = string
  default     = null
}

variable "acm_certificate_arn" {
  description = "ACM certificate for domain_name, issued in us-east-1. Required with domain_name."
  type        = string
  default     = null

  validation {
    condition     = (var.acm_certificate_arn == null) == (var.domain_name == null)
    error_message = "Set domain_name and acm_certificate_arn together, or neither."
  }
}

variable "waf_auth_requests_per_5_minutes" {
  description = "Per-IP limit on /api/auth/* (login, refresh, register, password reset)."
  type        = number
  default     = 100
}

variable "waf_requests_per_5_minutes" {
  description = "Per-IP limit on all requests."
  type        = number
  default     = 3000
}

variable "cloudfront_price_class" {
  description = "PriceClass_200 includes India and the rest of Asia; PriceClass_All adds South America and Oceania."
  type        = string
  default     = "PriceClass_200"
}

# ---- operations --------------------------------------------------------------------------------

variable "bootstrap_admin_email" {
  description = "Creates the first platform administrator. Unset after the first sign-in."
  type        = string
  default     = null
}

variable "jwt_secret_version" {
  description = "Increase to generate a new JWT signing key (signs everyone out)."
  type        = number
  default     = 1
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "container_insights" {
  description = "ECS Container Insights metrics (billed per metric)."
  type        = bool
  default     = false
}

variable "deletion_protection" {
  description = "Protect the load balancer from deletion."
  type        = bool
  default     = true
}

variable "secret_recovery_window_days" {
  description = "Days a deleted secret can be restored. 0 deletes immediately (lets a test environment be recreated)."
  type        = number
  default     = 30
}

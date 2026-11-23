variable "region" {
  type    = string
  default = "ap-south-1"
}

variable "github_repository" {
  type    = string
  default = "0019-KDU/CloudForge"
}

variable "image_tag" {
  description = "Commit SHA whose images are already in ECR (first apply only; the CD workflow deploys after that)."
  type        = string
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

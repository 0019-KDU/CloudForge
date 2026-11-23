variable "name" {
  description = "Role name."
  type        = string
}

variable "oidc_provider_arn" {
  description = "The account's GitHub OIDC provider (created by infra/global)."
  type        = string
}

variable "github_repository" {
  description = "owner/repository, e.g. 0019-KDU/CloudForge."
  type        = string
}

variable "github_environment" {
  description = "GitHub environment whose jobs may assume this role, e.g. staging."
  type        = string
}

variable "ecr_repository_arns" {
  type = list(string)
}

variable "allow_image_push" {
  description = "Allow pushing images (only for the environment where images are first deployed)."
  type        = bool
  default     = false
}

variable "ecs_service_arns" {
  type = list(string)
}

variable "passable_role_arns" {
  type = list(string)
}

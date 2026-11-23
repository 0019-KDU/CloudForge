variable "region" {
  description = "AWS region for the shared resources. Use the same region as the environments."
  type        = string
  default     = "ap-south-1"
}

variable "repositories" {
  description = "ECR repositories, one per deployable image."
  type        = list(string)
  default     = ["cloudforge-backend", "cloudforge-frontend"]
}

variable "images_to_keep" {
  description = "Images kept per repository. Older ones are deleted; keep enough to roll back."
  type        = number
  default     = 30
}

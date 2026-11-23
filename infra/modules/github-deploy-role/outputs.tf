output "role_arn" {
  description = "Store as the AWS_DEPLOY_ROLE_ARN variable of the matching GitHub environment."
  value       = aws_iam_role.this.arn
}

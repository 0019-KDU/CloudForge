output "url" {
  description = "Public HTTPS address of the application."
  value       = "https://${coalesce(var.domain_name, aws_cloudfront_distribution.this.domain_name)}"
}

output "cloudfront_domain_name" {
  description = "Point the custom domain's DNS (CNAME or alias) here."
  value       = aws_cloudfront_distribution.this.domain_name
}

output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  value = aws_ecs_cluster.this.arn
}

output "service_names" {
  value = { backend = aws_ecs_service.backend.name, frontend = aws_ecs_service.frontend.name }
}

output "service_arns" {
  value = [aws_ecs_service.backend.id, aws_ecs_service.frontend.id]
}

output "task_definition_families" {
  value = { backend = aws_ecs_task_definition.backend.family, frontend = aws_ecs_task_definition.frontend.family }
}

output "task_role_arns" {
  description = "Roles a deployment may pass to ECS (iam:PassRole)."
  value       = [aws_iam_role.execution.arn, aws_iam_role.backend_task.arn]
}

output "backend_task_role_name" {
  description = "Attach cloud-provisioning permissions here when a provider is switched to real mode."
  value       = aws_iam_role.backend_task.name
}

output "bootstrap_admin_secret_name" {
  value = one(aws_secretsmanager_secret.bootstrap_admin[*].name)
}

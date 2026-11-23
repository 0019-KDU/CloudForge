output "url" {
  value = module.environment.url
}

output "deploy_role_arn" {
  description = "Store as variable AWS_DEPLOY_ROLE_ARN in the GitHub environment \"production\"."
  value       = module.environment.deploy_role_arn
}

output "cluster_name" {
  value = module.environment.cluster_name
}

output "service_names" {
  value = module.environment.service_names
}

output "task_definition_families" {
  value = module.environment.task_definition_families
}

output "bootstrap_admin_secret_name" {
  description = "Read the first admin's password here (Secrets Manager console)."
  value       = module.environment.bootstrap_admin_secret_name
}

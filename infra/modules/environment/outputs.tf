output "url" {
  value = module.app.url
}

output "cloudfront_domain_name" {
  value = module.app.cloudfront_domain_name
}

output "deploy_role_arn" {
  value = module.deploy_role.role_arn
}

output "cluster_name" {
  value = module.app.cluster_name
}

output "service_names" {
  value = module.app.service_names
}

output "task_definition_families" {
  value = module.app.task_definition_families
}

output "backend_task_role_name" {
  value = module.app.backend_task_role_name
}

output "bootstrap_admin_secret_name" {
  value = module.app.bootstrap_admin_secret_name
}

output "database_identifier" {
  value = module.database.identifier
}

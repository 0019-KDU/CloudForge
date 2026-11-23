output "repository_urls" {
  description = "Image repository URLs, by repository name."
  value       = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

output "github_oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.github.arn
}

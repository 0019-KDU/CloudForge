output "state_bucket" {
  description = "Put this in the backend.hcl of the global and environment stacks."
  value       = aws_s3_bucket.state.bucket
}

output "state_kms_key_arn" {
  description = "Put this in backend.hcl as kms_key_id."
  value       = aws_kms_key.state.arn
}

output "region" {
  value = var.region
}

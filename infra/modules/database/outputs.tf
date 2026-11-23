output "jdbc_url" {
  description = "JDBC URL for the backend; TLS required."
  value       = "jdbc:postgresql://${aws_db_instance.this.address}:${aws_db_instance.this.port}/${aws_db_instance.this.db_name}?sslmode=require"
}

output "username" {
  value = aws_db_instance.this.username
}

output "password_secret_arn" {
  value = aws_secretsmanager_secret.password.arn
}

output "security_group_id" {
  value = aws_security_group.this.id
}

output "identifier" {
  value = aws_db_instance.this.identifier
}

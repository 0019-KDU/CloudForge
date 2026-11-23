# Application secrets. Values are ephemeral and written through write-only attributes, so they
# never enter Terraform state; ECS injects them into the backend container at start-up.

# JWT signing key. The backend expects a Base64 HMAC key of at least 256 bits: 64 characters from
# the Base64 alphabet (letters and digits) decode to 48 bytes = 384 bits.
ephemeral "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${var.name}/jwt-secret"
  description             = "HMAC key that signs CloudForge access tokens (${var.name})."
  recovery_window_in_days = var.secret_recovery_window_days
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id                = aws_secretsmanager_secret.jwt.id
  secret_string_wo         = ephemeral.random_password.jwt.result
  secret_string_wo_version = var.jwt_secret_version
}

# First administrator. Only created when bootstrap_admin_email is set; read the password once in
# the Secrets Manager console, sign in, then unset bootstrap_admin_email (the backend ignores the
# bootstrap settings once an active admin exists).
ephemeral "random_password" "bootstrap_admin" {
  length      = 24
  min_upper   = 2
  min_lower   = 2
  min_numeric = 2
  special     = false
}

resource "aws_secretsmanager_secret" "bootstrap_admin" {
  count = local.bootstrap_admin ? 1 : 0

  name                    = "${var.name}/bootstrap-admin-password"
  description             = "Initial password of ${var.bootstrap_admin_email} (${var.name})."
  recovery_window_in_days = var.secret_recovery_window_days
}

resource "aws_secretsmanager_secret_version" "bootstrap_admin" {
  count = local.bootstrap_admin ? 1 : 0

  secret_id                = aws_secretsmanager_secret.bootstrap_admin[0].id
  secret_string_wo         = ephemeral.random_password.bootstrap_admin.result
  secret_string_wo_version = 1
}

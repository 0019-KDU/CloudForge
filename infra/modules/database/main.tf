# PostgreSQL on RDS, in private subnets, reachable only from the backend's security group.
#
# The master password never enters Terraform state: it is generated as an ephemeral value and
# written through write-only attributes to the database and to a Secrets Manager secret, which the
# backend task reads at start-up. To rotate it, increase password_version and apply, then
# redeploy the backend.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

ephemeral "random_password" "master" {
  length = 40
  # RDS forbids / @ " and spaces in master passwords; letters and digits avoid every escaping issue.
  special = false
}

resource "aws_secretsmanager_secret" "password" {
  name        = "${var.name}/database-password"
  description = "Master password of the ${var.name} PostgreSQL database."
  # Allows a deleted environment to be recreated with the same name after the recovery window.
  recovery_window_in_days = var.deletion_protection ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "password" {
  secret_id                = aws_secretsmanager_secret.password.id
  secret_string_wo         = ephemeral.random_password.master.result
  secret_string_wo_version = var.password_version
}

resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "this" {
  name        = "${var.name}-database"
  description = "PostgreSQL, reachable from the backend tasks only"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.name}-database" }
}

resource "aws_vpc_security_group_ingress_rule" "from_clients" {
  security_group_id            = aws_security_group.this.id
  referenced_security_group_id = var.client_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from the backend"
}

resource "aws_db_parameter_group" "this" {
  name   = var.name
  family = "postgres${split(".", var.engine_version)[0]}"

  # Refuse unencrypted connections.
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  # Log statements slower than 1 s, to find slow queries without logging everything.
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "this" {
  identifier     = var.name
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  db_name             = var.database_name
  username            = var.username
  password_wo         = ephemeral.random_password.master.result
  password_wo_version = var.password_version

  allocated_storage     = var.allocated_storage_gb
  max_allocated_storage = var.max_allocated_storage_gb
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.this.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = var.multi_az

  backup_retention_period    = var.backup_retention_days
  backup_window              = "20:00-21:00" # UTC: 01:30-02:30 in Sri Lanka
  maintenance_window         = "sun:21:30-sun:22:30"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = !var.deletion_protection
  final_snapshot_identifier = var.deletion_protection ? "${var.name}-final" : null

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
}

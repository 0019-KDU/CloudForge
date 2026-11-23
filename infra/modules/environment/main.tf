# One complete CloudForge environment: network, database, application and the GitHub deploy role.
# envs/staging and envs/production call this module with different settings, so the environments
# can't drift apart structurally.

terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws.us_east_1]
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

locals {
  name = "cloudforge-${var.environment}"
}

# Shared resources from infra/global.
data "aws_ecr_repository" "backend" {
  name = "cloudforge-backend"
}

data "aws_ecr_repository" "frontend" {
  name = "cloudforge-frontend"
}

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

module "network" {
  source = "../network"

  name             = local.name
  cidr_block       = var.vpc_cidr
  enable_flow_logs = var.enable_flow_logs
}

# The backend's security group lives here because both the database (inbound rule) and the
# application (the tasks) refer to it.
resource "aws_security_group" "backend" {
  name        = "${local.name}-backend"
  description = "Backend tasks"
  vpc_id      = module.network.vpc_id

  tags = { Name = "${local.name}-backend" }
}

resource "aws_vpc_security_group_egress_rule" "backend_https" {
  security_group_id = aws_security_group.backend.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "ECR, Secrets Manager, CloudWatch and the cloud provider APIs"
}

resource "aws_vpc_security_group_egress_rule" "backend_database" {
  security_group_id            = aws_security_group.backend.id
  referenced_security_group_id = module.database.security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL"
}

module "database" {
  source = "../database"

  name                     = local.name
  vpc_id                   = module.network.vpc_id
  subnet_ids               = module.network.private_subnet_ids
  client_security_group_id = aws_security_group.backend.id
  instance_class           = var.database_instance_class
  multi_az                 = var.database_multi_az
  backup_retention_days    = var.database_backup_retention_days
  deletion_protection      = var.deletion_protection
}

module "app" {
  source = "../app"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  name                      = local.name
  vpc_id                    = module.network.vpc_id
  public_subnet_ids         = module.network.public_subnet_ids
  private_subnet_ids        = module.network.private_subnet_ids
  backend_security_group_id = aws_security_group.backend.id

  backend_repository_url  = data.aws_ecr_repository.backend.repository_url
  frontend_repository_url = data.aws_ecr_repository.frontend.repository_url
  image_tag               = var.image_tag

  database_jdbc_url            = module.database.jdbc_url
  database_username            = module.database.username
  database_password_secret_arn = module.database.password_secret_arn

  backend_desired_count  = var.backend_desired_count
  frontend_desired_count = var.frontend_desired_count
  use_fargate_spot       = var.use_fargate_spot

  domain_name         = var.domain_name
  acm_certificate_arn = var.acm_certificate_arn

  bootstrap_admin_email       = var.bootstrap_admin_email
  log_retention_days          = var.log_retention_days
  deletion_protection         = var.deletion_protection
  secret_recovery_window_days = var.deletion_protection ? 30 : 0
}

module "deploy_role" {
  source = "../github-deploy-role"

  name                = "${local.name}-github-deploy"
  oidc_provider_arn   = data.aws_iam_openid_connect_provider.github.arn
  github_repository   = var.github_repository
  github_environment  = var.environment
  ecr_repository_arns = [data.aws_ecr_repository.backend.arn, data.aws_ecr_repository.frontend.arn]
  allow_image_push    = var.allow_image_push
  ecs_service_arns    = module.app.service_arns
  passable_role_arns  = module.app.task_role_arns
}

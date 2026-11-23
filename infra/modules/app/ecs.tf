# ECS on Fargate: no servers or cluster control plane to operate.
#
# Deployments: Terraform creates the services and their first task definitions. After that the CD
# workflow owns which image runs: it registers a new task-definition revision with the new image
# and updates the service, so Terraform ignores the service's task_definition. A configuration
# change made here (environment variables, CPU, memory) becomes a new revision that the next
# deployment picks up.
#
# Every deployment is a rolling update with the circuit breaker on: if the new tasks don't become
# healthy, ECS rolls back to the previous revision by itself.

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  container_port  = 8080
  bootstrap_admin = var.bootstrap_admin_email != null

  backend_secret_arns = concat(
    [var.database_password_secret_arn, aws_secretsmanager_secret.jwt.arn],
    aws_secretsmanager_secret.bootstrap_admin[*].arn,
  )

  # Read-only root filesystem: the only writable path is an empty /tmp volume.
  tmp_volume = "tmp"
  hardened_container = {
    readonlyRootFilesystem = true
    mountPoints            = [{ sourceVolume = local.tmp_volume, containerPath = "/tmp", readOnly = false }]
    linuxParameters        = { capabilities = { drop = ["ALL"] }, initProcessEnabled = true }
  }
}

resource "aws_ecs_cluster" "this" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = var.container_insights ? "enhanced" : "disabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/cloudforge/${var.name}/backend"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "frontend" {
  name              = "/cloudforge/${var.name}/frontend"
  retention_in_days = var.log_retention_days
}

# ---- backend ---------------------------------------------------------------------------------

resource "aws_vpc_security_group_ingress_rule" "backend_from_alb" {
  security_group_id            = var.backend_security_group_id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = local.container_port
  to_port                      = local.container_port
  description                  = "Load balancer"
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.name}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.backend_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  volume {
    name = local.tmp_volume
  }

  container_definitions = jsonencode([merge(local.hardened_container, {
    name      = "backend"
    image     = "${var.backend_repository_url}:${var.image_tag}"
    essential = true

    portMappings = [{ containerPort = local.container_port, protocol = "tcp" }]

    environment = concat(
      [
        { name = "CLOUDFORGE_DB_URL", value = var.database_jdbc_url },
        { name = "CLOUDFORGE_DB_USERNAME", value = var.database_username },
        # Behind CloudFront and the load balancer: trust X-Forwarded-* from them.
        { name = "SERVER_FORWARD_HEADERS_STRATEGY", value = "native" },
        # JSON logs: one CloudWatch event per line, MDC (correlation ID) included.
        { name = "LOGGING_STRUCTURED_FORMAT_CONSOLE", value = "ecs" },
      ],
      local.bootstrap_admin ? [
        { name = "CLOUDFORGE_BOOTSTRAP_ADMIN_EMAIL", value = var.bootstrap_admin_email },
        { name = "CLOUDFORGE_BOOTSTRAP_ADMIN_DISPLAY_NAME", value = "Platform Admin" },
      ] : [],
    )

    secrets = concat(
      [
        { name = "CLOUDFORGE_DB_PASSWORD", valueFrom = var.database_password_secret_arn },
        { name = "CLOUDFORGE_JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt.arn },
      ],
      [for arn in aws_secretsmanager_secret.bootstrap_admin[*].arn :
      { name = "CLOUDFORGE_BOOTSTRAP_ADMIN_PASSWORD", valueFrom = arn }],
    )

    # Same probe as the image's HEALTHCHECK. Start period covers Flyway migrations on first boot.
    healthCheck = {
      command     = ["CMD-SHELL", "curl -fsS http://localhost:${local.container_port}/actuator/health/liveness || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 120
    }

    # The backend drains requests for up to 30 s on SIGTERM (server.shutdown=graceful).
    stopTimeout = 40

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.backend.name
        awslogs-region        = data.aws_region.current.region
        awslogs-stream-prefix = "backend"
      }
    }
  })])
}

resource "aws_ecs_service" "backend" {
  name            = "backend"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count

  capacity_provider_strategy {
    capacity_provider = var.use_fargate_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 1
  }

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [var.backend_security_group_id]
    assign_public_ip = true # outbound only; the security group admits the load balancer alone
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = local.container_port
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 180

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  propagate_tags = "SERVICE"

  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener_rule.api, aws_ecs_cluster_capacity_providers.this]
}

# ---- frontend --------------------------------------------------------------------------------

resource "aws_security_group" "frontend" {
  name        = "${var.name}-frontend"
  description = "Frontend tasks, HTTP from the load balancer only"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.name}-frontend" }
}

resource "aws_vpc_security_group_ingress_rule" "frontend_from_alb" {
  security_group_id            = aws_security_group.frontend.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = local.container_port
  to_port                      = local.container_port
  description                  = "Load balancer"
}

# HTTPS out, for pulling the image from ECR.
resource "aws_vpc_security_group_egress_rule" "frontend_https" {
  security_group_id = aws_security_group.frontend.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "ECR and AWS APIs"
}

resource "aws_ecs_task_definition" "frontend" {
  family                   = "${var.name}-frontend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.frontend_cpu
  memory                   = var.frontend_memory
  execution_role_arn       = aws_iam_role.execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  volume {
    name = local.tmp_volume
  }

  container_definitions = jsonencode([merge(local.hardened_container, {
    name      = "frontend"
    image     = "${var.frontend_repository_url}:${var.image_tag}"
    essential = true

    portMappings = [{ containerPort = local.container_port, protocol = "tcp" }]

    # /api never reaches NGINX here: the load balancer routes it to the backend directly.
    healthCheck = {
      command     = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:${local.container_port}/healthz || exit 1"]
      interval    = 30
      timeout     = 3
      retries     = 3
      startPeriod = 10
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.frontend.name
        awslogs-region        = data.aws_region.current.region
        awslogs-stream-prefix = "frontend"
      }
    }
  })])
}

resource "aws_ecs_service" "frontend" {
  name            = "frontend"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.frontend.arn
  desired_count   = var.frontend_desired_count

  capacity_provider_strategy {
    capacity_provider = var.use_fargate_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 1
  }

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [aws_security_group.frontend.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.frontend.arn
    container_name   = "frontend"
    container_port   = local.container_port
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 30

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  propagate_tags = "SERVICE"

  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener.http, aws_ecs_cluster_capacity_providers.this]
}

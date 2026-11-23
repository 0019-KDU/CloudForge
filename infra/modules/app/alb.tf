# Internal Application Load Balancer in the private subnets. It has no public address: the only
# way in is CloudFront, through a CloudFront VPC origin (see cloudfront.tf).
# Routing mirrors the frontend's same-origin design: /api/* → backend, everything else → frontend.

# CloudFront's origin-facing addresses, which include the VPC-origin traffic.
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${var.name}-alb"
  description = "Internal load balancer, HTTP from CloudFront only"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.name}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  description       = "CloudFront (VPC origin)"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_backend" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = var.backend_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = local.container_port
  to_port                      = local.container_port
  description                  = "Backend tasks"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_frontend" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.frontend.id
  ip_protocol                  = "tcp"
  from_port                    = local.container_port
  to_port                      = local.container_port
  description                  = "Frontend tasks"
}

resource "aws_lb" "this" {
  name               = var.name
  load_balancer_type = "application"
  internal           = true
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.private_subnet_ids

  drop_invalid_header_fields = true
  enable_deletion_protection = var.deletion_protection
  idle_timeout               = 60
}

resource "aws_lb_target_group" "backend" {
  name                 = "${var.name}-backend"
  port                 = local.container_port
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = var.vpc_id
  deregistration_delay = 30

  # Readiness, not liveness: a task that can't reach the database gets no traffic.
  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_target_group" "frontend" {
  name                 = "${var.name}-frontend"
  port                 = local.container_port
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = var.vpc_id
  deregistration_delay = 15

  health_check {
    path                = "/healthz"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# Plain HTTP on this hop only: CloudFront terminates TLS for the browser, and the load balancer is
# reachable only through the VPC origin inside AWS's network. HTTPS here needs a certificate for a
# domain (see the acm_certificate_arn variable); recorded in .trivyignore.yaml.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}

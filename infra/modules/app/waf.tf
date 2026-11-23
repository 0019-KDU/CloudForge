# AWS WAF in front of CloudFront (web ACLs for CloudFront must live in us-east-1).
#
#   rate-limit-auth   - per client IP, on /api/auth/*: stops password guessing and token abuse at
#                       the edge. The backend's own login throttle only sees CloudFront addresses,
#                       so this is the per-client limit that actually works.
#   rate-limit-all    - per client IP, everything: blunts floods and scrapers
#   AWS managed rules - IP reputation, known bad inputs (e.g. Log4Shell probes), common web attacks

resource "aws_wafv2_web_acl" "this" {
  provider = aws.us_east_1

  name        = var.name
  description = "CloudForge ${var.name} edge protection"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "rate-limit-auth"
    priority = 0

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit                 = var.waf_auth_requests_per_5_minutes
        evaluation_window_sec = 300
        aggregate_key_type    = "IP"

        scope_down_statement {
          byte_match_statement {
            search_string         = "/api/auth/"
            positional_constraint = "STARTS_WITH"

            field_to_match {
              uri_path {}
            }

            text_transformation {
              priority = 0
              type     = "LOWERCASE"
            }
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-rate-limit-auth"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "rate-limit-all"
    priority = 1

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit                 = var.waf_requests_per_5_minutes
        evaluation_window_sec = 300
        aggregate_key_type    = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-rate-limit-all"
      sampled_requests_enabled   = true
    }
  }

  dynamic "rule" {
    for_each = {
      AWSManagedRulesAmazonIpReputationList = 10
      AWSManagedRulesKnownBadInputsRuleSet  = 11
      AWSManagedRulesCommonRuleSet          = 12
    }

    content {
      name     = rule.key
      priority = rule.value

      override_action {
        none {}
      }

      statement {
        managed_rule_group_statement {
          vendor_name = "AWS"
          name        = rule.key
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "${var.name}-${rule.key}"
        sampled_requests_enabled   = true
      }
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = var.name
    sampled_requests_enabled   = true
  }
}

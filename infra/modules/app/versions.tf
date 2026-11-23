terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
      # us_east_1: the WAF web ACL for CloudFront must be created there.
      configuration_aliases = [aws.us_east_1]
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

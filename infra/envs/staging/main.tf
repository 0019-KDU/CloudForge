# Staging: every merge to main deploys here automatically. Cheaper settings than production:
# Fargate Spot, no deletion protection (it can be torn down and recreated), short log retention.

terraform {
  required_version = ">= 1.11" # write-only attributes

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.73"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
  }

  # Bucket and region come from backend.hcl (see backend.hcl.example).
  backend "s3" {
    key          = "envs/staging/terraform.tfstate"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "cloudforge"
      Environment = "staging"
      ManagedBy   = "terraform"
    }
  }
}

# CloudFront's WAF web ACL can only be created in us-east-1.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = "cloudforge"
      Environment = "staging"
      ManagedBy   = "terraform"
    }
  }
}

module "environment" {
  source = "../../modules/environment"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  environment       = "staging"
  github_repository = var.github_repository
  image_tag         = var.image_tag
  # Images are pushed once, by the staging deployment; production promotes the same images.
  allow_image_push = true

  vpc_cidr               = "10.20.0.0/16"
  use_fargate_spot       = true
  backend_desired_count  = 1
  frontend_desired_count = 1

  database_instance_class        = "db.t4g.micro"
  database_multi_az              = false
  database_backup_retention_days = 1

  domain_name         = var.domain_name
  acm_certificate_arn = var.acm_certificate_arn

  bootstrap_admin_email = var.bootstrap_admin_email
  log_retention_days    = 14
  deletion_protection   = false
}

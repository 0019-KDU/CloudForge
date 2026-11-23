# Production: deployed only after approval in the GitHub environment "production", using the exact
# images already running in staging. Safer settings than staging: on-demand Fargate, deletion
# protection with a final database snapshot, longer backups and logs.

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
    key          = "envs/production/terraform.tfstate"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "cloudforge"
      Environment = "production"
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
      Environment = "production"
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

  environment       = "production"
  github_repository = var.github_repository
  image_tag         = var.image_tag
  # Production never builds or pushes images: it promotes what staging already tested.
  allow_image_push = false

  vpc_cidr               = "10.30.0.0/16"
  use_fargate_spot       = false
  backend_desired_count  = 2 # one task per AZ: survives a task or AZ failure, and deploys with no gap
  frontend_desired_count = 2

  database_instance_class        = "db.t4g.small"
  database_multi_az              = false # set true for a standby in a second AZ (doubles the DB cost)
  database_backup_retention_days = 14

  domain_name         = var.domain_name
  acm_certificate_arn = var.acm_certificate_arn

  bootstrap_admin_email = var.bootstrap_admin_email
  log_retention_days    = 90
  deletion_protection   = true
}

#!/usr/bin/env bash
# Deploy one commit's images to a CloudForge ECS environment.
#
#   .github/scripts/ecs-deploy.sh <environment> <commit-sha> [<image-artifact-dir>]
#
# With an artifact directory (the staging deployment from CI), the images CI built and tested are
# loaded from their `docker save` tars and pushed to ECR first: build once, never rebuild. Without
# one (promotion to production, rollback), the images must already be in ECR.
#
# For each service, backend first (so a new frontend never calls an older API):
#   1. take the service's latest task definition (Terraform owns everything except the image)
#   2. register a new revision with only the image changed to <commit-sha>
#   3. update the service and wait until ECS reports the rollout COMPLETED on that revision
# ECS's deployment circuit breaker rolls back by itself if the new tasks never become healthy;
# this script then fails, because the service is no longer running the requested revision.
#
# Needs AWS credentials (the GitHub OIDC deploy role) and AWS_REGION.
set -euo pipefail

environment="${1:?usage: $0 <environment> <commit-sha> [<image-artifact-dir>]}"
tag="${2:?usage: $0 <environment> <commit-sha> [<image-artifact-dir>]}"
artifact_dir="${3:-}"

# Inputs arrive from workflow_dispatch: validate before they reach any command.
[[ "$environment" =~ ^[a-z][a-z0-9-]{1,20}$ ]] || { echo "::error::Invalid environment: $environment"; exit 1; }
[[ "$tag" =~ ^[0-9a-f]{40}$ ]] || { echo "::error::Image tag must be a full 40-character commit SHA, got: $tag"; exit 1; }
: "${AWS_REGION:?AWS_REGION must be set}"

components=(backend frontend)
cluster="cloudforge-$environment"
account=$(aws sts get-caller-identity --query Account --output text)
registry="$account.dkr.ecr.$AWS_REGION.amazonaws.com"
rollout_timeout_seconds=900
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"

image_in_ecr() {
  aws ecr describe-images --repository-name "cloudforge-$1" --image-ids imageTag="$tag" >/dev/null 2>&1
}

# ---- 1. images ---------------------------------------------------------------------------------
if [[ -n "$artifact_dir" ]]; then
  aws ecr get-login-password | docker login --username AWS --password-stdin "$registry"
  for component in "${components[@]}"; do
    if image_in_ecr "$component"; then
      echo "cloudforge-$component:$tag is already in ECR (re-run); tags are immutable, not pushing again."
      continue
    fi
    tar="$artifact_dir/$component-image-$tag/image.tar"
    [[ -f "$tar" ]] || { echo "::error::Missing image artifact $tar"; exit 1; }
    docker load -i "$tar"
    docker tag "cloudforge-$component:ci-$tag" "$registry/cloudforge-$component:$tag"
    docker push "$registry/cloudforge-$component:$tag"
  done
fi

# Promotion and rollback deploy only images that went through CI and staging.
for component in "${components[@]}"; do
  image_in_ecr "$component" || {
    echo "::error::cloudforge-$component:$tag is not in ECR. Only commits built by CI on main can be deployed."
    exit 1
  }
done

# ---- 2. services -------------------------------------------------------------------------------
deploy_component() {
  local component="$1" family="cloudforge-$environment-$1" image current new_arn
  image="$registry/cloudforge-$component:$tag"

  current=$(aws ecs describe-task-definition --task-definition "$family" --query taskDefinition --output json)
  # Change the image only; drop the read-only fields describe returns but register rejects.
  new_arn=$(jq --arg name "$component" --arg image "$image" '
      .containerDefinitions |= map(if .name == $name then .image = $image else . end)
      | del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities,
            .registeredAt, .registeredBy, .deregisteredAt)' <<<"$current" \
    | aws ecs register-task-definition --cli-input-json file:///dev/stdin \
        --query taskDefinition.taskDefinitionArn --output text)
  echo "Registered $new_arn"

  aws ecs update-service --cluster "$cluster" --service "$component" --task-definition "$new_arn" \
    --query 'service.deployments[?status==`PRIMARY`].id' --output text >/dev/null

  # Wait for the rollout to finish on the new revision, or detect a circuit-breaker rollback.
  local deadline=$((SECONDS + rollout_timeout_seconds)) primary_td state
  while ((SECONDS < deadline)); do
    read -r primary_td state < <(aws ecs describe-services --cluster "$cluster" --services "$component" \
      --query 'services[0].deployments[?status==`PRIMARY`] | [0].[taskDefinition, rolloutState]' --output text)
    if [[ "$primary_td" != "$new_arn" ]]; then
      echo "::error title=$component rolled back::The new tasks did not become healthy; ECS rolled back to $primary_td. Check the service events and the /cloudforge/$cluster/$component logs."
      return 1
    fi
    case "$state" in
      COMPLETED)
        echo "✅ $component is running $tag"
        echo "| $component | \`$tag\` | ✅ healthy |" >>"$summary"
        return 0 ;;
      FAILED)
        echo "::error title=$component deployment failed::Rollout state FAILED. Check the service events."
        return 1 ;;
    esac
    echo "  $component: rollout $state..."
    sleep 15
  done
  echo "::error title=$component deployment timed out::Not COMPLETED after ${rollout_timeout_seconds}s."
  return 1
}

{
  echo "### Deployment to \`$environment\`"
  echo
  echo "| Service | Image tag | Result |"
  echo "|---|---|---|"
} >>"$summary"

for component in "${components[@]}"; do
  deploy_component "$component"
done

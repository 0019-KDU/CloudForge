#!/usr/bin/env bash
# Smoke test for the production backend image: start it the way production should run it
# (read-only root filesystem, no capabilities, non-root, configuration only from environment
# variables) next to a throwaway PostgreSQL, and check that it really works end to end:
# Flyway migrates an empty database, the readiness probe reports UP, security is active, and the
# bootstrap admin can log in and call an authenticated endpoint.
#
#   .github/scripts/smoke-test-backend-image.sh cloudforge-backend:<tag>
#
# Every credential is random and lives only for this run. No real database is ever used.
set -euo pipefail

image="${1:?usage: $0 <image>}"
postgres_image="${POSTGRES_IMAGE:-postgres:17.11-alpine@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b}"
id="cf-smoke-$$"
port="${SMOKE_PORT:-18090}"
base="http://127.0.0.1:$port"
failures=0

db_password=$(openssl rand -hex 16)
admin_email="smoke-admin@example.com"
admin_password="Smoke-$(openssl rand -hex 12)"

cleanup() {
  if [[ "$failures" -ne 0 ]]; then
    echo "---- backend logs ----"
    docker logs "$id-app" 2>&1 | tail -80 || true
  fi
  docker rm -f "$id-app" "$id-db" >/dev/null 2>&1 || true
  docker network rm "$id" >/dev/null 2>&1 || true
}
trap cleanup EXIT

check() { # check <description> <command...>
  local description="$1"; shift
  if "$@"; then
    echo "✅ $description"
  else
    echo "::error title=Backend image smoke test::$description"
    failures=$((failures + 1))
  fi
}
# "|| true": a failed request shows up as status 000 (and keeps Git Bash on Windows working).
status() { curl -s -o /dev/null -w '%{http_code}' "$@" || true; }

docker network create "$id" >/dev/null
docker run -d --name "$id-db" --network "$id" \
  -e POSTGRES_DB=cloudforge -e POSTGRES_USER=cloudforge -e POSTGRES_PASSWORD="$db_password" \
  "$postgres_image" >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$id-db" pg_isready -U cloudforge -d cloudforge >/dev/null 2>&1; then break; fi
  sleep 1
done

docker run -d --name "$id-app" --network "$id" \
  --read-only --tmpfs /tmp \
  --cap-drop ALL --security-opt no-new-privileges \
  --memory 768m \
  -p "127.0.0.1:$port:8080" \
  -e CLOUDFORGE_DB_URL="jdbc:postgresql://$id-db:5432/cloudforge" \
  -e CLOUDFORGE_DB_USERNAME=cloudforge \
  -e CLOUDFORGE_DB_PASSWORD="$db_password" \
  -e CLOUDFORGE_JWT_SECRET="$(openssl rand -base64 32)" \
  -e CLOUDFORGE_BOOTSTRAP_ADMIN_EMAIL="$admin_email" \
  -e CLOUDFORGE_BOOTSTRAP_ADMIN_PASSWORD="$admin_password" \
  -e CLOUDFORGE_BOOTSTRAP_ADMIN_DISPLAY_NAME="Smoke Test Admin" \
  "$image" >/dev/null

# Spring Boot + Flyway on an empty database: usually 10-20 s; allow 3 minutes for slow runners.
ready=false
for _ in $(seq 1 90); do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$id-app")" != true ]]; then
    echo "The backend container exited during startup."
    break
  fi
  if curl -fsS "$base/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then ready=true; break; fi
  sleep 2
done

check "starts and reports ready (/actuator/health/readiness = UP)" test "$ready" = true
if [[ "$ready" == true ]]; then
  check "liveness probe is UP" grep -q '"UP"' <<<"$(curl -fsS "$base/actuator/health/liveness" || true)"
  check "runs as the unprivileged user (uid 10001)" test "$(docker exec "$id-app" id -u)" = 10001
  check "API requires authentication (/api/users/me without a token → 401)" test "$(status "$base/api/users/me")" = 401
  check "API docs are disabled outside the local profile (/v3/api-docs → not 200)" \
    test "$(status "$base/v3/api-docs")" != 200

  login=$(curl -s -H 'Content-Type: application/json' \
    -d "{\"email\":\"$admin_email\",\"password\":\"$admin_password\"}" "$base/api/auth/login" || true)
  token=$(sed -nE 's/.*"accessToken":"([^"]+)".*/\1/p' <<<"$login")
  check "bootstrap admin can log in (POST /api/auth/login → access token)" test -n "$token"
  if [[ -n "$token" ]]; then
    check "authenticated call works (/api/users/me with the token → 200)" \
      test "$(status -H "Authorization: Bearer $token" "$base/api/users/me")" = 200
  fi
  check "no ERROR lines in the startup log" test -z "$(docker logs "$id-app" 2>&1 | grep ' ERROR ' || true)"
fi

if [[ "$failures" -ne 0 ]]; then
  echo "$failures check(s) failed."
  exit 1
fi
echo "Backend image smoke test passed."

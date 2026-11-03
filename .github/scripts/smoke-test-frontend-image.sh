#!/usr/bin/env bash
# Smoke test for the production frontend image: start it the way production should run it
# (read-only root filesystem, no capabilities, non-root) and check that NGINX serves the SPA with
# the expected routing, caching and security headers.
#
#   .github/scripts/smoke-test-frontend-image.sh cloudforge-frontend:<tag>
#
# No backend is started: /api must reach the proxy (and fail with a gateway error), never the SPA.
set -euo pipefail

image="${1:?usage: $0 <image>}"
name="cf-frontend-smoke-$$"
port="${SMOKE_PORT:-18080}"
base="http://127.0.0.1:$port"
failures=0

cleanup() {
  if [[ "$failures" -ne 0 ]]; then
    echo "---- container logs ----"
    docker logs "$name" 2>&1 | tail -50 || true
  fi
  docker rm -f "$name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

check() { # check <description> <command...>
  local description="$1"; shift
  if "$@"; then
    echo "✅ $description"
  else
    echo "::error title=Frontend image smoke test::$description"
    failures=$((failures + 1))
  fi
}
# "|| true": a failed request already shows up as status 000. It also keeps the script usable from
# Git Bash on Windows (MSYS_NO_PATHCONV=1), where curl can't open /dev/null and exits 23.
status() { curl -s -o /dev/null -w '%{http_code}' "$base$1" || true; }
header() { curl -s -D - -o /dev/null "$base$1" | tr -d '\r' | grep -i "^$2:" | cut -d' ' -f2- || true; }

docker run -d --name "$name" \
  --read-only --tmpfs /tmp \
  --cap-drop ALL --security-opt no-new-privileges \
  -p "127.0.0.1:$port:8080" \
  -e CLOUDFORGE_BACKEND_URL=http://cloudforge-backend.invalid:8080 \
  "$image" >/dev/null

# NGINX starts in about a second; allow 30 s for slow runners.
for _ in $(seq 1 30); do
  if curl -fsS "$base/healthz" >/dev/null 2>&1; then break; fi
  sleep 1
done

uid=$(docker exec "$name" id -u)
check "runs as a non-root user (uid $uid)" test "$uid" != 0
check "/healthz answers 200" test "$(status /healthz)" = 200

index=$(curl -fsS "$base/" || true)
check "/ serves the SPA entry page" grep -q '<div id="root"></div>' <<<"$index"
check "client-side routes fall back to the SPA (/requests/123 → 200)" test "$(status /requests/123)" = 200
check "entry page is revalidated (Cache-Control: no-cache)" test "$(header / cache-control)" = no-cache
check "Content-Security-Policy is set" test -n "$(header / content-security-policy)"
check "X-Content-Type-Options: nosniff" test "$(header / x-content-type-options)" = nosniff
check "X-Frame-Options: DENY" test "$(header / x-frame-options)" = DENY

asset=$(grep -oE '/assets/[^"]+[.]js' <<<"$index" | head -1 || true)
check "the entry page references a hashed JS bundle" test -n "$asset"
if [[ -n "$asset" ]]; then
  check "hashed assets are served ($asset → 200)" test "$(status "$asset")" = 200
  check "hashed assets are cached immutably" grep -q immutable <<<"$(header "$asset" cache-control)"
fi
check "source maps are not shipped" test -z "$(docker exec "$name" find /usr/share/nginx/html -name '*.map')"

# The backend is unreachable on purpose: a gateway error proves /api goes to the proxy.
api_status=$(status /api/users/me)
check "/api is proxied to the backend (got $api_status; 502/504 expected without a backend)" \
  grep -qxE '50[24]' <<<"$api_status"

if [[ "$failures" -ne 0 ]]; then
  echo "$failures check(s) failed."
  exit 1
fi
echo "Frontend image smoke test passed."

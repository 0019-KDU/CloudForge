#!/usr/bin/env bash
# Resolve the backend's Maven dependencies into the local repository, retrying when Maven Central
# rate-limits the runner (HTTP 429). Shared GitHub-hosted runner addresses hit that limit when the
# dependency cache misses (any pom.xml change) and several jobs download everything at once.
#
#   .github/scripts/maven-resolve-with-retry.sh        (run from the repository root)
#
# Each attempt keeps what earlier attempts downloaded, so a retry only fetches what is still missing.
set -euo pipefail

attempts=4
delay=30

cd backend
for attempt in $(seq 1 "$attempts"); do
  if ./mvnw --batch-mode --no-transfer-progress dependency:resolve; then
    exit 0
  fi
  if [[ "$attempt" -lt "$attempts" ]]; then
    echo "::warning title=Maven dependency download failed::Attempt $attempt of $attempts failed (often HTTP 429 from Maven Central). Retrying in ${delay}s."
    sleep "$delay"
    delay=$((delay * 2))
  fi
done

echo "::error title=Maven dependency download failed::All $attempts attempts failed. If the log shows HTTP 429, Maven Central is rate-limiting the runner; re-run the job later."
exit 1

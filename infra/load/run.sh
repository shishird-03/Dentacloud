#!/usr/bin/env bash
# Runs the k6 load test against a running API (default http://localhost:8095) and the compose database:
#   infra/load/run.sh
# 1. signs up the "big" clinic and seeds it with 100,000 patients (seed-100k.sql)
# 2. runs k6.js in Docker; the summary goes to infra/load/summary.json
# Exit code is k6's: non-zero when any threshold fails.
set -euo pipefail
cd "$(dirname "$0")/../.."

API="${API_URL:-http://localhost:8095}"
DB_USER="${DB_OWNER_USER:-dentacloud}"
# Rerun against an already seeded clinic with BIG_EMAIL=<its owner email> to skip the 100k seed.
email="${BIG_EMAIL:-}"
if [ -z "$email" ]; then
  email="load-big+$(date +%s)@test.clinic"
  resp=$(curl -sf -X POST "$API/api/v1/auth/signup" -H 'Content-Type: application/json' \
    -d "{\"clinicName\":\"Load Big Dental\",\"ownerName\":\"Dr Big\",\"email\":\"$email\",\"password\":\"password123\"}")
  clinic=$(sed -n 's/.*"clinicId":"\([^"]*\)".*/\1/p' <<<"$resp")
  [ -n "$clinic" ] || { echo "signup failed: $resp"; exit 1; }

  echo "seeding 100,000 patients into clinic $clinic (owner $email)"
  docker compose -f infra/docker-compose.yml exec -T postgres \
    psql -U "$DB_USER" -d dentacloud -v ON_ERROR_STOP=1 -q -v clinic="$clinic" -f - < infra/load/seed-100k.sql
fi

# host.docker.internal reaches the API on the host from inside the k6 container.
export MSYS_NO_PATHCONV=1
docker run --rm -i -v "$(pwd)/infra/load:/out" \
  -e BASE_URL="${API/localhost/host.docker.internal}" -e BIG_EMAIL="$email" \
  grafana/k6:0.54.0 run - < infra/load/k6.js

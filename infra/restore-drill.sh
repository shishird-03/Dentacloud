#!/usr/bin/env bash
# Restore drill (PRD 13, 16: monthly): restores a backup into a throwaway Postgres container and
# checks every table's row count and the audit-chain digest against the backup's .counts manifest.
#   BACKUP_PASSPHRASE=... infra/restore-drill.sh [backups/dentacloud-<UTC>.dump.gpg]   (default: newest)
# Exit code 0 means the backup is restorable and complete.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${BACKUP_PASSPHRASE:?set BACKUP_PASSPHRASE}"
DIR="${BACKUP_DIR:-backups}"
file="${1:-$(ls -1 "$DIR"/dentacloud-*.dump.gpg | sort | tail -n 1)}"
manifest="${file%.dump.gpg}.counts"
[ -f "$manifest" ] || { echo "missing manifest $manifest"; exit 1; }
DB_USER="${DB_OWNER_USER:-dentacloud}"
container="dentacloud-drill-$$"

# MSYS_NO_PATHCONV stops Git Bash on Windows rewriting the container paths below.
export MSYS_NO_PATHCONV=1
docker run -d --rm --name "$container" \
  -e POSTGRES_DB=dentacloud -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD=drill \
  -e APP_DB_USER="${DB_APP_USER:-dentacloud_app}" -e APP_DB_PASSWORD=drill \
  -v "$(pwd)/infra/postgres-init:/docker-entrypoint-initdb.d:ro" \
  postgres:16-alpine > /dev/null
trap 'docker rm -f "$container" > /dev/null 2>&1 || true' EXIT

# The image restarts once after running init scripts; wait for the final server.
for _ in $(seq 1 60); do
  if docker logs "$container" 2>&1 | grep -q "PostgreSQL init process complete" \
     && docker exec "$container" pg_isready -U "$DB_USER" -d dentacloud > /dev/null 2>&1; then
    break
  fi
  sleep 1
done

infra/restore.sh "$file" "$container"
docker exec -i "$container" psql -U "$DB_USER" -d dentacloud -At -v ON_ERROR_STOP=1 -f - \
  < infra/backup-counts.sql > "$container.counts"

if diff -u "$manifest" "$container.counts"; then
  echo "DRILL PASSED: $file restores with $(grep -c '=' "$manifest") checks matching"
  rm -f "$container.counts"
else
  echo "DRILL FAILED: restored data differs from $manifest (diff above)"
  rm -f "$container.counts"
  exit 1
fi

#!/usr/bin/env bash
# Encrypted database backup (PRD 14, 16). Run daily from cron, e.g.
#   15 2 * * *  cd /srv/dentacloud && BACKUP_PASSPHRASE=... infra/backup.sh
# Writes backups/dentacloud-<UTC>.dump.gpg (pg_dump custom format, gpg AES-256, integrity-checked)
# and a plaintext .counts manifest (row counts and the audit-chain fingerprint, no patient data)
# that restore-drill.sh checks the restore against.
# ponytail: local directory only; copy backups/ to a second region or account (PRD 16) with your host's tooling.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${BACKUP_PASSPHRASE:?set BACKUP_PASSPHRASE (keep it in your secrets store, not in the repo)}"
DIR="${BACKUP_DIR:-backups}"
KEEP_DAYS="${BACKUP_RETENTION_DAYS:-35}"
DB_USER="${DB_OWNER_USER:-dentacloud}"
COMPOSE=(docker compose -f infra/docker-compose.yml)

mkdir -p "$DIR"
name="$DIR/dentacloud-$(date -u +%Y%m%dT%H%M%SZ)"

# ponytail: counts and dump are two snapshots; a write in between shows up as a drill mismatch.
# Run in a quiet window; pg_dump --snapshot with an exported snapshot if that ever bites.
"${COMPOSE[@]}" exec -T postgres psql -U "$DB_USER" -d dentacloud -At -v ON_ERROR_STOP=1 \
  -f - < infra/backup-counts.sql > "$name.counts"

"${COMPOSE[@]}" exec -T postgres pg_dump -U "$DB_USER" -d dentacloud -Fc \
  | gpg --batch --yes --symmetric --cipher-algo AES256 --passphrase-fd 3 -o "$name.dump.gpg" 3<<<"$BACKUP_PASSPHRASE"

find "$DIR" -name 'dentacloud-*' -mtime +"$KEEP_DAYS" -delete
echo "backup written: $name.dump.gpg ($(wc -c < "$name.dump.gpg") bytes)"

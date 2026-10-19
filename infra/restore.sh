#!/usr/bin/env bash
# Restores an encrypted backup into a running Postgres container:
#   BACKUP_PASSPHRASE=... infra/restore.sh backups/dentacloud-<UTC>.dump.gpg <container>
# The target must already have the app role (infra/postgres-init creates it) and an empty
# "dentacloud" database. For production, restore into a fresh instance, run restore-drill checks,
# then repoint the API; never restore over the live database.
set -euo pipefail

: "${BACKUP_PASSPHRASE:?set BACKUP_PASSPHRASE}"
file="${1:?usage: restore.sh <backup.dump.gpg> <container>}"
container="${2:?usage: restore.sh <backup.dump.gpg> <container>}"
DB_USER="${DB_OWNER_USER:-dentacloud}"

# gpg refuses to output data whose integrity check fails, so a tampered file stops here.
gpg --batch --quiet --decrypt --passphrase-fd 3 "$file" 3<<<"$BACKUP_PASSPHRASE" \
  | docker exec -i "$container" pg_restore -U "$DB_USER" -d dentacloud --exit-on-error --no-owner --role="$DB_USER"
echo "restored $file into $container"

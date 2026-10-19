# Runbook: database backups and restore

Owner: A (backend and security). Backup owner: B. PRD 13 (RPO/RTO), 14 (backups), 16 (DR).

## What you need, and where it lives

| Secret | Used for | Where it must live |
|---|---|---|
| `BACKUP_PASSPHRASE` | Encrypting and decrypting backup files (gpg AES-256) | Secrets store, readable by the backup job and two named people |
| `FIELD_ENC_KEY` | Master key that unwraps each clinic's data key | Secrets store, **separately from the backups** |
| `BLIND_INDEX_KEY` | Phone search (HMAC) | Same as `FIELD_ENC_KEY` |

A backup restores fine without `FIELD_ENC_KEY`, but every encrypted field (phone, address, history, allergies, visit notes) stays unreadable without it. Losing it loses that data. Storing it next to the backups means anyone holding a backup can read it. Keep both, and keep them apart.

## Daily backup

```bash
# cron on the database host, 02:15 every day
15 2 * * *  cd /srv/dentacloud && BACKUP_PASSPHRASE=... infra/backup.sh >> /var/log/dentacloud-backup.log 2>&1
```

Each run writes two files to `backups/` (override with `BACKUP_DIR`):
- `dentacloud-<UTC>.dump.gpg`: `pg_dump` custom format, encrypted. gpg refuses to decrypt a modified file.
- `dentacloud-<UTC>.counts`: row count per table plus a digest of the audit hash chain. It contains no patient data, and the drill compares against it.

Files older than 35 days are deleted (`BACKUP_RETENTION_DAYS`).

**Not automated yet:** copying `backups/` to a second region or account (PRD 16), and WAL archiving for the 15-minute RPO. Until both exist, the real RPO is 24 hours and a lost host loses its backups. Add them before onboarding a real clinic.

## Monthly restore drill

```bash
BACKUP_PASSPHRASE=... infra/restore-drill.sh                  # newest backup
BACKUP_PASSPHRASE=... infra/restore-drill.sh backups/<file>   # a specific one
```

The drill starts a throwaway `postgres:16-alpine` container, restores into it, compares every table's row count and the audit-chain digest with the `.counts` manifest, and then removes the container. `DRILL PASSED` with exit code 0 means the backup is restorable and complete. Record the date and result in the ops log.

A `DRILL FAILED` diff on a busy table usually means writes landed between the counts and the dump. Rerun the drill on a backup taken in a quiet window. Any other failure means the backup is bad, so treat it as an incident.

Last verified: 19 Oct 2026. The drill passed (28/28), and it failed as it should on a wrong passphrase, a tampered file and a manifest mismatch.

## Real restore (incident)

1. Stop writes: scale the API to zero, or put it in maintenance.
2. Start a **fresh** Postgres 16 instance or container with the app role (`infra/postgres-init`) and an empty `dentacloud` database. Never restore over the live database.
3. Restore and check:
   ```bash
   BACKUP_PASSPHRASE=... infra/restore.sh backups/<file> <container>
   ```
   Then run `infra/backup-counts.sql` against the new instance and diff it with the backup's `.counts`.
4. Point the API at the new instance with the **same** `FIELD_ENC_KEY` and `BLIND_INDEX_KEY`, then start it.
5. Log in as an owner, open a patient (phone and allergies should be readable) and call `GET /api/v1/audit-events/verify`. It should return `"valid": true`.
6. Writes since the backup are lost (RPO). Tell the affected clinics, following the incident response plan.

Target RTO is 1 hour (PRD 13).

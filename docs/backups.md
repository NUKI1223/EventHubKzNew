# Database backups & restore

All EventHub data lives in Postgres (7 databases across 5 instances). These scripts back them
all up with `pg_dump` via `docker exec`, so they work identically in dev and on the deploy host —
no published DB ports required.

| Instance | Databases |
|---|---|
| postgres-users | users_db |
| postgres-events | events_db |
| postgres-likes | likes_db |
| postgres-registrations | registrations_db |
| postgres-notifications | notifications_db, audit_db, ingestion_db |

## Back up now

```bash
./scripts/backup-db.sh
```

Writes gzipped dumps to `./backups/<db>-<YYYYmmdd-HHMMSS>.sql.gz` and keeps the newest 14 per
database (override with `BACKUP_KEEP`, `BACKUP_DIR`). `backups/` is git-ignored.

## Schedule (production)

Add a host cron entry (the deploy user must be able to run `docker`). Daily at 03:30:

```cron
30 3 * * * cd /opt/eventhubkz && BACKUP_KEEP=30 ./scripts/backup-db.sh >> /var/log/eventhub-backup.log 2>&1
```

## Restore

```bash
./scripts/restore-db.sh <db_name> backups/<db>-<timestamp>.sql.gz
```

It auto-finds the instance hosting `<db>`, asks for a typed `restore` confirmation, then loads the
dump (`ON_ERROR_STOP`). To restore onto a **fresh** DB/instance (real disaster recovery), create the
empty database first, then `gunzip -c <dump> | docker exec -i <instance> psql -U postgres -d <db>`.

Restorability is verified: an `events_db` dump restored into a throwaway database reproduced all
tables and rows.

## Offsite (do before real launch)

Local dumps die with the server. Ship `backups/` offsite on a schedule — e.g. Oracle Object
Storage / S3 via `rclone` or the OCI CLI, or `restic` for encrypted incremental backups. This is
the one piece that still needs the deploy environment (a bucket + credentials) and is left for the
deploy step.

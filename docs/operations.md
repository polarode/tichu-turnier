# Operations runbook

Day-to-day operation of the production deploy: the app on the operator's VPS
under Coolify, with Postgres running on the same box. The deployment topology
(image pipeline, environment variables, HTTPS/DNS setup, verification
checklist) lives in [`deploy/README.md`](../deploy/README.md) — this document
covers everything after first boot.

The box is small: 1 vCPU, 2 GB RAM, 60 GB disk, 2 GB swapfile
(provisioned by [`scripts/provision-vps.sh`](../scripts/provision-vps.sh)).
Keep that in mind for every command below.

## Coolify app logs

Tail the app's stdout from the Coolify dashboard: open the app resource →
**Logs**. The streaming view shows the Spring Boot log; Flyway migration
output, `ddl-auto: validate` results and stack traces all appear here. The
**Deployments** tab keeps the log of each individual deploy (useful for
checking "did the migrations run cleanly on this boot?").

From the VPS shell, container logs work too:

```bash
docker logs -f <app-container-name>        # follow
docker logs --since 1h <app-container-name>  # last hour
```

`GET /health` answers `{"status":"UP"}` unauthenticated and includes a
database check — an unreachable database yields 503.

## Postgres backups

Coolify-managed Postgres resources support scheduled dumps. In the Postgres
resource's dashboard, configure a **scheduled backup** (daily is fine for this
app's data volume) writing to the backup directory Coolify manages on the VPS.

Scheduled dumps alone are **not enough**: they live on the same 2 GB box as
the database. Copy dumps off the VPS as part of your routine — e.g. after each
backup cycle or weekly, from your own machine:

```bash
scp <vps-user>@<vps-ip>:<coolify-backup-dir>/<dump>.dump ~/backups/tichu/
```

(Where the backup directory is depends on your Coolify setup; find it under
the Postgres resource's **Backups** tab.) Keep at least a few generations
off-box.

## Restore drill

A backup you haven't restored is a backup you don't have. Run this drill
after the first scheduled backup has happened, and re-run it whenever the
backup procedure changes (at minimum: try it once a quarter).

1. On the VPS (or locally — the drill works anywhere with Docker), start a
   throwaway Postgres container with a fresh volume:

   ```bash
   docker run -d --name restore-drill -e POSTGRES_PASSWORD=drill \
     -e POSTGRES_DB=drill postgres:16
   ```

2. Copy the latest dump in and restore it:

   ```bash
   docker cp <dump>.dump restore-drill:/tmp/dump.dump
   docker exec restore-drill pg_restore -U postgres -d drill \
     --clean --if-exists /tmp/dump.dump
   ```

   (Use `psql -f /tmp/dump.sql` instead if the backup is a plain SQL dump.)

3. Spot-check the data:

   ```bash
   docker exec restore-drill psql -U postgres -d drill \
     -c '\dt' -c 'select count(*) from tournaments;'
   ```

   Tables exist, row counts look plausible.

4. Optionally boot the app against the restored database
   (`SPRING_DATASOURCE_URL` pointing at the drill container) and log in —
   that proves the restore end to end, including Flyway's `ddl-auto: validate`
   passing against the restored schema.

5. Clean up: `docker rm -f restore-drill`.

Record the drill date and result in the issue or your ops notes.

## Credential rotation

### Owner password

The owner account is provisioned idempotently at startup from
`OWNER_EMAIL` / `OWNER_PASSWORD_HASH` (see
[`deploy/README.md`](../deploy/README.md)). To rotate the owner password:

1. Generate a new bcrypt hash (e.g. with `htpasswd -bnBC 10 "" <new-password>`
   and drop the leading `:`).
2. Update `OWNER_PASSWORD_HASH` in the app resource's environment variables in
   Coolify.
3. Redeploy the app resource. Provisioning is idempotent — the existing owner
   account gets the new hash.
4. Verify: log in at `/admin` with the new password.

### Database credentials

Rotate in an order that never breaks the running app's connection for longer
than one redeploy:

1. Change the Postgres role's password. From the VPS:

   ```bash
   docker exec -it <postgres-container> psql -U <db-user> -d <db-name> \
     -c "ALTER ROLE <db-user> WITH PASSWORD '<new-password>';"
   ```

2. Immediately update the app resource's environment variables in Coolify:
   `SPRING_DATASOURCE_PASSWORD` and `SPRING_FLYWAY_PASSWORD` (both must match;
   with the on-VPS Postgres there is no pooler, so both URLs stay the same).
3. Redeploy the app resource.
4. Verify: `curl https://<subdomain>/health` → `{"status":"UP"}`; check the
   app logs for connection errors.

Step 1–2 is a brief window where the old app instance fails connections; with
the connection pool it usually rides it out, and the redeploy fixes it
regardless. Do not leave the gap open — do steps 1–3 in one sitting.

## VPS patching

### Baseline: unattended-upgrades

Security updates should apply themselves. On the VPS:

```bash
apt install unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades   # enable
```

Verify it's active and applying:

```bash
systemctl status unattended-upgrades
cat /var/log/unattended-upgrades/unattended-upgrades.log | tail
```

Reboots when the kernel changes need a human: check
`/var/run/reboot-required` occasionally and reboot at a quiet moment
(`sudo reboot`) — the stack (Coolify, containers, swapfile in `/etc/fstab`)
comes back on its own.

### Monthly checklist

A short routine, once a month:

- [ ] **Coolify version**: update Coolify itself (dashboard → Settings, or
      `curl -fsSL https://cdn.coollabs.io/coolify/install.sh | bash` per the
      Coolify docs) — small frequent updates beat rare big ones.
- [ ] **Image currency**: confirm the running container matches the latest
      `main` build (`sha-<sha>` tag on
      `ghcr.io/polarode/tichu-turnier`); if auto-deploy stalled, trigger a
      redeploy from the Coolify dashboard.
- [ ] **Reboot check**: `/var/run/reboot-required` pending? Reboot.
- [ ] **Disk space**: `df -h /` — dumps and Docker layers accumulate;
      `docker system prune -f` if usage creeps up.
- [ ] **Backups still happening**: check the backup timestamps in the Postgres
      resource's Backups tab, and confirm an off-box copy exists.
- [ ] **Swap/memory**: `free -h` — swap should be mostly idle; sustained swap
      pressure means it's time to look at what's growing.

## See also

- [`deploy/README.md`](../deploy/README.md) — image, environment variables,
  HTTPS/DNS topology, production verification checklist.
- [`scripts/provision-vps.sh`](../scripts/provision-vps.sh) — the
  provisioning wizard that sets this stack up.

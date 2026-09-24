# Production Update and Save Safety

Current production is managed by PufferPanel 3 on the dedicated host `95.216.71.232`; the Void server uses PufferPanel Host mode, not Docker. Follow `Hetzner-Cloud-Deployment.md` for the complete build and deploy procedure.

## Authoritative data

- Live saves: `/srv/games/pufferpanel/servers/a59b8fa2/data/saves/`
- Backups: `/srv/void-cloud-rsps-backups/`
- Old-host migration copy: `/srv/void-cloud-rsps-data/saves/` (preserved, not live)
- PufferPanel server root: `/srv/games/pufferpanel/servers/a59b8fa2/`

PufferPanel Host runs the server in an `unshare` filesystem view. The live saves must remain a real directory under the panel server root; an external symlink is invisible there. Keep code under `/opt/void` and persistent data under the panel root. Updating code/JAR must never replace `data/saves`.

## Required backup before maintenance

Announce downtime, stop the server from PufferPanel, then create and verify an archive:

```bash
server=/srv/games/pufferpanel/servers/a59b8fa2
backup_dir=/srv/void-cloud-rsps-backups/$(date +%Y-%m-%d_%H-%M-%S)
mkdir -p "$backup_dir"
tar -C "$server/data" -czf "$backup_dir/saves.tar.gz" saves
test -s "$backup_dir/saves.tar.gz"
gzip -t "$backup_dir/saves.tar.gz"
sha256sum "$backup_dir/saves.tar.gz"
```

Keep a bounded retention set and copy important backups off the server when practical. Do not delete the migration archive or old-host saves until a separate rollback decision is made.

## Safe code update

Build in `/opt/void` while the current runtime remains untouched. For deployment, stop via PufferPanel, create the verified backup above, replace only `void-server.jar`, then start from PufferPanel and smoke-test an existing account. Never copy a fresh checkout over the PufferPanel server root.

## Absolute data-safety rules

Never during normal maintenance:

- Run `git clean -fdx` in `/opt/void`.
- Delete or replace `/srv/games/pufferpanel/servers/a59b8fa2/data/saves`.
- Replace saves with an empty folder or restore over live data while the server is running.
- Run old-host `docker compose down -v` or restart the old production Compose stack.
- Delete `/srv/void-cloud-rsps-data` or old-host saves/backups during the rollback window.
- Change `storage.type=files` to a database backend without a separately tested migration.

Before any operation that may delete, overwrite, or restore saves, state the exact target, verify a recent archive, and get explicit approval.

## Recovery

Stop the PufferPanel server, preserve the current failed saves separately, verify the chosen archive with `gzip -t`, then restore its `saves/` directory into the panel server's `data/` directory. Preserve `pufferpanel:pufferpanel` ownership. Start only after verifying the restored file count and paths. The database is not the player-storage backend.

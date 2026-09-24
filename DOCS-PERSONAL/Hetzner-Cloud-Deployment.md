# Hetzner RSPS Deployment and Operations

This is the current deployment guide for the dedicated Hetzner server. It replaces the former Docker Compose deployment on the 4 GB Hetzner Cloud VM.

## Current deployment

- Public game endpoint: `95.216.71.232:43594` (TCP)
- Host: Ubuntu 24.04, Intel Core i7-8700 (6 cores / 12 threads), 64 GB RAM, mirrored NVMe
- Source checkout/build workspace: `/opt/void`
- Branch/revision at migration: `personal-tweaks`, `b2cff164c`
- Manager: PufferPanel 3, **Host** environment
- PufferPanel server ID: `a59b8fa2`
- PufferPanel server directory: `/srv/games/pufferpanel/servers/a59b8fa2/`
- Runtime JAR: `/srv/games/pufferpanel/servers/a59b8fa2/void-server.jar`
- Runtime data/cache: `/srv/games/pufferpanel/servers/a59b8fa2/data/`
- Authoritative saves: `/srv/games/pufferpanel/servers/a59b8fa2/data/saves/`
- Local backup directory: `/srv/void-cloud-rsps-backups/`
- Java: OpenJDK 21
- Runtime options: `-Xms1g -Xmx8g -XX:MaxMetaspaceSize=1g`

PufferPanel Host uses an `unshare` filesystem view. Keep saves physically inside the panel server directory: a symlink to `/srv/void-cloud-rsps-data/saves` is not visible in the server's isolated view and caused `FileNotFoundException` for `data/saves/logs`. Do not re-create that symlink. The old external saves copy is retained for recovery, but the panel directory is authoritative once the new server is started and verified.

Host mode is simpler for this Java application, but it is not a security boundary comparable to Docker, and it does not enforce CPU/RAM cgroup limits. PufferPanel launches the process as its `pufferpanel` user. The 8 GB JVM heap ceiling is the current safeguard; monitor actual usage as other games are added. The template is in `deploy/pufferpanel/void-rsps-host.json`.

## Migration status (2026-09-24)

The old server at `2.28.141.196` has been stopped and left intact for rollback. A final saves archive was created at `/srv/void-cloud-rsps-backups/2026-09-24_05-21-24-final-migration/saves.tar.gz`, passed `gzip -t`, and has matching SHA-256 on both hosts (`e73c4c757dc9b441101ed41da30a23d1232936c378a943506e48e89ef152cb99`). Its 50 files were restored to the new host. The cache was copied and checksum-verified; the JAR built successfully and its copied checksum matched.

The new PufferPanel instance was started once, reported that `data/saves/logs` was missing in its isolated view, and was stopped. The save data has since been copied into the panel-managed directory. Restart the instance from PufferPanel and confirm clean game-loop/audit logging, existing-account login, save, logout, and relogin before considering cutover complete. The old host remains stopped, not deleted.

## Build and deploy an update

Build on the dedicated host:

```bash
cd /opt/void
git status --short --branch
./gradlew --stop
./gradlew :game:build -x test --no-daemon
test -s game/build/libs/void-server-dev.jar
```

Then, during announced maintenance, use PufferPanel to stop server `a59b8fa2` and create a consistent save backup before replacing the JAR:

```bash
server=/srv/games/pufferpanel/servers/a59b8fa2
backup_dir=/srv/void-cloud-rsps-backups/$(date +%Y-%m-%d_%H-%M-%S)
mkdir -p "$backup_dir"
tar -C "$server/data" -czf "$backup_dir/saves.tar.gz" saves
test -s "$backup_dir/saves.tar.gz"
gzip -t "$backup_dir/saves.tar.gz"
cp /opt/void/game/build/libs/void-server-dev.jar "$server/void-server.jar"
chown pufferpanel:pufferpanel "$server/void-server.jar"
```

Start the server from PufferPanel, inspect its Console, and test an existing account. Never replace the JAR while the game process is running. Do not run the old Compose deployment for this instance.

## Restore saves

Stop the PufferPanel server first and preserve the current save directory before restoring. Verify the selected archive with `gzip -t`. Restore into the panel-managed `data/` directory so the archive's `saves/` directory lands at `.../a59b8fa2/data/saves/`. Keep ownership `pufferpanel:pufferpanel`. Do not restore while the game is running, and do not restore into `/opt/void/data/saves`.

## Client and port

Use `95.216.71.232` and TCP port `43594`. The desktop client requires `-ip` and `-p`, not the Java property `-Dvoid.server`. `client-hetzner/client.bat` now points at the new address; the distributable ZIP has not yet been rebuilt. Rebuild it after the server passes its smoke test so friends receive the new address. Port `8080` is not the game port; do not expose it for the RSPS.

## Troubleshooting

- Use the PufferPanel Console and server status for the game process.
- Use `journalctl -u pufferpanel --since '15 minutes ago' --no-pager` for panel/daemon events.
- Check `ss -lntp | grep ':43594'` for the game listener.
- Check `free -h` and `ps -eo pid,rss,args --sort=-rss | head` for host memory use.
- If `data/saves/...` reports `No such file or directory`, verify the saves are a real directory under the PufferPanel server root, not a symlink outside it.

## Legacy deployment

The previous host at `2.28.141.196` used `/opt/void`, Docker Compose, and `/srv/void-cloud-rsps-data/saves`. It is stopped and preserved for rollback only. Its old Compose start/update commands must not be used as the normal production procedure.

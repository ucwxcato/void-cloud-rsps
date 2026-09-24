# Control the RSPS in PufferPanel

The Void RSPS runs on Hetzner dedicated host `95.216.71.232` under PufferPanel server ID `a59b8fa2`, using the Host environment. The old server at `2.28.141.196` is stopped and preserved for rollback.

## Start, stop, restart

Use the PufferPanel server page for **Start**, **Stop**, and **Restart**. This keeps console output and process status managed. A graceful stop sends SIGTERM; the Java shutdown hook saves world/player state. Do not start the old Docker Compose instance.

## Console and status

Open the server's **Console** tab to check startup and live errors. A successful server should complete startup without repeated `GameLoop` errors. The recurring `warped_rat_secondary` drop-table warning is unrelated to connectivity and is not fatal.

For host-level checks over SSH:

```bash
ss -lntp | grep ':43594'
free -h
ps -eo pid,rss,args --sort=-rss | head
journalctl -u pufferpanel --since '15 minutes ago' --no-pager
```

The game listener should be TCP `43594`. Connect using `95.216.71.232:43594`.

## Build and deploy code

Build in `/opt/void`, then stop the panel server, back up saves, and replace only the JAR:

```bash
cd /opt/void
./gradlew --stop
./gradlew :game:build -x test --no-daemon
```

```bash
server=/srv/games/pufferpanel/servers/a59b8fa2
cp /opt/void/game/build/libs/void-server-dev.jar "$server/void-server.jar"
chown pufferpanel:pufferpanel "$server/void-server.jar"
```

Start via PufferPanel and check the Console. See `Hetzner-Cloud-Deployment.md` for the complete safe update process.

## Saves and backups

- Live saves: `/srv/games/pufferpanel/servers/a59b8fa2/data/saves/`
- Backups: `/srv/void-cloud-rsps-backups/`

Stop the server before backing up or restoring. Keep `data/saves` as a real directory within the PufferPanel server root; Host-mode `unshare` cannot see a symlink to the old external saves path.

## Client

Run `client-hetzner\\client.bat` on Windows. It uses the desktop JAR's `-ip 95.216.71.232 -p 43594` options. Do not substitute `-Dvoid.server`.

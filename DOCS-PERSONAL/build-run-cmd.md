# Build and Run Commands

Production is the PufferPanel-managed Void instance on the dedicated Hetzner host `95.216.71.232`, TCP `43594`. The RSPS uses PufferPanel's Host environment; do not use Docker Compose to start it.

## Compile

On the host:

```bash
cd /opt/void
./gradlew --stop
./gradlew :game:build -x test --no-daemon
```

The successful build artifact is `game/build/libs/void-server-dev.jar`. Java 21 is installed. The host has 64 GB RAM; current Gradle settings are known to build successfully, so do not alter them without a concrete need.

For a long build, monitor from a second SSH session:

```bash
free -h
pgrep -af 'GradleDaemon|KotlinCompileDaemon' || true
```

Do not start a second build while one is running.

## Deploy the JAR

Announce downtime, stop server `a59b8fa2` from PufferPanel, and back up its saves first. Then replace only the JAR:

```bash
server=/srv/games/pufferpanel/servers/a59b8fa2
cp /opt/void/game/build/libs/void-server-dev.jar "$server/void-server.jar"
chown pufferpanel:pufferpanel "$server/void-server.jar"
```

Start from PufferPanel and inspect its Console. Do not replace or symlink `data/saves`; it must remain a real directory under the PufferPanel server root because Host `unshare` cannot see the external migration path.

## Storage and backups

```properties
storage.type=files
storage.players.path=./data/saves/
```

Live saves: `/srv/games/pufferpanel/servers/a59b8fa2/data/saves/`. Back them up to `/srv/void-cloud-rsps-backups/` while the server is stopped. See `Production-Update-Safety.md` for the checked archive command and restore rules.

## Cache and client

The runtime cache is `/srv/games/pufferpanel/servers/a59b8fa2/data/cache/`; source cache is `/opt/void/data/cache/` and is not tracked in Git. The client connects to `95.216.71.232:43594` using `-ip` and `-p`; `client-hetzner/client.bat` now uses this address. Rebuild the distributable ZIP after the server passes its smoke test.

## Local development

For local development only:

```bash
./gradlew --stop
./gradlew :game:run
```

Never use `git clean -fdx` on production, and never place production saves inside a disposable build or image.

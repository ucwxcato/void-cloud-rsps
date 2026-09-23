# Hetzner Cloud Deployment Guide

This guide runs the `personal-tweaks` branch from `/opt/void/` with local Docker builds and file storage. Compile first with `./gradlew :game:build -x test --no-daemon`; the expected artifact is `game/build/libs/void-server-dev.jar`.

## Production storage model

- Source checkout: `/opt/void/`
- Authoritative player saves: `/srv/void-cloud-rsps-data/saves/`
- Save backups: `/srv/void-cloud-rsps-backups/`
- Container save path: `/app/data/saves/`
- Storage mode: `storage.type=files`

The external saves directory is real player data. Git updates and Docker image rebuilds must never replace it.

PostgreSQL is not the player-storage backend in this model. Do not migrate to database storage or delete any legacy `void-db-data` volume until a separate migration plan has been tested.

## 1. Install Docker and clone the branch

```bash
ssh root@YOUR_SERVER_IP
apt update
apt install -y git docker.io docker-compose-plugin tar
systemctl enable --now docker
docker --version
docker compose version
```

Clone the personal branch:

```bash
mkdir -p /opt
git clone --branch personal-tweaks --single-branch https://github.com/ucwxcato/void-cloud-rsps.git /opt/void
cd /opt/void
```

For later updates, update the existing checkout:

```bash
cd /opt/void
git fetch origin
git switch personal-tweaks
git pull --ff-only origin personal-tweaks
```

Never use `git clean -fdx` on this server.

## 2. Create persistent data directories

```bash
mkdir -p /srv/void-cloud-rsps-data/saves
mkdir -p /srv/void-cloud-rsps-backups
```

If saves already exist in `/opt/void/data/saves`, copy them once before production starts:

```bash
cp -a /opt/void/data/saves/. /srv/void-cloud-rsps-data/saves/
```

After this, `/srv/void-cloud-rsps-data/saves/` is authoritative. Never copy over it while the server is running.

## 3. Verify file storage

Before building, verify the source configuration:

```bash
grep -n '^storage.type' game/src/main/resources/game.properties
grep -n '^storage.players.path' game/src/main/resources/game.properties
```

Expected values:

```text
storage.type=files
storage.players.path=./data/saves/
```

Do not set `storage.type=database`. Existing TOML accounts are not automatically imported into PostgreSQL.

## 4. Install the game cache

The cache is not stored in Git. Upload its contents into `/opt/void/data/cache/` before the Docker image build:

```bash
find /opt/void/data/cache -type f | head
du -sh /opt/void/data/cache
```

## 5. Back up saves

Run this before every first start and update:

```bash
backup_dir='/srv/void-cloud-rsps-backups/'$(date +%Y-%m-%d_%H-%M-%S)
mkdir -p "$backup_dir"
tar -C /srv/void-cloud-rsps-data -czf "$backup_dir/saves.tar.gz" saves
test -s "$backup_dir/saves.tar.gz"
```

Keep important backups on separate storage as well as on Hetzner.

## 6. Build and start

```bash
cd /opt/void
./gradlew --stop
./gradlew :game:build -x test --no-daemon
docker compose build void
docker compose up -d
docker compose ps
docker compose logs --tail=200 void
```

Do not start Docker until the Compose file is aligned with file storage and the cache has been uploaded.

The game server uses TCP port `43594`. Port `8080` is only needed when the web client is enabled.

## 7. Normal update procedure

```bash
cd /opt/void
docker compose stop void
backup_dir='/srv/void-cloud-rsps-backups/'$(date +%Y-%m-%d_%H-%M-%S)
mkdir -p "$backup_dir"
tar -C /srv/void-cloud-rsps-data -czf "$backup_dir/saves.tar.gz" saves
test -s "$backup_dir/saves.tar.gz"
git fetch origin
git switch personal-tweaks
git pull --ff-only origin personal-tweaks
./gradlew :game:build -x test --no-daemon
docker compose build void
docker compose up -d
docker compose logs --tail=200 void
```

The code update changes the checkout and Docker image only. The external save directory remains in place.

## 8. Never run during normal deployment

- `git clean -fdx`
- `rm -rf /opt/void/data/saves`
- `rm -rf /srv/void-cloud-rsps-data`
- `docker compose down -v`
- Copying a fresh checkout over `/srv/void-cloud-rsps-data`

## 9. Smoke test and monitoring

After every deployment, test login, character loading, a save-changing action, logout, and relogin.

```bash
docker compose ps
docker compose logs -f --tail=100 void
du -sh /srv/void-cloud-rsps-data/saves
```

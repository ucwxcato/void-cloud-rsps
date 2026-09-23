# Hetzner Cloud Deployment Guide

This guide runs the `personal-tweaks` branch from `/opt/void/` with Docker Compose. It builds this checkout locally, keeps player saves outside the Git checkout, and keeps PostgreSQL in a persistent Docker volume.

## 1. Install Docker and clone the branch

SSH into the Hetzner server:

```bash
ssh root@YOUR_SERVER_IP
```

On a fresh Ubuntu or Debian server:

```bash
apt update
apt install -y git docker.io docker-compose-plugin tar
systemctl enable --now docker
docker --version
docker compose version
```

Clone your personal branch into `/opt/void/`:

```bash
mkdir -p /opt
git clone --branch personal-tweaks --single-branch https://github.com/ucwxcato/void-cloud-rsps.git /opt/void
cd /opt/void
```

For later code updates, update this checkout instead of copying over the data directories:

```bash
cd /opt/void
git fetch origin
git switch personal-tweaks
git pull --ff-only origin personal-tweaks
```

Never use `git clean -fdx` on this server.

## 2. Create persistent data directories

Keep saves outside `/opt/void/` so Git updates cannot remove them:

```bash
mkdir -p /srv/void-cloud-rsps-data/saves
mkdir -p /srv/void-cloud-rsps-backups
```

If saves already exist in `/opt/void/data/saves`, copy them once before production starts:

```bash
cp -a /opt/void/data/saves/. /srv/void-cloud-rsps-data/saves/
```

After this, `/srv/void-cloud-rsps-data/saves` is the authoritative save location. Never copy over it while the server is running.

## 3. Configure Docker Compose

Create `/opt/void/.env` with a unique database password:

```bash
cat > /opt/void/.env <<'EOF'
VOID_SAVES_DIR=/srv/void-cloud-rsps-data/saves
POSTGRES_PASSWORD=REPLACE_WITH_A_LONG_RANDOM_PASSWORD
EOF
chmod 600 /opt/void/.env
```

`.env` is ignored by Git. Do not commit or publicly share it.

The cache files under `data/cache/` are not stored in Git. Download the cache archive from the project's official installation instructions and extract it into `/opt/void/data/cache/` before building.

## 4. Back up before starting or updating

Always make a saves backup before starting a new build or updating code:

```bash
backup_dir="/srv/void-cloud-rsps-backups/$(date +%Y-%m-%d_%H-%M-%S)"
mkdir -p "$backup_dir"
tar -C /srv/void-cloud-rsps-data -czf "$backup_dir/saves.tar.gz" saves
```

After PostgreSQL is running, back it up too:

```bash
docker compose exec -T db pg_dump -U postgres game > "$backup_dir/game.sql"
```

Keep important backups somewhere other than the same Hetzner server.

## 5. Build and start the branch

The Compose file builds the uploaded branch locally. It does not use the upstream prebuilt image:

```bash
cd /opt/void
./gradlew --stop
./gradlew :game:build -x test --no-daemon
docker compose build void
docker compose up -d
```

Check startup:

```bash
docker compose ps
docker compose logs --tail=200 void
```

The game server uses TCP port `43594`. The web client, if enabled, uses port `8080`.

## 6. Hetzner firewall

Allow only the ports you need:

- TCP `22` for SSH, preferably restricted to your IP.
- TCP `43594` for the game client.
- TCP `8080` only if the web client is enabled.

Do not expose PostgreSQL port `5432`; it is available only inside Docker's internal network.

## 7. Normal update procedure

Use this sequence for every update:

```bash
cd /opt/void
docker compose stop void
backup_dir="/srv/void-cloud-rsps-backups/$(date +%Y-%m-%d_%H-%M-%S)"
mkdir -p "$backup_dir"
tar -C /srv/void-cloud-rsps-data -czf "$backup_dir/saves.tar.gz" saves
docker compose exec -T db pg_dump -U postgres game > "$backup_dir/game.sql"
git fetch origin
git switch personal-tweaks
git pull --ff-only origin personal-tweaks
./gradlew :game:build -x test --no-daemon
docker compose build void
docker compose up -d
docker compose logs --tail=200 void
```

`docker compose up -d` keeps the external saves directory and the `void-db-data` PostgreSQL volume. Do not use `docker compose down -v` during normal updates.

## 8. Commands that can destroy data

Do not run these during normal deployment:

- `git clean -fdx`
- `rm -rf /opt/void/data/saves`
- `rm -rf /srv/void-cloud-rsps-data`
- `docker compose down -v`
- A fresh checkout copied over `/srv/void-cloud-rsps-data`

## 9. Rollback

If the new build fails, stop the game, return to a known-good commit, rebuild, and restart:

```bash
cd /opt/void
docker compose stop void
git log --oneline -10
git reset --hard KNOWN_GOOD_COMMIT
./gradlew :game:build -x test --no-daemon
docker compose build void
docker compose up -d
docker compose logs --tail=200 void
```

Restore saves or the database only when runtime data was damaged or made incompatible. Preserve the failed backup before restoring anything.

## 10. Monitoring and smoke test

```bash
docker compose ps
docker compose logs -f --tail=100 void
docker stats
du -sh /srv/void-cloud-rsps-data/saves
```

Before announcing the server is online, test login, character loading, saving, logout, and logging in again.

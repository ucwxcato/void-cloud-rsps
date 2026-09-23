# AGENTS.md

## Project purpose

This repository is the personal production branch for a Void RuneScape private server.

- Personal repository: `https://github.com/ucwxcato/void-cloud-rsps.git`
- Upstream repository: `https://github.com/GregHib/void.git`
- Production branch: `personal-tweaks`
- Production checkout: `/opt/void`
- Target server: Hetzner Cloud Ubuntu 24.04

## Git remotes and branch model

- `origin` points to the personal fork.
- `upstream` points to `https://github.com/GregHib/void.git`.
- Local `main` is the clean upstream sync branch.
- `personal-tweaks` contains personal code, configuration, and documentation.

Normal upstream sync:

```bash
git status --short --branch
git fetch upstream
git switch main
git merge --ff-only upstream/main
git switch personal-tweaks
git merge --no-ff main -m 'sync: update personal branch from upstream'
```

Do not resolve conflicts by deleting runtime data. Review storage code, configuration, and any tracked data files manually.

## Production storage architecture

Production uses file storage, not PostgreSQL, for player accounts and progress.

```properties
storage.type=files
storage.players.path=./data/saves/
```

Player saves are persistent host data:

- Host: `/srv/void-cloud-rsps-data/saves/`
- Container: `/app/data/saves/`
- Backup directory: `/srv/void-cloud-rsps-backups/`

The Docker bind mount must map the host save directory to the container save directory. Existing TOML accounts are not automatically imported into PostgreSQL.

PostgreSQL is not part of the normal file-storage deployment. Do not migrate to database storage or delete any legacy `void-db-data` volume without a separately tested migration plan.

## Docker and image build

The personal Compose setup builds the local checkout. It must not use GregHib's prebuilt image.

- Compose service image: `void-cloud-rsps:personal-tweaks`
- Dockerfile input JAR: `game/build/libs/void-server-dev.jar`
- Game port: TCP `43594`
- Web port: TCP `8080` only when the web server is enabled
- PostgreSQL port `5432` must not be exposed publicly

## Working Hetzner client

The tracked client bundle is in `client-hetzner/`:

- `client-hetzner/void-client-1.2.0.jar`
- `client-hetzner/client.bat`

Run `client.bat` from that folder on Windows. The client must receive its
server address as the client's command-line option `-ip`; the Java system
property `-Dvoid.server=...` does not work for this desktop JAR.

The working launcher connects to `2.28.141.196:43594` using:

```bat
java -Dsun.java2d.uiScale=1.0 -Dsun.java2d.dpiaware=false -jar void-client-1.2.0.jar -ip 2.28.141.196 -p 43594
```

If the client cannot connect, first confirm the Void container is running and
test TCP port `43594` from the client PC. Do not expose port `8080` unless the
web server is intentionally enabled.

Before Docker image creation, the cache must exist under `data/cache/`. Cache files are runtime assets and are not expected to be stored in Git.

## Build memory settings

This server has approximately 4 GB RAM and 4 GB swap. The repository uses conservative settings:

```properties
org.gradle.jvmargs=-Xmx1g -Xms256m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx3g -Xms512m
```

The Gradle wrapper uses a small launcher heap:

```text
DEFAULT_JVM_OPTS='"-Xmx512m" "-Xms128m"'
```

Do not restore the original 7 GB Gradle/Kotlin settings on this 4 GB server. If a compile fails, inspect active JVM processes before changing limits or starting another build. Stop stale compiler daemons only when no build is running.

Build command:

```bash
cd /opt/void
./gradlew --stop
./gradlew :game:build -x test --no-daemon
```

## First deployment

1. Clone `personal-tweaks` into `/opt/void`.
2. Create `/srv/void-cloud-rsps-data/saves` and `/srv/void-cloud-rsps-backups`.
3. Ensure the existing TOML saves are in the external host directory.
4. Upload the cache into `/opt/void/data/cache/`.
5. Verify `storage.type=files` and `storage.players.path=./data/saves/`.
6. Create and verify a dated saves backup.
7. Build the JAR successfully.
8. Build the local Docker image.
9. Start the Compose service.
10. Test login, account loading, save, logout, and relogin.

Do not start Docker until the external saves, cache, file-storage configuration, and successful JAR build have all been verified.

## Normal production update

Run this only after announcing maintenance:

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

After deployment, test an existing account and verify a save-changing action survives logout and relogin.

## Absolute data-safety rules

Never run these during normal maintenance:

- `git clean -fdx`
- `rm -rf /opt/void/data/saves`
- `rm -rf /srv/void-cloud-rsps-data`
- `docker compose down -v`
- A fresh checkout copied over `/srv/void-cloud-rsps-data`
- A database migration or storage-mode switch without a tested backup and import plan

Before any command that can delete, overwrite, reset, migrate, or replace runtime data:

1. State exactly what it affects.
2. Confirm whether it touches player saves.
3. Confirm whether it touches PostgreSQL or a Docker volume.
4. Verify an appropriate backup exists.
5. Obtain explicit approval before proceeding.

## Rollback

If a deployment fails, preserve the failed logs and save backup, stop the game container, return to a known-good code commit, rebuild, and restart. Do not restore saves merely because a code build failed. Restore runtime data only when it was actually damaged or made incompatible.

## Secrets and local-only files

- Never commit `.env`, passwords, SSH credentials, or private server notes.
- Never print the production database password.
- Keep deployment secrets outside Git.
- Local gameplay changes in `game/src/main/resources/game.properties` must be reviewed before syncing or committing.

## Detailed references

- `DOCS-PERSONAL/Hetzner-Cloud-Deployment.md`: first deployment and operations.
- `DOCS-PERSONAL/Production-Update-Safety.md`: save backup and persistence rules.
- `DOCS-PERSONAL/planned/Personal-Branch-Update-Plan.md`: upstream synchronization and release workflow.
- `DOCS-PERSONAL/build-run-cmd.md`: build command notes.

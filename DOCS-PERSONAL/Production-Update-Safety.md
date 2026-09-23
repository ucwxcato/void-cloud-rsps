# Production Update Safety

## Non-negotiable save rule

Player saves must never be part of the code-update operation. On the Hetzner server, keep `data/saves` in a persistent directory outside the Git checkout and mount that directory into the container with `VOID_SAVES_DIR`.

Example production setup:

```bash
sudo mkdir -p /srv/void-cloud-rsps-data/saves
export VOID_SAVES_DIR=/srv/void-cloud-rsps-data/saves
docker compose up -d
```

Use the same `VOID_SAVES_DIR` value every time Compose is run. The Compose file intentionally refuses to start if this variable is missing, so a production deployment cannot silently fall back to a directory inside the Git checkout.

For local development, explicitly point the variable at the repository directory before starting Compose:

```bash
export VOID_SAVES_DIR="$PWD/data/saves"
```

The Compose configuration also uses a named Docker volume called `void-db-data` for PostgreSQL. This volume must not be removed during normal updates.

## Required pre-update backup

Before every update, stop the game cleanly and create a dated backup of the external saves directory:

```bash
backup_dir=/srv/void-cloud-rsps-backups/$(date +%Y-%m-%d_%H-%M-%S)
mkdir -p "$backup_dir"
tar -C /srv/void-cloud-rsps-data -czf "$backup_dir/saves.tar.gz" saves
```

Also back up the PostgreSQL database before changes that may affect storage or migrations:

```bash
docker compose exec -T db pg_dump -U postgres game > "$backup_dir/game.sql"
```

Confirm that both backup files exist before continuing.

## Safe update sequence

Run the following from the server checkout after setting `VOID_SAVES_DIR`:

```bash
docker compose stop void
git status --short
git fetch upstream
git switch personal-tweaks
git merge --no-ff upstream/main
docker compose up -d --build
docker compose logs --tail=100 void
```

The Git update changes application code only. The external saves directory and `void-db-data` volume remain in place.

## Commands that are forbidden during normal updates

- `git clean -fdx` or any command that deletes ignored files.
- `rm -rf data/saves` or deletion of the external saves directory.
- `docker compose down -v`, because `-v` removes the PostgreSQL named volume.
- Recreating the server from a fresh checkout without restoring the external saves directory and database volume/backup.
- Starting Compose without `VOID_SAVES_DIR` set on production.

If a deployment process copies a new checkout to the server, copy only application files and keep `/srv/void-cloud-rsps-data` untouched.

## Recovery

If saves need to be restored, stop the game container first, preserve the current failed state, and extract the backup back into the external data directory. Restore the database dump only when the database state also needs to be rolled back.

This protects save persistence across branch merges, fresh application builds, container recreation, and server updates. It does not replace off-server backups; periodically copy the backup directory to separate storage.

# Personal Branch Update Plan

This guide updates production from upstream content while preserving the `personal-tweaks` branch and all live file-storage data.

## Branch roles

- `upstream/main`: GregHib's source of new content and fixes.
- Local `main`: clean upstream sync branch.
- `personal-tweaks`: personal code/configuration and production branch.
- `origin`: the personal GitHub fork.

Player data is not a Git branch. It lives outside the checkout at `/srv/void-cloud-rsps-data/saves/`.

The server uses:

```properties
storage.type=files
storage.players.path=./data/saves/
```

The Docker bind mount maps the external directory to `/app/data/saves/`.

## Before updating

1. Announce maintenance and stop accepting new logins.
2. Stop the game server cleanly.
3. Verify `/srv/void-cloud-rsps-data/saves/` exists.
4. Back up the external saves:

   ```bash
   backup_dir='/srv/void-cloud-rsps-backups/'$(date +%Y-%m-%d_%H-%M-%S)
   mkdir -p "$backup_dir"
   tar -C /srv/void-cloud-rsps-data -czf "$backup_dir/saves.tar.gz" saves
   test -s "$backup_dir/saves.tar.gz"
   ```

5. Review `git status --short --branch`.
6. Commit intended personal source changes before syncing. Never commit secrets, runtime saves, or `.env`.

## Sync upstream content

Fetch upstream without changing the working tree:

```bash
git fetch upstream
```

Update the clean local upstream branch:

```bash
git switch main
git merge --ff-only upstream/main
```

Bring the updated upstream code into the personal branch:

```bash
git switch personal-tweaks
git merge --no-ff main -m 'sync: update personal branch from upstream'
```

A merge is preferred for the shared personal branch. Never resolve conflicts by deleting or replacing the external save directory.

If conflicts occur:

```bash
git status
git diff --name-only --diff-filter=U
```

Read each conflict before resolving it. To cancel an unfinished merge:

```bash
git merge --abort
```

## Build and verify

Before deployment:

- Confirm `storage.type=files`.
- Confirm `storage.players.path=./data/saves/`.
- Confirm the Compose bind mount targets `/srv/void-cloud-rsps-data/saves`.
- Confirm cache files exist under `data/cache/`.
- Build and run relevant tests.
- Record `git rev-parse --short HEAD`.

## Deploy to Hetzner

Push the tested branch:

```bash
git push origin personal-tweaks
```

On Hetzner:

```bash
cd /opt/void
docker compose stop void
git fetch origin
git switch personal-tweaks
git pull --ff-only origin personal-tweaks
./gradlew :game:build -x test --no-daemon
docker compose build void
docker compose up -d
docker compose logs --tail=200 void
```

The external saves remain outside Git and must not be cleaned, reset, or overwritten.

## Rollback

If deployment fails:

1. Keep the pre-update saves backup.
2. Stop the game container.
3. Return to the previous known-good code commit.
4. Rebuild and restart.
5. Restore saves only if runtime data was actually damaged or made incompatible.
6. Preserve failed logs and backups before restoring anything.

## Checklist

- [ ] Maintenance announced
- [ ] Game stopped cleanly
- [ ] External saves backup verified
- [ ] Working tree reviewed
- [ ] `git fetch upstream` completed
- [ ] Local `main` fast-forwarded to `upstream/main`
- [ ] `personal-tweaks` merged from local `main`
- [ ] Storage remains `files`
- [ ] Cache files present
- [ ] Build passed
- [ ] Branch pushed to `origin`
- [ ] Production rebuilt
- [ ] Login/save/logout/relogin test passed
- [ ] Logs monitored

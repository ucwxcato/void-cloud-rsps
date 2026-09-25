# AGENTS.md

## Project and production host

This repository is the personal production branch for the Void RuneScape private server.

- Personal repository: `https://github.com/ucwxcato/void-cloud-rsps.git`
- Upstream repository: `https://github.com/GregHib/void.git`
- Production branch: `personal-tweaks`
- Production checkout/build workspace: `/opt/void`
- Current production host: Hetzner dedicated Ubuntu 24.04 at `95.216.71.232`
- Game endpoint: TCP `95.216.71.232:43594`
- PufferPanel 3 server ID: `a59b8fa2`, **Host** environment
- PufferPanel server root: `/srv/games/pufferpanel/servers/a59b8fa2/`
- Runtime JAR: `/srv/games/pufferpanel/servers/a59b8fa2/void-server.jar`

The former 4 GB Hetzner Cloud host at `2.28.141.196` is stopped and retained for rollback. Do not start, update, delete, or clean its old Compose deployment unless the user explicitly asks.

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

Do not resolve conflicts by deleting runtime data. Review storage code, configuration, and tracked data files manually.

## Production storage and PufferPanel Host isolation

Production uses file storage, not PostgreSQL:

```properties
storage.type=files
storage.players.path=./data/saves/
```

Authoritative live saves are a real directory at:

```text
/srv/games/pufferpanel/servers/a59b8fa2/data/saves/
```

Backups are stored outside the PufferPanel server directory at `/srv/void-cloud-rsps-backups/`. PufferPanel Host runs the process in an `unshare` filesystem view exposing the server root; an external symlink to `/srv/void-cloud-rsps-data/saves` is not visible and caused `FileNotFoundException`. Never replace `data/saves` with an external symlink. The old external migration copy and dated archive are retained separately for rollback.

Do not migrate player storage to PostgreSQL or delete a legacy `void-db-data` volume without a separately tested migration plan.

## Runtime and resources

- Java: OpenJDK 21 installed on the dedicated host.
- PufferPanel starts the JAR from the server root using the template `deploy/pufferpanel/void-rsps-host.json`.
- JVM settings: `-Xms1g -Xmx8g -XX:MaxMetaspaceSize=1g`; the 8 GB heap ceiling leaves RAM for other hosted games.
- Host mode has no Docker cgroup CPU/RAM limit and provides weaker isolation than Docker. Keep this tradeoff in mind; do not silently switch environments.
- Start and stop the RSPS through PufferPanel so its console and process state stay managed. The JVM shutdown hook saves state on graceful termination.
- Other game servers may use Docker independently; Docker Compose is not the production launcher for this RSPS.

## Discord XP logout summaries

- The game can post each real player's positive XP gains from the current session to the dedicated Discord XP channel on logout. Sessions with no XP gain produce no message; bots are excluded.
- Configure the dedicated XP channel webhook through PufferPanel's secret-backed `discordXpWebhook` variable (`DISCORD_XP_WEBHOOK_URL`) and the player login webhook through `discordLoginWebhook` (`DISCORD_LOGIN_WEBHOOK_URL`). Successful real-player logins go to the login webhook, skill XP gains at logout go to the XP webhook, and plain logout notices go to the status webhook (`discordWebhook` / `DISCORD_WEBHOOK_URL`). The status webhook remains an XP fallback. Do not put webhook URLs in tracked files, commit them, or print them in logs.
- Webhook delivery runs asynchronously and must not delay logout. A failed Discord request is logged without affecting player saves or server operation.
- The logout summary uses per-skill XP differences captured at login; it is informational and does not change player save data.

## Build and deployment

Build in `/opt/void`:

```bash
./gradlew --stop
./gradlew :game:build -x test --no-daemon
```

Expected artifact: `game/build/libs/void-server-dev.jar`. The cache is a runtime asset, ignored by Git, at `/opt/void/data/cache/`; production has a copied cache at `<PufferPanel server root>/data/cache/`.

For updates, announce maintenance, stop the PufferPanel server, create and verify a dated saves backup, build the JAR, replace only `void-server.jar`, restore its `pufferpanel:pufferpanel` ownership, and start/test from PufferPanel. Never replace `data/saves` while deploying code. Follow `DOCS-PERSONAL/Hetzner-Cloud-Deployment.md` and `DOCS-PERSONAL/Production-Update-Safety.md`.

## Client

The tracked client launcher is `client-hetzner/client.bat`; it must use the desktop JAR's command-line `-ip` option, not `-Dvoid.server`. The launcher now targets `95.216.71.232:43594`; rebuild the distributable ZIP after confirming the new server works.

## Absolute data-safety rules

Never during routine work:

- Run `git clean -fdx`.
- Delete, empty, or replace `/srv/games/pufferpanel/servers/a59b8fa2/data/saves/`.
- Restore saves while the game is running.
- Replace a fresh checkout over the PufferPanel server directory or its saves.
- Run `docker compose down -v` or remove any Docker volume.
- Delete `/srv/void-cloud-rsps-data` or the old-host data/backups during the rollback window.
- Change file storage to a database backend without a tested backup/import plan.

Before any command that can delete, overwrite, reset, restore, or migrate runtime data:

1. State exactly what it affects.
2. Confirm whether player saves or PostgreSQL/Docker volumes are involved.
3. Verify an appropriate backup exists.
4. Obtain explicit approval.

## Secrets and local-only files

- Never commit `.env`, passwords, SSH credentials, or private server notes.
- Never print or commit production passwords.
- Keep deployment secrets outside Git.
- Review personal gameplay changes in `game/src/main/resources/game.properties` before syncing or committing.

## Detailed references

- `TWEAKS-PERSONAL/`: one document per personal gameplay/code tweak.
- `DOCS-PERSONAL/Hetzner-Cloud-Deployment.md`: current PufferPanel deployment and operations.
- `DOCS-PERSONAL/Production-Update-Safety.md`: save backup and persistence rules.
- `TWEAKS-PERSONAL/discord-xp-logout-summary.md`: Discord XP logout summary behavior and configuration.
- `DOCS-PERSONAL/planned/Personal-Branch-Update-Plan.md`: upstream synchronization workflow.
- `DOCS-PERSONAL/build-run-cmd.md`: build and runtime commands.
- `deploy/pufferpanel/void-rsps-host.json`: importable Host template.

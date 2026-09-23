# Personal Branch Update Plan

This guide describes how to bring new content from `main` into the personal `personal-tweaks` branch while keeping personal changes safe.

## Branch roles

- `main`: upstream server content and shared changes.
- `personal-tweaks`: personal configuration, gameplay changes, and server-specific tweaks.
- `origin`: the remote Git repository.

Keep production-specific secrets, credentials, world data, logs, and runtime files outside Git whenever possible.

## Before updating

1. Announce maintenance to players and stop accepting new logins.
2. Stop the server cleanly.
3. Back up the database, world/save data, configuration files, and the current server build.
4. Confirm the working tree is clean:

   ```powershell
   git status
   ```

5. If there are unfinished local changes, commit them before updating:

   ```powershell
   git add .
   git commit -m "Save personal server tweaks before upstream update"
   ```

## Update the personal branch

Fetch the latest remote branches:

```powershell
git fetch origin
```

Update `personal-tweaks` with the latest `main` changes. Rebasing keeps the branch history easy to follow:

```powershell
git switch personal-tweaks
git rebase origin/main
```

If conflicts occur:

```powershell
git status
# Edit each conflicted file and keep the correct combination of upstream and personal changes
git add <resolved-file>
git rebase --continue
```

To cancel the update without losing the branch state from before the rebase:

```powershell
git rebase --abort
```

Do not use `git push --force` casually. If the rebased branch is already shared remotely, use the team-approved force-push procedure or merge `origin/main` instead.

## Build and test before deployment

1. Review the changes introduced by upstream.
2. Run the repository's build and test commands.
3. Confirm configuration files still point to the correct production database, ports, paths, and Java/runtime version.
4. Check migrations, plugin changes, item/NPC data changes, and save-format changes for compatibility.
5. Test login, character loading, combat, trading, banking, persistence, and any personal features on a staging or local copy when possible.

## Deploy to the Hetzner server

1. Create a deployment backup and record the Git commit being deployed:

   ```powershell
   git rev-parse --short HEAD
   ```

2. Upload or pull the tested build on the server.
3. Replace only the intended application files; preserve production data and secrets.
4. Start the server using the normal service/process command.
5. Watch startup logs for errors and verify a real client can connect.
6. Perform a short smoke test before announcing that maintenance is complete.

## Rollback plan

If the update causes problems:

1. Stop the server.
2. Restore the previous application build and configuration backup.
3. Restore database/world data only if the new version changed data incompatibly; make a copy of the failed state first.
4. Restart the server and verify player access.
5. Record the failed commit, error logs, and symptoms before attempting another update.

For a Git-only rollback locally, identify the last known-good commit and create a recovery branch before making further changes:

```powershell
git switch personal-tweaks
git branch recovery-before-rollback
git reset --hard <known-good-commit>
```

Only reset a branch after confirming that all required personal changes are committed and backed up.

## Recommended update checklist

- [ ] Maintenance announced
- [ ] Server stopped cleanly
- [ ] Database/world/configuration/build backups completed
- [ ] Personal changes committed
- [ ] `git fetch origin` completed
- [ ] `personal-tweaks` rebased onto `origin/main`
- [ ] Conflicts reviewed and resolved
- [ ] Build and tests passed
- [ ] Production configuration checked
- [ ] Tested commit recorded
- [ ] Server deployed and smoke-tested
- [ ] Logs monitored after startup
- [ ] Players notified

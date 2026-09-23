# Global and Proximity Chat

> **Status:** Planned; implementation has not started.
>
> **Related development plan:** [`DOCS-PERSONAL/planned/global-proximity-chat-plan.md`](../DOCS-PERSONAL/planned/global-proximity-chat-plan.md)

## Purpose

Add two clearly labeled public-chat modes:

```text
[PROXIMITY] Alice: Hello nearby players
[GLOBAL] Alice: Welcome everyone
```

Normal public chat remains limited to nearby players. `/s ` sends a temporary
server-wide message to eligible online players.

## Proposed behavior

- Normal text uses the existing proximity radius.
- `/s ` at the beginning of a message selects global chat.
- `/s` with no message does not broadcast anything.
- `/server` and other text that does not begin with `/s ` remain normal chat.
- Muted players cannot use either channel.
- Global chat has a server-side cooldown and message-length limit.
- Recipient ignore lists should be respected unless a later decision changes
  that policy.
- Messages are transient and are not saved to player files.
- Clan chat behavior remains unchanged.

## Implementation boundary

Void’s content system supports Kotlin classes implementing `Script`, with
handlers registered in `init` and discovered automatically during startup.
This tweak should use that content/script layer rather than changing the
network protocol or client JAR.

This cannot be TOML-only because the server must inspect and route the existing
`ChatPublic` instruction. The current handler is in:

```text
game/src/main/kotlin/content/social/chat/Chat.kt
```

Keep the upstream integration small and predictable:

```text
Chat.kt                  ← minimal delegation/integration seam
GlobalChat.kt            ← global routing and validation
ProximityChat.kt         ← optional proximity formatting helper
```

Do not register a second competing `ChatPublic` handler and depend on script
load order. Either extend the existing handler minimally or make it delegate to
one dedicated router.

## Upstream-update strategy

The goal is to keep nearly all custom logic outside files likely to be changed
by GregHib upstream.

1. Keep upstream synchronization on the clean local `main` branch.
2. Keep this tweak on `personal-tweaks`.
3. Keep the implementation in dedicated files under
   `game/src/main/kotlin/content/social/chat/`.
4. Keep the change to upstream-owned `Chat.kt` as small as possible.
5. Keep the feature implementation, tests, and this document in separate
   commits where practical.
6. Enable Git’s remembered conflict resolution once:

   ```bash
   git config rerere.enabled true
   ```

7. Sync upstream using `DOCS-PERSONAL/syncing-upstream.md`.
8. If `Chat.kt` conflicts, preserve upstream behavior and reinsert the small
   delegation seam. Do not blindly choose ours or theirs.
9. Run focused chat tests and compile before deploying.

Typical sync shape:

```bash
git status --short --branch
git fetch upstream
git switch main
git merge --ff-only upstream/main
git switch personal-tweaks
git merge main
./gradlew :game:test --tests 'content.social.chat.*'
./gradlew :game:build -x test --no-daemon
git push origin personal-tweaks
```

If upstream moves or renames chat handling, stop and inspect the new
implementation before resolving the conflict. The dedicated tweak files are
not a reason to overwrite upstream chat changes.

## Required tests

- [ ] Normal text reaches nearby eligible players only.
- [ ] Normal text is labeled `[PROXIMITY]`.
- [ ] `/s message` reaches eligible players outside the proximity radius.
- [ ] Global text is labeled `[GLOBAL]`.
- [ ] `/s` and `/s   ` are rejected without broadcasting.
- [ ] `/server` remains normal proximity chat.
- [ ] Muted players cannot send either channel.
- [ ] Ignore behavior is preserved.
- [ ] Clan chat still works normally.
- [ ] Cooldown and maximum length are enforced server-side.
- [ ] A disconnecting recipient does not prevent delivery to others.
- [ ] Player saves remain unchanged after testing and container restart.

## Deployment safety

This tweak does not require migration of player data. Before deploying it:

1. Stop the game container according to the production control guide.
2. Back up `/srv/void-cloud-rsps-data/saves/`.
3. Pull and build `personal-tweaks`.
4. Build the local Docker image.
5. Restart the service and inspect logs.
6. Test with two accounts using the tracked Windows client package.

Never use `git clean -fdx`, delete the external saves directory, or run
`docker compose down -v` as part of this tweak.


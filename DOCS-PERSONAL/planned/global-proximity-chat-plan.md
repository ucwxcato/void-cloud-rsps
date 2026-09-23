# Global and Proximity Chat — Development Plan

> **Status:** Planned. The existing chat architecture has been inspected; no
> feature code has been changed and no gameplay behavior is verified yet.
>
> **Purpose:** Let players use normal chat for nearby players and `/s message`
> for a server-wide message with clear `[PROXIMITY]` or `[GLOBAL]` labels.
>
> **Authority:** This document owns the feature behavior, implementation order,
> safety rules, and verification requirements. It does not change the existing
> clan-chat contract.
>
> **Target:** Void server on the `personal-tweaks` branch, deployed through the
> existing local Docker image and Windows client setup.

## 0. Outcome

Players type normally to speak to nearby players:

```text
[PROXIMITY] Alice: Hello nearby players
```

Players type `/s message` to speak to everyone online:

```text
[GLOBAL] Alice: Welcome to the server!
```

Complete when:

```text
player types normal text -> nearby players see [PROXIMITY] text
player types /s text -> every eligible online player sees [GLOBAL] text
mute/rate-limit/invalid input -> no broadcast and clear feedback
restart/deploy -> no player save data is changed
```

## 1. Verified repository facts

- [x] `game/src/main/kotlin/content/social/chat/Chat.kt` currently handles
  `ChatPublic` and `ChatTypeChange`.
- [x] Normal public chat currently broadcasts through `Client.publicChat()` to
  players within `Viewport.Companion.VIEW_RADIUS`.
- [x] Existing public chat respects ignored players and muted-player checks.
- [x] Existing clan chat uses the same `ChatPublic` instruction when the
  player's chat type is `clan`.
- [x] `ChatHistory` already records recent messages for abuse-report evidence.
- [x] `ContentLoader` loads Kotlin classes implementing `Script` from generated
  `scripts.txt`.
- [x] The server already has a server-to-client `Client.message()` path for
  ordinary chat-box messages.
- [x] Player saves are external to the checkout at
  `/srv/void-cloud-rsps-data/saves/`; this feature must not alter that design.

## 1.1 Script feasibility research

The official Void documentation describes content as a mix of TOML data files
and lightweight Kotlin scripts. It defines a script as a Kotlin class in the
game module implementing `Script`; handlers registered in `init` are loaded
when the server starts. See:

- [Void Content Creation](https://greghib.github.io/void/docs/content-creation.html)
- [Void Scripts](https://greghib.github.io/void/docs/scripts.html)
- [Void Event Handlers](https://greghib.github.io/void/docs/event-handlers.html)
- [Void Players](https://greghib.github.io/void/docs/players.html)

**Conclusion:** the feature is suitable for a Void content-level Kotlin
script, and does not require an engine, network protocol, or client-JAR
change. It is not configuration-only: `/s` must inspect the executable
`ChatPublic` instruction, and the current handler is owned by
`game/src/main/kotlin/content/social/chat/Chat.kt`. The implementation should
therefore either make a small change in that script or extract its handler into
one dedicated chat script. Do not register a second competing `ChatPublic`
handler and depend on generated script load order.

## 2. Locked decisions

- `/s` means global only when it is followed by whitespace and non-empty text.
  This avoids treating words such as `/server` as global chat.
- Global chat is server-side and does not require a client JAR change. The
  existing JAR can display a normal server message with a `[GLOBAL]` prefix.
- Normal public chat remains proximity chat. Its visible text receives a
  `[PROXIMITY]` prefix.
- Clan chat remains separate. A message sent while the client is in clan-chat
  mode continues to use the existing clan rules unless the final implementation
  explicitly decides that `/s` is a global escape command in every mode.
- Muted players cannot send either chat mode.
- Global chat must have a server-side cooldown and length limit. Exact values
  remain tuning points until live testing.
- Global broadcasts must not write player data, modify inventory, award items,
  or depend on PostgreSQL.

## 3. Goals and non-goals

### Goals

- [ ] Add global chat using `/s `.
- [ ] Label normal public messages as `[PROXIMITY]`.
- [ ] Label global messages as `[GLOBAL]`.
- [ ] Preserve mute checks, ignore behavior, abuse-report history, and clan chat.
- [ ] Keep the implementation server-side and compatible with the current
  `client-hetzner` package.
- [ ] Add automated tests for routing, formatting, invalid input, and limits.

### Explicitly out of scope

- [ ] A new client interface, custom chat tabs, or client-JAR rebuild.
- [ ] Persistent chat history or cross-restart chat replay.
- [ ] Private-message or clan-chat redesign.
- [ ] Discord integration, web chat, or cross-world federation.
- [ ] Chat-related items, ranks, subscriptions, or monetization.

## 4. Proposed implementation boundary

### Preferred approach: Void content script

This feature should first be implemented as a content-level Kotlin `Script`,
not by changing the engine or network protocol. A likely implementation is a
focused chat script under:

```text
game/src/main/kotlin/content/social/chat/
```

However, this is still compiled source code. Void's documented script system
means a Kotlin content class implementing `Script`; it is not a runtime TOML or
text configuration hook. A data-only implementation is not currently possible
because `ChatPublic` must be intercepted and routed by executable logic.

### Existing handler integration

The current `Chat.kt` already registers the `ChatPublic` instruction handler.
The implementation must not create two competing handlers and rely on load
order. Use one of these deliberate designs:

- [ ] Smallest change: extend the existing `Chat.kt` public-chat handler with a
  private routing/helper function.
- [ ] Cleaner modular change: move public-chat routing into a dedicated
  `PublicChat` content script/helper and make `Chat.kt` delegate to it.
- [ ] Do not change engine instruction dispatch unless inspection proves the
  current handler registration cannot be safely replaced.

### Proposed routing pipeline

1. Receive `ChatPublic`.
2. Reject muted players using the existing mute behavior.
3. Normalize and validate the text without changing the original message more
   than the current capitalization behavior already does.
4. Detect `/s ` at the beginning and remove only that prefix.
5. Route global text to all eligible online players.
6. Route ordinary public text to nearby eligible players.
7. Record the final message type in `ChatHistory`.
8. Send clear feedback to the sender for empty, blocked, or rate-limited input.

## 5. User experience and behavior contract

| Input | Route | Visible format | Notes |
|---|---|---|---|
| `Hello` | Nearby players | `[PROXIMITY] Alice: Hello` | Existing proximity radius |
| `/s Hello` | All eligible players | `[GLOBAL] Alice: Hello` | Prefix is removed from visible text |
| `/s` | None | Usage/error feedback | Must not broadcast |
| `/server` | Proximity | `[PROXIMITY] Alice: /server` | Not a global command |
| Clan-mode normal text | Existing clan route | Existing clan format | Preserve current behavior |

The exact client packet presentation must be confirmed during the spike. The
preferred global path is a server chat-box message carrying the sender name and
`[GLOBAL]` text, because a normal `publicChat` packet uses a local player index
and may not render correctly for players outside the sender's viewport.

## 6. Data, lifecycle, and failure handling

- Global messages are transient and exist only in memory while players are
  online.
- No new player variable or save-file field is required for the MVP.
- Rate-limit state should be keyed by the live `Player`/account identity and
  cleared when the player despawns or disconnects.
- A failed recipient send must not abort delivery to other recipients.
- A disconnected recipient is skipped safely.
- A server restart loses chat history by design but does not affect saves.
- Deployments must continue to stop the game container and back up external
  saves before replacing the image, as documented in
  `DOCS-PERSONAL/Production-Update-Safety.md`.

## 7. Configuration, permissions, and integrations

Proposed settings should be added only if the existing settings system supports
them cleanly; otherwise keep the first version constants in the chat script and
document them. Candidate settings:

```properties
chat.global.enabled=true
chat.global.cooldown.seconds=3
chat.global.max.length=80
chat.global.minimum.rights=PLAYER
```

The default should allow ordinary players to use global chat, subject to mute,
cooldown, and validation. Staff bypasses are optional and must not silently
bypass mutes or abuse safeguards without an explicit decision.

Potential implementation helpers:

- `Players` for recipient enumeration.
- `Player.ignores()` for ignore filtering.
- `Player.message()` and `ChatType.Chat` for the global chat-box delivery.
- `ChatHistory.add()` for moderation evidence.
- Existing `isMuted` and `sendMuteMessage` behavior.
- Existing `VIEW_RADIUS` and dungeon-member special handling for proximity.

## 8. Safety, moderation, and abuse controls

- [ ] Preserve the existing mute check before any routing.
- [ ] Apply a maximum message length before broadcasting.
- [ ] Reject empty `/s` messages without sending an empty line.
- [ ] Add a global-chat cooldown to prevent flooding the entire server.
- [ ] Decide whether global chat respects recipient ignore lists; preferred MVP
  behavior is yes.
- [ ] Record `global` and `proximity` as distinct `ChatHistory` types.
- [ ] Ensure tags cannot be injected twice by routing helpers.
- [ ] Avoid logging passwords, save contents, or secrets.
- [ ] Confirm that a global broadcast cannot trigger gameplay actions or command
  execution merely because its text begins with `/`.

## 9. Verification matrix

| Scenario | Expected result | Evidence required |
|---|---|---|
| Two players nearby; Alice types normal text | Both receive `[PROXIMITY]` | Automated test and live client check |
| Two players far apart; Alice types normal text | Far player receives nothing | Automated routing test |
| Two players far apart; Alice types `/s hello` | Both receive `[GLOBAL] Alice: hello` | Automated test and live client check |
| Alice types `/s` or `/s   ` | No broadcast; usage feedback | Unit test |
| Alice types `/server` | It remains proximity text | Unit test |
| Muted Alice attempts either mode | Existing mute message; no recipients receive text | Existing mute tests plus new tests |
| Recipient ignores Alice | Recipient receives no eligible chat, according to locked policy | Test |
| Global cooldown is exceeded | Later message is rejected or delayed with feedback | Test |
| Clan chat remains active | Existing clan behavior is unchanged | Existing clan tests and regression test |
| Recipient disconnects during broadcast | Other recipients still receive the message | Failure-path test or controlled runtime check |
| Server restart after chat activity | Chat is gone; player saves remain intact | Runtime smoke test and save backup verification |
| Windows client package | Existing `client-hetzner` client connects without changes | Live client test |

## 10. Phased checkboxed delivery plan

### Phase 0 — Design lock and protocol spike

- [ ] Confirm whether `[GLOBAL]` should appear in chat-box text only or also as
  overhead text.
- [ ] Confirm whether `/s` works while the client is in public mode only, or is
  an escape from clan mode too.
- [ ] Confirm global ignore-list behavior.
- [ ] Inspect the live client presentation of `Client.message(..., ChatType.Chat,
  name=...)` with a temporary non-persistent test message.
- [ ] Lock cooldown and maximum-length values.
- [ ] **Verify:** Write the final decisions into this plan before coding.

### Phase 1 — Content-script MVP

- [ ] Implement routing in the existing chat script boundary or one dedicated
  content script without changing engine/network protocol code.
- [ ] If a new script class is created, confirm it is under the configured
  `content` package so Void's automatic script discovery includes it.
- [ ] If generated script metadata becomes stale, run the documented metadata
  regeneration task before rebuilding; do not manually delete unrelated build
  or runtime data.
- [ ] Add exact `/s ` detection and prefix stripping.
- [ ] Add `[PROXIMITY]` and `[GLOBAL]` formatting.
- [ ] Preserve dungeon proximity behavior and clan routing.
- [ ] Add mute, length, empty-message, ignore, and cooldown handling.
- [ ] Record both message types in `ChatHistory`.
- [ ] **Verify:** Run focused chat tests and compile `:game:build -x test`.

### Phase 2 — Automated regression coverage

- [ ] Add tests under `game/src/test/kotlin/content/social/chat/` for routing and
  formatting.
- [ ] Test global recipients outside `VIEW_RADIUS`.
- [ ] Test no duplicate sends and no accidental clan-chat regression.
- [ ] Test disconnect-safe recipient iteration.
- [ ] **Verify:** Focused tests pass, then the relevant full test task passes.

### Phase 3 — Staging and live smoke test

- [ ] Build the local Docker image from the successful JAR.
- [ ] Confirm the external saves directory and latest dated backup before
  restarting production.
- [ ] Deploy using the existing safe update procedure.
- [ ] Connect using the tracked `client-hetzner/client.bat` or the shared ZIP.
- [ ] Test proximity chat with two accounts.
- [ ] Test global chat with two accounts in different regions.
- [ ] Test mute, ignore, cooldown, and clan chat behavior.
- [ ] **Verify:** Save a small player change, restart only the container, and
  confirm the change persists.

### Phase 4 — Optional polish

- [ ] Add configurable colors using existing client color-tag support if the
  base tags are readable in the chat box.
- [ ] Add a staff-only global announcement command if genuinely needed.
- [ ] Add `/g` as an optional alias only after `/s` is stable.
- [ ] Add an opt-out or chat-filter integration if the client supports it.
- [ ] **Verify:** Each addition has its own test and does not weaken cooldown,
  mute, ignore, or save-safety behavior.

## 11. Open tuning points

- Should global messages appear as overhead text, chat-box text, or both?
- Should clan-mode `/s message` always escape to global chat?
- What are the final cooldown and message-length values?
- Should staff have a separate announcement channel rather than a bypass?
- Should global chat be disabled in specific areas or activities?

# Syncing Upstream (GregHib/void) Without Losing Local Tweaks

This repo (`alexfemcat/void-rsps-server-linux`) is a fork of
[`GregHib/void`](https://github.com/GregHib/void). This doc tells coding
assistants exactly how to pull new content/fixes from upstream **without**
overwriting the user's custom code, characters, or game data.

If you're an assistant reading this: read it fully before running `git pull`,
`git merge`, or `git rebase`. The user has things they care about.

---

## 0. Quick Reference (TL;DR for Assistants)

```bash
# One-time setup (skip if already done)
git remote add upstream https://github.com/GregHib/void.git
git fetch upstream
git branch --set-upstream-to=upstream/main main   # local main mirrors Greg

# Each sync — assume user is on `main`
git status                                # MUST be clean; if not, stash/commit first
git fetch upstream
git checkout main
git merge upstream/main                   # merge, NOT rebase — see §3
# ...resolve conflicts using §4...
./gradlew :game:build -x test             # verify it still compiles
git checkout custom/tweaks                # bring your tweaks branch up to date
git merge main
git push                                  # pushes custom/tweaks to origin
```

**Never** run `git push --force` on a shared branch. **Never** `git rebase`
onto upstream without explicit user approval. **Never** run `git push origin
main` — `main` is a local sync mirror of `upstream/main`; only your tweaks
branch (`custom/tweaks`) goes to `origin`.

---

## 1. The Three Remotes / Branches You Must Understand

| Remote        | URL                                                  | Role                                  |
| ------------- | ---------------------------------------------------- | ------------------------------------- |
| `origin`      | `github.com/alexfemcat/new-void.git`                 | The user's fork. Their canonical copy. |
| `upstream`    | `github.com/GregHib/void.git`                        | Greg's original. Source of new features. |
| (local) `main`| tracked branch on `upstream`                         | Clean mirror of Greg — never pushed to origin. |
| (local) `custom/tweaks` | tracks `origin/custom/tweaks`              | User's custom changes — play from here, push here. |

Verify with:

```bash
git remote -v                          # both remotes should appear
git branch -vv                         # confirm which remote `main` tracks
```

If `upstream` is missing, **ask the user before adding it** — they may want a
specific branch (e.g. `upstream/master`) rather than `main`.

The fork's `origin/main` is a placeholder with unrelated history (just bootstrap
commits) and is **not** what `main` should track. If `git branch -vv` shows
`main` tracking `origin/main`, fix it with:

```bash
git branch --set-upstream-to=upstream/main main
```

---

## 2. What Counts as "Local Tweak" vs "Upstream"

Before merging, you need to know what's *theirs* (user) and what's *ours*
(Greg). Run:

```bash
git log --oneline origin/main ^upstream/main | head -30   # user-only commits
git log --oneline upstream/main ^origin/main | head -30   # upstream-only commits
```

**User-owned surfaces** (high blast radius — preserve unless told otherwise):

- Anything under `data/saves/` that holds live player state:
  `kitten.toml`, `matthew.toml`, `peepeepoopoo.toml`, `test1.toml`,
  `grand_exchange/offers.toml`, `grand_exchange/claimable_offers.toml`.
- Anything under `data/saves/logs/`.
- Local config tweaks under `config/` (often `*.properties`,
  `gradle.properties`).
- Commit messages starting with `custom:`, `local:`, or `tweak:` — these are
  the user's. Don't rebase them away.

**Upstream-owned surfaces** (safe to take wholesale — they don't exist in the
fork yet):

- New game content (NPCs, items, quests, areas) — usually under `data/`
  subfolders that don't exist locally yet.
- Build / Gradle changes — Greg refactors these often.
- Bug fixes in `engine/` and `network/` — usually pure refactors.

**Both touch** (will conflict — handle carefully, §4):

- `data/achievement/`, `data/skill/`, `data/entity/` — Greg often touches the
  same definition files the user customizes.
- Save-format code (anything reading/writing `*.toml` saves) — schema changes
  here can silently invalidate local characters.

---

## 3. Merge vs Rebase — Why We Merge

Default to **merge** for syncing upstream:

```bash
git merge --no-ff upstream/main -m "Sync upstream GregHib/void <date>"
```

Why not rebase:

- Rebasing rewrites the user's commit hashes. Anything pointing at them
  (PRs, backups, tags, other clones) breaks.
- If a rebase goes wrong mid-conflict, recovery is harder.
- The user wants a clear, honest history: "this commit is mine, this commit
  is Greg's."

When rebase *is* okay: on a **throwaway local feature branch** (e.g.
`feature/my-thing`) that hasn't been pushed and isn't shared. Even then,
prefer merge unless the user explicitly asks.

---

## 4. Conflict Resolution Workflow

When `git merge upstream/main` produces conflicts, follow this order:

### 4.1 Triage

```bash
git status                              # list conflicted files
git diff --name-only --diff-filter=U    # same, narrower
```

Group the conflicted files:

1. **User-only files** (likely never seen upstream changes): usually auto-resolve
   in favor of "ours" — but verify with `git log` first.
2. **Upstream-only files** (likely never seen user changes): usually safe to
   take "theirs".
3. **Both-touched files**: requires reading both sides (§4.2).

### 4.2 Read both sides before resolving

For each conflicted file:

```bash
git log --oneline -- path/to/file        # who touched it, when
git show <commit> -- path/to/file        # what they actually changed
```

Don't blindly pick `--ours` or `--theirs`. Examples of judgment calls:

- Upstream added a new NPC in `data/entity/npcs.toml` that the user also
  edited → keep the user's version of existing entries, **append** the new
  ones.
- Upstream renamed a package path used by a save loader → keep upstream's
  rename, but **audit** every save reader/writer the user has touched.
- Gradle bumped a dependency → take upstream's version.

### 4.3 Resolving

For files where the user clearly owns the content:

```bash
git checkout --ours path/to/file
```

For files where upstream clearly owns the content:

```bash
git checkout --theirs path/to/file
```

For files needing manual merge: open them, find the `<<<<<<<`, `=======`,
`>>>>>>>` markers, edit to the desired state, then `git add` the file.

For bulk "we want everything from upstream, no exceptions":

```bash
git checkout --theirs .
git add -A
```

**This last command is destructive** — only use when the user has confirmed
they want a hard upstream override (rare; usually only for a one-off
bootstrap when adopting a divergent branch).

### 4.4 Verify after merge

```bash
./gradlew :game:build -x test    # must compile
./gradlew test                   # run tests
git diff --stat HEAD~1 HEAD      # eyeball the change footprint
```

If anything blows up, abort cleanly:

```bash
git merge --abort                # ONLY if nothing is staged yet
```

---

## 5. Protecting Characters and Game Data

This is the part the user cares about most. Several layers of defense:

### 5.1 Know what's already gitignored

Check `.gitignore` for the safety nets already in place:

- `data/cache/*` (cache files, `.idx`/`.dat`/`.dylib`/`.dll`)
- `data/logs/error.log`
- `data/saves/grand_exchange/price_history`
- `*.jar`, `*.class`, `build/`, `.gradle/`

**Good news:** cache and build artifacts are not tracked, so upstream changes
won't touch them.

### 5.2 Know what's still tracked (and at risk)

These are committed and **will** be affected by a sync:

- `data/saves/grand_exchange/offers.toml`
- `data/saves/grand_exchange/claimable_offers.toml`
- Individual character saves: `kitten.toml`, `matthew.toml`,
  `peepeepoopoo.toml`, `test1.toml`
- `data/saves/logs/`

**Before every sync, back these up outside git:**

```bash
# Linux / Git Bash
tar czf ~/backups/void-saves-$(date +%F).tar.gz data/saves/
```

Or simply copy `data/saves/` somewhere safe. If upstream changes the save
schema and an in-game load fails, you restore from this backup.

### 5.3 Recommended: keep saves out of git

If the user wants bulletproof safety, suggest moving runtime saves out of the
working tree entirely (e.g. to a sibling `runtime-data/` directory not under
git), and configuring the server to read/write there. This way:

- Syncing upstream can never collide with player state.
- A botched merge can't wipe a character.
- Save files don't bloat the repo.

If the user wants to keep them tracked (e.g. for backup across machines),
recommend `git-lfs` for the save files, or at minimum a pre-sync
`git diff data/saves/` sanity check.

### 5.4 Database

If the server uses a SQL database (MySQL, etc. — see `docker-compose.yml`
and `database/` directory), back up the dump before syncing:

```bash
mysqldump -u root -p void > ~/backups/void-db-$(date +%F).sql
```

Schema changes in upstream migrations can orphan rows; the dump is the only
rollback.

---

## 6. Recommended Branching Model

The simplest setup that doesn't get in the way:

```
upstream/main  ──►  local main  ──►  custom/tweaks  ──►  origin/custom/tweaks
(Greg)              (clean mirror)   (your tweaks)       (your fork)
```

`main` is a *sync target* — it only exists to fast-forward from `upstream/main`.
Your customizations live on `custom/tweaks` (or a similarly-named branch). You
play the game from `custom/tweaks` and push it to `origin`. **Do not push
`main` to `origin`** — the fork's main branch holds placeholder bootstrap
commits with unrelated history, and `main` is meant to mirror `upstream`
cleanly.

For heavier customization, consider splitting tweaks into multiple branches
and merging them into `custom/tweaks` before each play session or push:

```
main
├── custom/server-tweaks           ← game.properties, settings
├── custom/docs                    ← your local documentation
├── custom/feature-foo             ← a specific feature
└── sync/upstream-YYYY-MM-DD       ← one branch per sync attempt (deleted after merge)
```

The `sync/upstream-*` pattern gives you a place to resolve messy conflicts
without polluting `main`, and the dated branch names make rollbacks obvious:

```bash
git checkout -b sync/upstream-$(date +%F) main
git merge upstream/main
# resolve...
git checkout main
git merge --no-ff sync/upstream-$(date +%F)
git branch -d sync/upstream-$(date +%F)
```

---

## 7. Commit Message Conventions

Encourage the user (and yourself) to prefix commits so the sync boundary is
visible in `git log`:

| Prefix        | Meaning                                  |
| ------------- | ---------------------------------------- |
| `custom:`     | User's own tweak/feature                 |
| `local:`      | Local config or data fix                 |
| `sync:`       | A merge commit pulling in upstream       |
| `fix:`        | Bug fix (matches Greg's convention)      |
| `feat:`       | New feature (matches Greg's convention)  |

Example:

```bash
git commit -m "custom: add admin command ::givex for testing"
git commit -m "sync: pull GregHib/void through 2fae2ea"
```

---

## 8. Pre-Sync Checklist (for the Assistant)

Before running any sync command, confirm:

- [ ] Working tree is clean (`git status`).
- [ ] On `main` (or whatever branch the user named).
- [ ] `data/saves/` is backed up outside the repo.
- [ ] Database dump is taken (if applicable).
- [ ] User has confirmed they want a sync right now.
- [ ] You know which files are user-owned vs upstream-owned (§2).

If any box is unchecked, **stop and ask**.

## 9. Post-Sync Checklist

After a successful sync:

- [ ] `./gradlew :game:build -x test` passes.
- [ ] `./gradlew test` passes (or known-failing tests documented).
- [ ] `git log --oneline custom/tweaks ^upstream/main` still shows your tweaks.
- [ ] Spot-check 1-2 character saves load in-game before declaring victory.
- [ ] `git push` from `custom/tweaks` succeeds (no need to push `main`).
- [ ] User is told what was merged and what (if anything) was rejected.

---

## 10. When Things Go Wrong

| Symptom                                          | Likely cause                            | Fix                                                                          |
| ------------------------------------------------ | --------------------------------------- | ---------------------------------------------------------------------------- |
| `data/saves/X.toml` won't load in-game           | Upstream changed save schema            | Restore from backup; migrate manually or revert the offending upstream commit. |
| Build passes but NPC/item IDs collide            | Upstream added an ID the user also used | Edit one side to a new ID; document in commit message.                      |
| `main` doesn't fast-forward when running `git merge upstream/main` | Local `main` tracks `origin` (default after fork clone) instead of `upstream` | `git branch --set-upstream-to=upstream/main main` (also the expected one-time setup after a fresh fork) |
| Rebase went sideways                             | Mid-rebase conflict pile-up             | `git rebase --abort`, switch to merge workflow (§3).                         |
| Lost local commits after force push              | Someone force-pushed                    | Recover via `git reflog` if recent; otherwise the commits are gone — this is why we don't force-push. |

---

## 11. What the Assistant Should *Not* Do

- **Don't** run `git push --force` to `origin/main`.
- **Don't** push `main` to `origin` at all — `main` is a local sync mirror of
  `upstream/main`. Only your tweaks branch (`custom/tweaks` or similar) is
  pushed to `origin`. The fork's `main` is placeholder bootstrap with unrelated
  history; force-pushing or merge-pushing local `main` there will desync the
  fork.
- **Don't** rebase commits that have been pushed.
- **Don't** delete `data/saves/` files to "resolve" a conflict — those are
  player characters.
- **Don't** auto-resolve conflicts in `data/saves/`, `config/`,
  `gradle.properties`, or `docker-compose.yml` without reading them.
- **Don't** merge upstream without the user's explicit go-ahead.
- **Don't** assume the user's `main` is the same as Greg's `main` — check
  first (`git rev-parse upstream/main` vs `git rev-parse origin/main`).

---

## 12. One-Time Bootstrap (Only If the Fork Has Drifted Hard)

If `origin/main` and `upstream/main` have diverged so far that a normal
merge produces a wall of conflicts, do this with the user watching:

```bash
git checkout -b recovery/upstream-realignment main
git fetch upstream
git merge -s ours upstream/main     # take upstream's tree, keep our history
# Now checkout each *file* the user cares about from main:
git checkout main -- path/to/user/file1 path/to/user/file2 ...
git add -A
git commit -m "sync: realign fork with upstream, preserving user files"
```

Verify, then fast-forward `main`:

```bash
git checkout main
git merge --ff-only recovery/upstream-realignment
```

**This is destructive to the file tree** — only do it when a normal merge
isn't viable, and only with the user's explicit go-ahead.

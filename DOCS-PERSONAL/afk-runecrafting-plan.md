# Ourania Rotunda — AFK Runecrafting Minigame (Design Plan)

A second runecrafting training method for the channel rune players who don't
want to altar-hop, but who also don't want to sit at one altar and click.
This is **AFK with a heartbeat** — passive binding works, but the zone has
three interlocking mechanics that reward *both* idle and active play.

This plan builds on top of the existing `Runecrafting.kt` (`bindRunes`)
rather than replacing it, and uses the existing `Timers`, `Clock`, and
optional-instance surfaces the engine already provides.

---

## 0. TL;DR

- New zone: **Ourania Rotunda**, accessed via a new NPC outside the existing
  Astral altar chain.
- Six altar chambers sit in a hex ring around a central **Ourania glyph**.
- **Rune Surge** (a server-wide `Clock`) cycles the "charged" altar every 5
  minutes — binding at the charged altar grants **2× XP**; adjacent altars
  grant **1.25× XP**.
- A personal **Conduit Meter** (`Timer` per player) charges as the player
  binds. At full charge, the meter triggers a 90-second **Cataclysmic
  Window** of **3× XP** at any altar, then resets to a residual 20%.
- A small chance per binding produces **Essence Corruption** — the essence
  upgrades to the next-tier rune type with bonus XP.
- An **Ourania Trader** NPC exchanges a points token (earned passively while
  playing the zone) for skilling outfit pieces, runes, and cosmetics.

A player who just stands inside the rotunda and clicks essence will get
**1× XP**, identical to standard altars. A player who pays attention to the
surge and protects their conduit charge can reach **3–6× XP** rate. There is
no failure mode — only gradient payoff.

---

## 1. Why This is Interesting (vs Just Another Altar)

The standard altars work fine. Ourania Rotunda exists because the AFK
population has no good "set and let it tick" route that *also* lets twitchy
players chase meaningful bonuses. Existing runecrafting methods split into:

- **Altar hopping** — fast XP, requires active attention.
- **Combination altars** — niche XP per essence, also active.
- **Combination-rune bonus** — small extra multiplier, no engagement.

Ourania Rotunda splits the population with the surge cycle:

| Player type | What they do | What they get |
|---|---|---|
| Pure AFK | Stand on any altar, click essence | Stable 1× XP |
| Light AFK | Stand on charged altar during surge | 2× XP for 5 min |
| Engaged | Hop between altars as the surge cycles | 2–6× XP over the hour |
| Optimizer | Tracks the conduit meter, dumps it on surge | 3–6× XP in short bursts |

The hook is that *some attention* yields *large* multipliers, but *no attention*
yields *normal* multipliers. The minigame never punishes you for looking away.

---

## 2. The Three Mechanics

### 2.1 Rune Surge (server-wide Clock)

- **Cadence:** every 5 real-time minutes, a new altar becomes "charged."
- **Halo effect:** the charged altar grants 2× XP; the two altars adjacent
  in the rotation order grant 1.25× XP. All others stay at 1×.
- **Visual cue:** a coloured beam over the charged altar (existing engine
  supports spotanim / gfx).
- **Implementation:** `world.gregs.voidps.engine.client.variable.Clock("rune_surge")`
  with an id of the form `<altar_id>_surge`. At each tick, a global handler
  promotes/demotes the `surge_<altar_id>` boolean per player.

### 2.2 Conduit Meter (per-player Timer)

- **How it fills:** every rune bound at any altar in the rotunda adds
  `+1` per rune of element tier (air=1, water=2, …) to a personal
  `rune_conduit` `Timer`.
- **Cap:** the timer tops out at `100` units; once it does, the player
  receives a 90-second `conduit_cataclysmic` Clock during which **all** altar
  bindings grant 3× XP.
- **After cataclysmic fires:** the meter resets to a residual `20` so the
  player can immediately start filling again.
- **When player leaves the rotunda:** the meter stops charging. On return,
  the player is told how much residual they had and given the choice to
  resume.

### 2.3 Essence Corruption (per-binding roll)

- **Chance:** 1 in 8 per binding attempt inside the rotunda.
- **Effect:** the essence produces the *next-tier* rune with bonus XP.
  Tier table: air → water → earth → fire → mind → body → cosmic → law →
  nature → astral.
- **Visual:** the bound rune "flashes" with the corruption colour and the
  chat broadcasts `Your essence shimmers and resolves into something greater.`
- **Synergy:** corruption events feed the conduit meter at 2× rate, so players
  see corruption events as net positives, not losses.

---

## 3. The Zone

### 3.1 Layout

Six altar objects in a hex ring around a central glyph — coordinates are
placeholders. The zone is a single room, **not instanced** (peak online
assumption: ≤30 players in the rotunda).

```
                   [Earth altar]
                       |
                       |
[Air altar] — [Glyph] — [Fire altar]
                       |
                       |
                [Water altar]

            (North arm: Cosmic altar — bonus room)
```

The Cosmic altar is reached through a one-way teleport from the central
glyph and offers combination-rune yields that sit outside the surge cycle.
It functions as a niche reward for max-level players rather than an
everyday training room.

### 3.2 Entry & Exit

- An **Ourania Guide** NPC stands outside the rotunda (e.g. near Astral altar
  south entrance). Dialogue offers `Enter the Rotunda?` and explains the
  surge cycle in a tooltip.
- Exit is via a portal that the player can click at any time.
- On death, the player is returned to the same entrance as Astral altar death.

---

## 4. Engine Anchors

These are the existing engine surfaces we expect to use. **Do not introduce
new engine code.** This plan is content-only.

| Surface | Used for | Reference |
|---|---|---|
| `bindRunes(player, id)` (`Runecrafting.kt:106`) | Bind essence into runes | existing pattern |
| `Clock("rune_surge")` and `Clock("conduit_cataclysmic")` | Time-based progression | `content/area/.../ShootingStar.kt` shows the same model |
| `Timer("rune_conduit")` | Per-player accumulation | `Forager.kt:51` shows the pattern |
| `World.members` | Members-only gating | `Runecrafting.kt:49` already reads it |
| `exp(Skill.X, n)` | XP grants | existing |
| `Tile.equals(x, y)` or `Region` | Surge beam positioning | existing |

---

## 5. File Layout

```
game/src/main/kotlin/content/skill/runecrafting/
├── OuraniaRotunda.kt              ← zone setup, entry/exit
├── ConduitMeter.kt                ← per-player timer logic
├── SurgeClock.kt                  ← global surge cycle handler
└── EssenceCorruption.kt           ← per-binding roll

game/src/main/kotlin/content/area/wilderness/ourania/
├── OuraniaGuide.kt                ← entry NPC
├── OuraniaTrader.kt               ← reward shop NPC
└── OuraniaGlyph.kt                ← central glyph object

data/area/wilderness/ourania/
├── ourania.objs.toml              ← altars, glyph, portal
├── ourania.npcs.toml              ← guide, trader
└── ourania.gfx.toml               ← surge beam, corruption flash

data/skill/runecrafting/
├── runes.toml                     ← (existing) tuning for tier XP rates
├── rotunda_altars.toml            ← altar→rune, surge-weight, position
└── rotunda_drop_table.toml        ← essence-corruption outcome table
```

---

## 6. Tuning (numbers that the design commits to)

| Constant | Value | Why |
|---|---|---|
| Surge cycle length | 5 minutes | long enough to AFK, short enough to be readable |
| Surge altar XP multiplier | 2.0 | strong but not absurd |
| Surge adjacent multiplier | 1.25 | a smaller reward for being close |
| Default altar XP multiplier | 1.0 | zero penalty for ignoring the surge |
| Cataclysmic window | 90 seconds | long enough to bind ~30 runes |
| Cataclysmic multiplier | 3.0 | strong payoff for filling the conduit |
| Conduit fill rate | `1` per rune tier | readable, slow enough to feel earned |
| Conduit cap | 100 units | ~10 minutes of slow binding at high tier |
| Residual after cataclysmic | 20% | keeps the loop moving |
| Corruption chance | 1/8 (12.5%) | happens often enough to be exciting |
| Corruption XP bonus | +50% | clear reward, no straight double |
| Trader token value | 1 token per 50 runes bound | fair passive income |

These are starting numbers. **Tune after a week of live metrics.** Each of
them lives in a toml file, so changing them does not require a rebuild.

---

## 7. Dialogue & Help

The Ourania Guide NPC greets players with a four-option menu (mirrors
`Wayne.kt`'s `choice { option(...) { } }` pattern):

- "Tell me about the surge." → opens a static text panel.
- "Tell me about the conduit meter." → static text panel.
- "Take me to the rotunda." → teleports into the rotunda.
- "I'm fine, thanks." → closes the menu.

The Trader has its own dialogue mirroring the shop template in
`Wayne.kt`. Adds flavour-only small talk in rotation per `RockCrabs.kt`'s
`when (random.nextInt(4))` style.

---

## 8. Reward Loop

The Trader exchanges `ourania_token` for:

| Reward | Cost | Type |
|---|---|---|
| Conduit outfit pieces | 25 / 100 / 250 / 500 / 1000 | matches `skilling-outfits-plan.md` |
| Astral rune pouch (cosmetic) | 750 | bespoke |
| 1 of each binding essence (50 charges/day) | 50 / restock | consumable |
| Title: "of the Rotunda" | 5000 | one-time |

`ourania_token` is a hidden inventory object that auto-counts on binding
events. It doesn't take inventory space — it lives as a per-player counter
exposed via `player["ourania_tokens"]`.

---

## 9. Phase 1 Build Checklist

- [ ] Place zone shapes: `world/data/map/ourania/...` (if maps are needed) —
  likely a single tile room, so possibly skip.
- [ ] Author altar objects in `data/area/wilderness/ourania/ourania.objs.toml`.
- [ ] Author NPCs (Guide, Trader) in `data/.../npcs.toml`.
- [ ] Implement `SurgeClock.kt` and verify the rotation logic in isolation.
- [ ] Implement `ConduitMeter.kt` and the cataclysmic trigger.
- [ ] Implement `EssenceCorruption.kt` and rewire `Runecrafting.kt:bindRunes`
  to accept a per-binding hook (or wrap calls in the rotunda handler).
- [ ] Implement `OuraniaRotunda.kt` (zone entry/exit + per-zone XP multiplier
  reads `surge_<altar_id>` and `conduit_cataclysmic` clocks).
- [ ] Implement trader shop + reward table.
- [ ] Add a small task in `content/achievement/OutfitTasks.kt` that triggers on
  the first rotunda rune.
- [ ] Smoke test: enter, bind, observe surge, fill conduit, fire cataclysmic.

---

## 10. Phase 2 (Stretch)

| Idea | Mechanic |
|---|---|
| **Rotunda Boss** — an "Abyssal Regent" spawns at the cosmic altar at random | Combat add for maxed runecrafters who like PK; mirrors `RockCrabs.kt` combat patterns |
| **Weekly Rotunda Bounty** — first 100 runes bound on a fresh-rotunda week give 10× XP | Server-wide `Clock` + achievement tier |
| **Rotunda Leaderboard** | Storage-driven, daily reset, top 10 displayed in a custom interface |
| **Seasonal Surge Floors** | Once a month, all altars get a permanent surge multiplier |
| **Cosmetic altars** | Players buy rotating altar visuals with tokens |

---

## 11. What this plan does NOT touch

- `Runecrafting.kt` is read-only on its bind-logic. We do not modify it.
  We reuse `bindRunes()` via wrap-hooks or by replicating the small amount
  of logic needed. If a refactor is needed to expose a hook, that's a
  separate small plan.
- Combination runes are *not* re-tuned.
- The Astral altar storyline (existing Ourania quest chain) is left alone.

---

## 12. Open Questions

1. **Should the rotunda be members-only?** `World.members` check is
   one-line. Recommend yes.
2. **Should we deduplicate essence-corruption with binding necklace?** The
   necklace already grants partial-success binding. Likely fine to stack,
   but verify this with a test player.
3. **How loud is the surge beam visual?** Important because adjacent altars
   also get a small effect; we don't want it to be visually overwhelming.
4. **What happens to the conduit meter on death?** Recommend *preserve* so
   players don't feel cheated by a boss they couldn't avoid. Resetting on
   logout but preserving through death is the OSRS-ish convention.
5. **Can the rotunda zone crash?** A zero-effort zone with a 5-min cycle
   shouldn't be able to. But the `Clock` reset behaviour should be tested
   explicitly.

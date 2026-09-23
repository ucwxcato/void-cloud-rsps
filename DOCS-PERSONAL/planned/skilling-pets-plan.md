# Skilling Pets — Design & Implementation Plan

A roster of passive skilling "pets" — mechanically the Summoning familiar
system that already exists — that grant small flavour bonuses while summoned.
Each pet uses the `Forager.kt` pattern (30-second timer, drop-table roll into
BoB) so we don't write new timing infrastructure.

This plan does **not** invent a new pet system; it parameterises the
existing one.

---

## 0. TL;DR

- All skilling pets are **summoning familiars** with a passive drop-table
  side-effect. Combat-special familiars (Beaver-style object targets) are a
  Phase 2 stretch.
- One shared framework file: `content/skill/SkillerPet.kt`.
- Each pet is **a single toml entry + a single drop table** + an entry in
  `EngineModules.kt`.
- First pets: **Cliff (mining)**, **Yew Sprig (woodcutting)**, **Embertick (firemaking)**,
  **Tidepool (fishing)** — one per primary gathering skill.

---

## 1. Why Familiars, not a New System

Peeking at `content/skill/summoning/Forager.kt` and `content/skill/summoning/familiar/Beaver.kt`:

- `Forager.kt:43` already starts a 30-tick `timerStart("forage")` that loops.
  At each tick, the engine looks up the follower's familiar id, finds a
  `forage_<familiar>` drop table, rolls it, and stuffs the result into the
  owner's beast-of-burden inventory.
- The whole thing is data-driven — the table name is discovered from the
  follower's id. **Adding a new pet is just adding a drop table entry and a
  familiar row.**

So this plan is less "write a pet system" and more "fleet of small definitions."

---

## 2. Design Philosophy

- **Pets are flavour, not power.** Each gives a 1–5% improvement at most.
- **Pets require an active engagement loop** (summoning), so they feel like a
  reward, not a passive buff that competes with other familiars.
- **AFK-friendly.** The `Forager` pattern doesn't require clicks once summoned;
  the player can just train while the pet rolls.
- **No pet gives XP for "doing nothing."** Each pet's bonus only fires when the
  player is *actively skilling in that pet's theme*. (Idle AFK with the pet
  summoned still produces fletched loot over time, but no XP from thin air.)

---

## 3. The Shared Framework

### 3.1 `content/skill/SkillerPet.kt`

```kotlin
package content.skill

import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.exp.exp
import world.gregs.voidps.engine.entity.item.drop.DropTables
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.beastOfBurden
import world.gregs.voidps.engine.timer.Timer
import world.gregs.voidps.engine.timer.toTicks
import java.util.concurrent.TimeUnit

/**
 * Parametrised version of Forager.kt. Drives a pet to roll a drop table on a
 * tick and grant XP for each yielded item. The list of (item → XP) mappings
 * is supplied by the kit config; missing mappings default to no XP.
 *
 * Run via `startForage(player, petId, tableId, items)`. Hooks into:
 *   - summonFamiliar(petId) → starts the timer
 *   - dismissFamiliar        → stops the timer
 */
class SkillerPet(val dropTables: DropTables) : Script {

    init {
        timerStart("skiller_pet_forage") { TimeUnit.SECONDS.toTicks(30) }
        timerTick("skiller_pet_forage") {
            val pet = follower?.id ?: return@timerTick Timer.CANCEL
            val cfg = SKILLER_PETS[pet] ?: return@timerTick Timer.CONTINUE
            val table = dropTables.get(cfg.tableId) ?: return@timerTick Timer.CONTINUE
            val rolls = table.roll(player = this)
            if (rolls.isEmpty()) return@timerTick Timer.CONTINUE
            for (drop in rolls) {
                val item = drop.toItem()
                if (item.isEmpty()) continue
                beastOfBurden.add(item.id, item.amount)
                cfg.items.firstOrNull { it.item == item.id }?.let { grant ->
                    exp(grant.skill, grant.xp)
                }
                message("Your ${cfg.displayName} finds something.")
            }
            Timer.CONTINUE
        }
    }

    data class PetItem(val item: String, val skill: Skill, val xp: Double)
    data class PetConfig(
        val pet: String,
        val displayName: String,
        val tableId: String,
        val items: List<PetItem>,
    )

    companion object {
        val SKILLER_PETS: Map<String, PetConfig> = mapOf(
            // populated in §5 below
        )
    }
}
```

(Note: `SKILLER_PETS` is intentionally a `Map` so we can add new entries without
touching the rolling logic. Real implementation will likely move the map to a
DI-supplied service so it can be updated without recompiling.)

### 3.2 What the framework does NOT do

- It does not handle combat-flavour pets (Phase 2). Combat variants reuse the
  existing `FamiliarCombat.kt` surface.
- It does not display the pet as a follower on-screen beyond the existing
  familiar renderer.

---

## 4. Pets (Phase 1)

One per primary gathering skill. Each gets:
- a familiar npc id (`<pet>_familiar`),
- a pouch recipe in `content/skill/summoning/SummoningCrafting.kt` if we want
  craftable, otherwise a drop-only vibe,
- a drop table with 1–3 results,
- a chat message at first summon (`"...something slithers out of your pack..."`).

| Pet | Familiar id | Skill | Drop items | XP granted |
|---|---|---|---|---|
| **Cliff** (mining) | `cliff_pet` | Mining | `iron_ore`, `coal`, occasional `runite_ore` (rare) | small XP per ore |
| **Yew Sprig** (woodcut) | `yew_sprig_pet` | Woodcutting | `logs`, `oak_logs`, occasional bird's nest | small XP per log |
| **Embertick** (firemaking) | `embertick_pet` | Firemaking | `tinderbox`, `firelighter`, rare `torch` | small XP on lit fire |
| **Tidepool** (fishing) | `tidepool_pet` | Fishing | same raw fish as the player catches + rare casket | 10% of catch XP |

**Acquisition:** Pets drop at a flat 1/3,000 chance from any successful action
in the matching skill. The drop rolls in the existing `DropTables` machinery —
a single line in `data/skill/<skill>/<skill>_extraction_drop_table`.

---

## 5. Drop Table Sketch

Under `data/skill/<skill>/`:

```toml
# extract-mining-cliff-pet.toml
[cliffs_minerals]
items = [
    { id = "iron_ore",   chance = 0.5,   amount = [1, 3] },
    { id = "coal",       chance = 0.4,   amount = [1, 2] },
    { id = "runite_ore", chance = 0.05,  amount = 1 },
]
xp_per_drop = 0   # xp is granted based on the matching item, e.g. iron=5
```

The framework's `PetConfig.items` list maps `id → (skill, xp)` so e.g.
`iron_ore` → `(Mining, 5.0)`, mirroring the data the rock defs already use.

---

## 6. File Layout

```
game/src/main/kotlin/content/skill/
├── SkillerPet.kt                  ← shared framework
└── pet/                           ← optional per-pet thin wrappers
    ├── Cliff.kt
    ├── YewSprig.kt
    ├── Embertick.kt
    └── Tidepool.kt

data/skill/mining/
└── cliff_pet_drop_table.toml
data/skill/woodcutting/
└── yew_sprig_drop_table.toml
data/skill/firemaking/
└── embertick_drop_table.toml
data/skill/fishing/
└── tidepool_drop_table.toml
```

Some kits may not need a wrapper — if the `Forager`-style timer handles them
fully, the per-skill file is empty.

---

## 7. Engine Wiring

- **Familiar definitions** in `data/skill/summoning/familiars.tables.toml`
  (already the location for familiar metadata — we add four rows).
- **Pouch recipes** in `data/skill/summoning/` (or skip and have the pet be
  drop-only for v1).
- **Timer hooks** on `summonFamiliar` / `dismissFamiliar` so the timer starts
  and stops correctly. Search `content/skill/summoning/Summoning.kt` for the
  existing hooks.
- **Koin registration** for the drop tables service if not already global.

---

## 8. Phase 2 (Stretches)

| Idea | Mechanic |
|---|---|
| **Combat-flair pets** (Beaver-style object target special) | Reuse `Beaver.kt` template: copy the file 4×, swap the action (mine rock, chop tree, etc.) |
| **"Mood" moods per pet** | Pet visibly bigger / smaller depending on player skill level (already partly exists for `Forager`) |
| **Pet transmogrification** | Item that lets you turn one pet into a cosmetic version of another |
| **Pet-specific tasks** | Achievement entries for "summon Cliff for 1000 ticks" etc. |

---

## 9. Phase 1 Build Checklist

- [ ] Confirm `SKILLER_PETS` data-flow — does the map get populated by Koin, or
  do we hardcode it in `companion object`?
- [ ] Write `SkillerPet.kt`
- [ ] Wire familiar definitions for the 4 pets
- [ ] Add pouch recipes (or drop-only routes)
- [ ] Author 4 drop tables
- [ ] First-summon dialogue hooks (re-use existing `FamiliarSummon.kt` patterns)
- [ ] Smoke test: summon Cliff, mine an iron rock, verify XP+loot
- [ ] Repeat for the other 3 pets
- [ ] Add 4 first-summon achievements

---

## 10. Open Questions

1. **Should pets be untradeable, account-bound, or tradable?** Pets in this
   codebase are typically untradeable (Soul Wars rewards, etc.) — recommend
   account-bound for v1.
2. **Pet food / summoning cost.** Summoning already has a points cost via
   the existing `Summoning.kt`. No new cost layer needed.
3. **Bank storage.** Can pets be stored in the costume tab? The bank tab
   system may need a special entry for bound familiars.
4. **Inventory interaction.** When the BoB is full, the pet logs a message and
   skips the roll (per `Forager.kt:79`). Confirm this is the intended UX.

---

## 11. What this plan does NOT touch

- Combat familiars (existing Familiars with combat roles — those are already
  in `content/skill/summoning/familiar/`).
- Summoning skill itself (no new items, no level changes).
- Pet interaction dialogue beyond first-summon flavour.

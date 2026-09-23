# Skilling Outfits — Design & Implementation Plan

A unified framework for skilling outfits ("hood + top + legs + gloves + boots")
that grant a small XP bonus per piece worn, plus a **set bonus** at full 5-piece
assembly. Kits are data-driven — no per-kit boilerplate beyond a small config
file and (optionally) a shop NPC.

---

## 0. TL;DR

- One shared engine: `content/skill/Outfit.kt` exposes a per-skill XP hook that
  reads equipped gear and applies a multiplier.
- Each kit is a small data file + (optional) shop.
- First release: **Harbourmaster (fishing)**, **Stoneward (mining)**,
  **Wildwood (woodcutting)**, **Coalfire (firemaking)**, **Conduit (runecrafting)**.

The system sits entirely on top of the existing XP pipeline (`exp(Skill.X, n)`
→ handled by the engine's XP event bus), so we don't touch the engine.

---

## 1. Design Philosophy

- **Engage, don't gate.** Pieces are individual upgrades; the full set is a
  small bonus on top, not a power spike.
- **Per-piece 0.5% XP bonus, set adds a small skill-specific flavour bonus.**
  This mirrors the OSRS convention so it feels familiar.
- **No set bonuses tied to combat.** Outfits are pure skilling flavour; combat
  outfits (Raiments of Rigour, etc.) are out of scope for this plan.
- **Drop / shop split.** Each kit has both an open-world drop source (rare
  table) and a points shop NPC. Players can pick their grind.
- **Per-skill variable key** (`harbourmaster_helmet` etc.) so quests and custom
  commands can target pieces later.

---

## 2. The Framework

### 2.1 `content/skill/Outfit.kt`

```kotlin
package content.skill

import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.equip.equipped
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.timer.toTicks
import java.util.concurrent.TimeUnit

/**
 * Generic skilling-outfit XP boost. Reads equipped gear that starts with
 * [prefix] and counts pieces; every N pieces adds [perPiece] extra XP on every
 * XP gain in [skill].
 *
 * Pieces are conventionally: hat / chest / legs / hands / feet, but the
 * framework doesn't care which slots a kit occupies.
 */
class Outfit(
    private val skill: Skill,
    private val prefix: String,
    private val perPiece: Double = 0.005,
    private val pieceSlots: List<EquipSlot> = listOf(
        EquipSlot.Hat, EquipSlot.Chest, EquipSlot.Legs, EquipSlot.Hands, EquipSlot.Feet
    ),
) : Script {

    init {
        // Hook the engine's XP-gain event for this skill. The exact API name
        // (`exp` / `xpGain`) will be confirmed against the existing engine
        // when the first instance is wired in.
        xpGain(skill) { amount ->
            amount * (1.0 + pieces(this) * perPiece)
        }
    }

    private fun pieces(player: Player): Int =
        pieceSlots.count { player.equipped(it).id.startsWith("${prefix}_") }
}
```

A separate file (`content/skill/OutfitConfig.kt`) lists the kits at startup so
Koin can construct one `Outfit` per skill:

```kotlin
val OUTFIT_KITS = listOf(
    Kit(Skill.Fishing,    "harbourmaster", perPiece = 0.005),
    Kit(Skill.Mining,     "stoneward",     perPiece = 0.005),
    Kit(Skill.Woodcutting,"wildwood",      perPiece = 0.005),
    Kit(Skill.Firemaking, "coalfire",      perPiece = 0.005),
    Kit(Skill.Runecrafting,"conduit",      perPiece = 0.005),
)
```

Register in `Main.kt` `EngineModules.kt`:

```kotlin
single { (kit: Kit) -> Outfit(kit.skill, kit.prefix, kit.perPiece) }
```

### 2.2 What still needs confirming before coding

- The exact XP-gain event name. Search `engine/Skill.kt` and the `expGain(...)`
  / `onXp(...)` registrations already in the codebase to find the canonical
  block; pick whichever the engine already exposes.
- Whether `EquipSlot.Hands` and `EquipSlot.Feet` exist as-is, or whether we
  need a fallback (some 2011 kits only cover head/chest/legs).

---

## 3. Kits (Phase 1)

Each kit ships with 5 wearable pieces (head, chest, legs, hands, feet) at a
shared item level (no stats, no combat). Naming follows the existing
`<prefix>_<slot>` convention so anything reading them can do a prefix lookup.

| Kit (prefix) | Skill | Drop source | Shop NPC |
|---|---|---|---|
| `harbourmaster` | Fishing | Random from caskets + trawler reward table | `Jaques` (south of Fishing Guild) |
| `stoneward` | Mining | Rare drop from rock golems (Falador mine area) | `Yarsul` (Mining Guild) |
| `wildwood` | Woodcutting | Random from bird's nests (5% rate at 90+ WC) | `Lathas` (Seers' village) |
| `coalfire` | Firemaking | Reward from Firemaking daily challenges | `Ignisia` (Taverley) |
| `conduit` | Runecrafting | Drops from Ourania rogues (see AFK RC plan) | `Ourania herald` (ASTRAL altars) |

For Phase 1, set bonuses are *purely cosmetic* (a chat message at full set:
"You feel fully attuned to the water." / etc.). Phase 2 turns them into a
small skill-specific roll (see §6).

---

## 4. File Layout

```
game/src/main/kotlin/content/skill/
├── Outfit.kt                  ← framework (this plan §2.1)
├── OutfitConfig.kt            ← kits list, Koin-loaded
└── outfit/                    ← per-kit drop tables, settings
    ├── harbourmaster/
    │   └── HarbourmasterShop.kt
    ├── stoneward/
    │   └── StonewardShop.kt
    └── ...

data/equipment/
├── harbourmaster.hats.toml
├── harbourmaster.chests.toml
├── harbourmaster.legs.toml
├── harbourmaster.hands.toml
├── harbourmaster.feet.toml
└── (one toml group per kit, mirrored)

data/skill/outfit/
└── outfits.toml               ← per-kit tuning: perPiece, set bonus, drop rate

data/saves/unlocks/            ← player-unlocked outfit pieces (engraved separately
                                 if we ever go that route — out of scope for v1)
```

Shop NPCs follow the `Wayne.kt` template (`npcOperate("Talk-to", "id") →
openShop(...)`). Each shop carries the five pieces at a flat 1,000–5,000 gp
range — pricing lives next to the item def.

---

## 5. Drop Mechanics (Phase 1.5, after kits exist)

Pick one source per kit:

1. **Casket / task reward** — reuses `DropTables.roll()` machinery; pure data.
2. **Rare from familiar** — adds an entry to an existing pet forage table.
3. **Shop-only** — kits that feel too rare for the drop table.

The simplest path: just open shops. Casket drops become a stretch goal.

---

## 6. Phase 2 (nice-to-haves)

These depend on Phase 1 shipping and on real player feedback.

| Idea | Mechanic | Engine hook |
|---|---|---|
| Set-specific roll chance | At 5 pieces, +5% chance of double catch / extra ore | override `Drops.roll()` per item |
| Outfit pieces degrade | Lose piece on death-in-Wilderness | `onDeath` event |
| Outfit enhance scrolls | Right-click cosmetic recolours | custom ItemOption |
| Daily outfit synergy | Full set gives 2× drop chance for one rock/tree | Clock + extra Timer |

Each is small and self-contained once the framework exists.

---

## 7. Phase 1 Build Checklist

- [ ] Confirm XP-gain event name in engine
- [ ] Write `Outfit.kt` (§2.1) and `OutfitConfig.kt`
- [ ] Wire `Outfit` into `EngineModules.kt`
- [ ] Author 5 item defs for Harbourmaster (head/chest/legs/hands/feet)
- [ ] Author `HarbourmasterShop.kt` (~25 lines, `Wayne.kt` style)
- [ ] Compile & smoke test (login, equip all 5, cast fishing net, observe XP)
- [ ] Repeat per kit for the remaining 4
- [ ] Add a tiny outfit-tracking achievement hook (`content/achievement/OutfitTasks.kt`)

---

## 8. Open Questions

1. **Cosmetic vs stackable.** Should pieces stack in the bank tab they live in,
   or take their own cosmetic slot? Bank tabs in this codebase are folder-
   based; check `world.gregs.voidps.engine.inv.bank.Bank.kt` for the existing
   tab API.
2. **Outfit cosmetics and overrides.** Does the engine support a per-piece
   override mesh per player? If not, garments share the base body model (which
   is fine for launch).
3. **Should bonuses be XP-only, or include a small "double roll" buff?** XP-only
   keeps the system deterministic and easy to tune.

---

## 9. What this plan does NOT touch

- Combat outfits (Raiments of Rigour, etc.) — separate plan.
- Skill capes — already handled in `content/entity/player/skillcape/` patterns
  visible across the codebase.
- Engine schema (no new `Skill` enum values, no new `EquipSlot` entries).

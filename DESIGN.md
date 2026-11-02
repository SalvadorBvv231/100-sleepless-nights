# 100 Sleepless Nights — Design

By Sans Słońce · Minecraft Forge 1.7.10 · License: LGPL-3.0

## Core idea

Vanilla mobs only. Difficulty grows with the **world day**:

```
day = total world time / 24000
```

- Every tier **adds to** the previous ones.
- Changes **ramp smoothly**, with no sudden jumps.

## Tiers

| Day  | Adds |
|------|------|
| 0–10 | Vanilla, no changes |
| 10+  | More night spawns, longer detection range |
| 25+  | Slightly more health and damage; zombies break wooden doors |
| 50+  | Armor and weapons are more common (not enchanted) |
| 75+  | Creeper explosions grow gradually |
| 100+ | Some mobs get full armor and enchanted gear |

**Siege nights:** every 10 days, an extra wave heads to the player's base.

## Rules

- Mobs never get faster.
- Baby zombie and creeper ratios stay vanilla.
- Extra spawns follow all vanilla spawn rules, so slabs, chests and lit areas stay safe.
- Also, nothing spawns on leaves or logs.
- All numbers live in a config file.
- A day speed multiplier exists for testing.
- Commands:
  - `/sleepless status`
  - `/sleepless setday <n>`
- A chat message announces when a tier begins.
- Server-side.

## Progress

### Milestone 1 ✓ — difficulty clock (no mob changes)

- Difficulty day = overworld total world time / 24000 × `daySpeedMultiplier` + offset.
- The offset is set by `/sleepless setday` and saved in the world (`data/sleepless100.dat`).
- Config `config/sleepless100.cfg`: `daySpeedMultiplier`, `tierStartDays`, `tierChatMessages`.
- `/sleepless status` (everyone): day, tier, and the next tier and when it starts.
- `/sleepless setday <n>` (ops only).
- A chat message goes to all players when a new tier begins, once per tier.
- The day/tier math lives in `core/DifficultyClock` with no Minecraft imports, and is unit-tested.

### Milestone 2 — mob effects of all 5 tiers

- Each tier's effects start at 0 on its start day and grow linearly until the next tier starts.
- The final tier ramps over `finalTierRampDays` (25).
- Effects then stay at full strength, so later tiers add on top.
- All full-strength values are in the `effects` section of the config.
- Effects are decided once, when a hostile mob spawns, and saved in the mob's data.
- Existing mobs are never changed. Bosses are excluded. Movement speed is never touched.
- Tier 1:
  - Extra vanilla-style night spawn passes with a raised hostile cap (default +62.5%).
  - Spawns use the vanilla mob mix and spawn rules, and never land on leaves or logs.
  - Follow range +.
- Tier 2:
  - Max health +.
  - Damage dealt + (melee and arrows, not explosions).
  - Zombies break wooden doors on any difficulty (needs gamerule `mobGriefing`).
- Tier 3: zombies and skeletons get extra unenchanted armor, and zombies sometimes get an iron sword or shovel.
- Tier 4: creeper explosion radius × scale, capped by `tier4CreeperMaxRadius` (default 6, a vanilla charged creeper).
  The cap applies after vanilla's charged doubling, so charged creepers stay at 6.
- Tier 5: some zombies and skeletons get full chainmail, iron or diamond armor plus an enchanted weapon.
- `/sleepless effects` shows the current strengths.
- `debugLogging` logs every changed mob.

## Phase 2: our own AI

- Track players by sound, light and blood.
- Break doors and trapdoors.
- Plan routes up ladders.
- Keep chasing: jump gaps, risk lava.
- Mechanisms last.
- Then remove the need for Zombie Awareness.

## Phase 3: ports

- Port to 1.12.2, 1.20.1 and 1.21.
- Keep the game rules separate from Minecraft code, so they can move between versions.

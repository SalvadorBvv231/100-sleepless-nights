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

**Friday Night Party:** day 0 is a Sunday. Every Friday (difficulty day % 7 == 5), from dusk to sunrise,
a horde heads for the player's home. It grows every week.

## Rules

- Mobs never get faster: movement speed is never changed.
- No diamond armor or weapons before difficulty day 150 (`diamondGearMinDay`), from any mob or mod source:
  mob gear (vanilla's or ours), party rewards, champion loot and villager trades.
  Diamond pieces become iron (enchantments kept). Crafting by players is not restricted.
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

### Milestone 3 — Friday Night Party and villages

All party and village math lives in `core/` (`PartyRules`, `VillageRules`, `LootTable`) without Minecraft
imports, and is unit-tested. Every number is in the `party` and `villages` config sections.

**When.** The party starts at dusk (time of day 13000) on a Friday, at most once per Friday, and ends at sunrise
(23000). It can still start until midnight (18000), e.g. after `setday`. Friday 1 is day 5, Friday 2 is day 12, ...
"Week N" means Friday N.

**Start.**
- Chat to everyone in the overworld: "Friday Night Party — they're coming..." and a distant zombie groan for
  each guest, from the direction of a group.
- Sleeping is blocked on party nights (from dusk on a Friday until sunrise), otherwise the party is skipped.
- Multiplayer: one party per Friday, aimed at one random player in the overworld (the host). Players
  within 64 blocks of the target are guests and share the reward.

**Target.**
- The host's bed (or the world spawn if they have no bed), if the host is within 48 blocks of it.
  Otherwise the host's position. Re-checked every 5 seconds.
- Never a village: party zombies ignore villagers, and the target is always a player's home or position.

**Spawning.**
- 2–4 groups (about 5 mobs per group), from evenly spread sides with some random angle.
- 24–40 blocks from the target, on solid ground near the target's height. Never on leaves or logs, and never
  in torch light (block light 8+).
- Out of sight: line of sight to each guest is blocked, or the spot is outside the guest's view.
- One group spawns every 2 seconds, so the laptop doesn't spike. If no spot is found, it retries for 30 s.
- Party mobs don't despawn while the party is running.

**Size and mobs.**
- 4 mobs on Friday 1, +2 per week, capped at 30.
- Fridays 1–2: zombies only. 3–5: zombies + 1 random type. 6–9: + 2 types. 10+: + 3 types.
- Extra types: skeletons, spiders, witches, creepers (at most 2), endermen. Each extra type is ~15% of the
  party (at least 1, at most 4); the rest are zombies.
- Party endermen attack players unprovoked, but only during the party.
- Party mobs get the normal tier effects and gear (milestone 2).

**Regroup, then attack.**
- Each group first walks to a rally point 18 blocks from the target, on its own side.
- When every group has spawned and 75% of each group is at its rally point (or after 40 s), all groups
  attack together.
- Attacking mobs head for the target and lock on to a guest within 24 blocks.

**Miners** (30% of party zombies, from week 1) hold a pickaxe and dig when blocked, towards their target.
- Weeks 1–3: dirt, grass, sand, gravel. Weeks 4–7: + logs, planks, wooden stairs/slabs/fences/trapdoors, glass.
  Week 8+: + stone, cobblestone. The block lists are in the config.
- Never: ores (including ore-dictionary ores), obsidian, iron blocks, bedrock, unbreakable blocks, or any block
  with a tile entity (chests, furnaces, ...).
- Digging time = block hardness × 60 ticks (at least 1 s). Cracks and sounds show progress.
- Dug blocks drop as items (glass too). Needs gamerule `mobGriefing`.

**Builders** (20% of party zombies, from week 5) hold dirt and carry 16 blocks.
- Blocked by a wall, or target above: pillar up (jump, place dirt below), at most 6 blocks high.
- Gap or water in front: bridge with dirt. Needs `mobGriefing`.

**Champions** (from week 8): 1–2 per party, chosen from zombies and skeletons, named "Champion ..." with
1–2 powers:
- Regen: permanent Regeneration I.
- Bulwark: permanent Resistance I and +50% knockback resistance.
- Vengeance: reflects 30% of melee damage back to the attacker.
- Webber: every 10 s, puts a cobweb at its target's feet when within 6 blocks (needs `mobGriefing`). Webs
  vanish at sunrise.
- Better loot: 50% equipment drop chance per slot, 3 rolls on the reward loot table, +30 XP.

**Reward.** Guests who don't die before sunrise get XP (10 + 4 per party mob) and loot rolls (0.3 per party mob,
at least 1) from a configurable weighted loot table. Items go to the inventory, or drop at the player's feet.

**Repair (config, default off).** When on, every block party mobs dig, place or blow up comes back after
10 minutes, if nobody changed it since. With repair on, dug blocks don't drop items and party creepers
don't break blocks with tile entities (no duplication).

**Commands.**
- `/sleepless status` also shows the weekday and the next party (or the running party: phase, mobs left).
- `/sleepless party start [friday]` (ops): starts a party now, as Friday N (default: the current week).
- `/sleepless party stop` (ops): ends the party without rewards.

**Villages.**
- Iron golems spawn 1.5× as often (`golemSpawnMultiplier`): an extra roll on top of vanilla's, with vanilla's
  rules (villagers / 10 golems, more than 20 doors).
- Golem health and damage: +15% per tier, ramping with the tiers (all golems, re-checked every 30 s).
- From day 50: one extra golem per village, on top of vanilla's cap. This extra slot needs no 20 doors, only
  villagers.
- From day 100: villagers go indoors from time 11000 (about 1 in-game hour before vanilla's dusk).

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

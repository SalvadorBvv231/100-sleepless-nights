# 100 Sleepless Nights

**Minecraft 1.7.10 · Forge · Server-side**

Vanilla mobs, but the nights get harder every day. No new mobs, no faster mobs: just more of them, tougher, and smarter about when they come.

## How it works

Difficulty follows the **world day**. Every tier adds to the previous ones and ramps in smoothly, with no sudden jumps. A chat message tells you when a new tier begins.

| Day  | What changes |
|------|--------------|
| 0–10 | Vanilla |
| 10+  | More night spawns, longer detection range |
| 25+  | Slightly more health and damage; zombies break wooden doors |
| 50+  | Armor and weapons are more common |
| 75+  | Creeper explosions grow gradually |
| 100+ | Some mobs get full armor and enchanted gear |

## Friday Night Party

Every Friday night, a horde marches on your bed. It grows every week and learns new tricks:

- **Miners** dig through dirt, wood and eventually stone to reach you.
- **Builders** pillar up and bridge gaps.
- **Champions** show up from week 8 with special powers.
- You can't sleep through it. Survive until sunrise for XP and loot.

## Villages

More iron golems, stronger golems, and late in the game villagers head indoors earlier.

## Fair rules

- Mobs never get faster.
- No diamond gear from mobs, loot or trades before day 150.
- Extra spawns follow all vanilla spawn rules, so slabs, chests and lit areas stay safe.
- Every number is configurable in `config/sleepless100.cfg`, and a day speed multiplier is there for testing.

## Commands

- `/sleepless status`: current day, tier and next party
- `/sleepless effects`: current effect strengths
- `/sleepless setday <n>` (ops)
- `/sleepless party start|stop` (ops)

## Installation

Put the jar in your `mods` folder. It is **server-side**: install it on the server (or in a single-player world); clients do not need it.

Requires Minecraft 1.7.10 with Forge 10.13.4.1614 or newer.

## License

LGPL-3.0-or-later. Source: https://github.com/SalvadorBvv231/100-sleepless-nights

# 100 Sleepless Nights

A Minecraft 1.7.10 Forge mod by Sans Słońce. Mod ID: `sleepless100`.

## Features

Vanilla mobs only. Difficulty grows with the world day, and every tier adds to the previous ones.

- **Five tiers** (day 10, 25, 50, 75, 100): more night spawns and follow range, tougher mobs and door-breaking zombies, more armor and weapons, bigger creeper explosions, and some fully armored, enchanted mobs. Mob speed is never changed.
- **Friday Night Party:** every Friday night a horde marches on your bed. It grows each week and adds miners, builders and champions. Survive until sunrise for XP and loot.
- **Villages:** more iron golems, stronger golems, and villagers that go indoors earlier late in the game.
- **No early diamond gear** (before day 150) from mobs, loot or trades.
- **Commands:** `/sleepless status`, `/sleepless effects`, `/sleepless setday <n>` (ops), `/sleepless party start|stop` (ops).
- Everything is configurable in `config/sleepless100.cfg`. See [DESIGN.md](DESIGN.md) for details.

## Install

1. Install Minecraft 1.7.10 with Forge 10.13.4.1614 or newer.
2. Download `sleepless100-v1.0.0.jar` from the [releases page](../../releases).
3. Put it in the `mods` folder (on a server, the server's `mods` folder; it is server-side).
4. Start the game. The config file is created on first launch.

## Building and running

```sh
./gradlew build       # builds the mod jar into build/libs/
./gradlew runClient   # launches Minecraft with the mod loaded
```

Built on the [GTNewHorizons ExampleMod 1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10) starter.

## License

Copyright (C) 2026 Sans Słońce. Licensed under the GNU Lesser General Public License v3.0 or later. See [COPYING.LESSER](COPYING.LESSER) and [COPYING](COPYING).

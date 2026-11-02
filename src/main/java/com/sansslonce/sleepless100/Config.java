package com.sansslonce.sleepless100;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.sansslonce.sleepless100.core.DifficultyClock;
import com.sansslonce.sleepless100.core.EffectSettings;
import com.sansslonce.sleepless100.core.LootTable;
import com.sansslonce.sleepless100.core.PartySettings;
import com.sansslonce.sleepless100.core.VillageSettings;

/** Settings loaded from config/sleepless100.cfg. */
public class Config {

    public static final String CATEGORY_DIFFICULTY = "difficulty";

    public static double daySpeedMultiplier = 1.0;
    public static int[] tierStartDays = DifficultyClock.DEFAULT_TIER_STARTS.clone();
    public static boolean tierChatMessages = true;
    public static boolean debugLogging = false;

    public static final String CATEGORY_EFFECTS = "effects";

    /** Full-strength effect values; each ramps up from 0 during its tier. */
    public static final EffectSettings effects = new EffectSettings();

    public static final String CATEGORY_PARTY = "party";
    public static final String CATEGORY_VILLAGES = "villages";

    public static final PartySettings party = new PartySettings();
    public static final VillageSettings villages = new VillageSettings();
    /** Block names miners can dig: list N opens on Friday digWeeks[N-1]. */
    public static String[][] minerBlocks = new String[3][];
    public static LootTable rewardLoot = LootTable.parse(new String[0]);

    public static final String[] DEFAULT_MINER_BLOCKS_1 = { "minecraft:dirt", "minecraft:grass", "minecraft:mycelium",
        "minecraft:farmland", "minecraft:sand", "minecraft:gravel" };
    public static final String[] DEFAULT_MINER_BLOCKS_2 = { "minecraft:log", "minecraft:log2", "minecraft:planks",
        "minecraft:wooden_slab", "minecraft:double_wooden_slab", "minecraft:oak_stairs", "minecraft:spruce_stairs",
        "minecraft:birch_stairs", "minecraft:jungle_stairs", "minecraft:acacia_stairs", "minecraft:dark_oak_stairs",
        "minecraft:fence", "minecraft:fence_gate", "minecraft:trapdoor", "minecraft:glass", "minecraft:glass_pane",
        "minecraft:stained_glass", "minecraft:stained_glass_pane" };
    public static final String[] DEFAULT_MINER_BLOCKS_3 = { "minecraft:stone", "minecraft:cobblestone",
        "minecraft:mossy_cobblestone", "minecraft:stone_stairs", "minecraft:cobblestone_wall" };
    public static final String[] DEFAULT_REWARD_LOOT = { "minecraft:bread,2,4,10", "minecraft:cooked_beef,1,3,8",
        "minecraft:arrow,4,12,8", "minecraft:iron_ingot,1,3,8", "minecraft:gold_ingot,1,2,5", "minecraft:emerald,1,2,4",
        "minecraft:experience_bottle,1,3,3", "minecraft:ender_pearl,1,1,2", "minecraft:name_tag,1,1,1",
        "minecraft:golden_apple,1,1,1" };

    private static void loadEffects(Configuration c) {
        c.setCategoryComment(
            CATEGORY_EFFECTS,
            "Mob effects. Each value is the FULL strength, reached at the end of its tier's ramp:"
                + " an effect starts at 0 on its tier's start day and grows smoothly until the next tier starts."
                + " Effects only apply to newly spawned mobs. Movement speed is never changed.");
        EffectSettings d = new EffectSettings();
        EffectSettings e = effects;

        e.diamondMinDay = num(
            c,
            "diamondGearMinDay",
            d.diamondMinDay,
            0,
            100000,
            "No diamond armor or weapons before this difficulty day, from any mob or mod source:"
                + " mob gear (vanilla's and this mod's), party rewards, champion loot and villager trades."
                + " Diamond pieces become iron (enchantments kept). Player crafting is not affected.");

        e.finalTierRampDays = num(
            c,
            "finalTierRampDays",
            d.finalTierRampDays,
            0,
            1000,
            "Days the final tier (tier 5) takes to reach full strength.");

        e.extraNightSpawns = num(
            c,
            "tier1ExtraNightSpawns",
            d.extraNightSpawns,
            0,
            10,
            "Tier 1: extra hostile spawning at night. 0.625 = +62.5% hostile mob cap and +62.5% spawn attempts."
                + " Higher values cost more server time (more mobs, more spawn passes).");
        e.followRangeBonus = num(
            c,
            "tier1FollowRangeBonus",
            d.followRangeBonus,
            0,
            5,
            "Tier 1: extra follow/detection range. 0.5 = +50%.");

        e.healthBonus = num(c, "tier2HealthBonus", d.healthBonus, 0, 10, "Tier 2: extra max health. 0.2 = +20%.");
        e.damageBonus = num(c, "tier2DamageBonus", d.damageBonus, 0, 10, "Tier 2: extra attack damage. 0.2 = +20%.");
        e.doorBreakChance = num(
            c,
            "tier2DoorBreakChance",
            d.doorBreakChance,
            0,
            1,
            "Tier 2: chance (0-1) a new zombie can break wooden doors, on any difficulty. Needs gamerule mobGriefing.");

        e.armorChance = num(
            c,
            "tier3ArmorChance",
            d.armorChance,
            0,
            1,
            "Tier 3: chance (0-1) a new zombie or skeleton gets extra armor (not enchanted).");
        e.weaponChance = num(
            c,
            "tier3WeaponChance",
            d.weaponChance,
            0,
            1,
            "Tier 3: chance (0-1) a new zombie without a weapon gets an iron sword or shovel (not enchanted).");

        e.creeperRadiusBonus = num(
            c,
            "tier4CreeperRadiusBonus",
            d.creeperRadiusBonus,
            0,
            10,
            "Tier 4: extra creeper explosion radius. 0.6 = +60% (vanilla radius is 3).");
        e.creeperMaxRadius = num(
            c,
            "tier4CreeperMaxRadius",
            d.creeperMaxRadius,
            3,
            20,
            "Tier 4: creeper explosions are never made bigger than this radius, charged creepers included."
                + " 6 = a vanilla charged creeper. Vanilla explosions are never made smaller.");

        e.fullGearChance = num(
            c,
            "tier5FullGearChance",
            d.fullGearChance,
            0,
            1,
            "Tier 5: chance (0-1) a new zombie or skeleton gets full armor and enchanted gear.");
        e.fullGearEnchantLevel = c.getInt(
            "tier5EnchantLevel",
            CATEGORY_EFFECTS,
            d.fullGearEnchantLevel,
            1,
            30,
            "Tier 5: enchantment level for that gear (an enchanting table's maximum is 30).");
    }

    private static void loadParty(Configuration c) {
        final String P = CATEGORY_PARTY;
        c.setCategoryComment(
            P,
            "Friday Night Party: day 0 is a Sunday; every Friday (difficulty day % 7 == partyWeekday) a horde comes"
                + " for the player's home from dusk to sunrise. 'Week N' means Friday N (Friday 1 is day 5)."
                + " Times of day are in ticks: 0 = sunrise-ish, 6000 noon, 13000 dusk, 18000 midnight.");
        PartySettings d = new PartySettings();
        PartySettings p = party;

        p.enabled = c.getBoolean("enabled", P, d.enabled, "Turn the Friday Night Party on or off.");
        p.partyWeekday = c.getInt("partyWeekday", P, d.partyWeekday, 0, 6, "Party day: day % 7. 5 = Friday.");
        p.startTime = c.getInt("startTime", P, d.startTime, 0, 23999, "Time of day the party starts (dusk).");
        p.latestStartTime = c.getInt(
            "latestStartTime",
            P,
            d.latestStartTime,
            0,
            23999,
            "A missed party can still start until this time.");
        p.endTime = c.getInt("endTime", P, d.endTime, 0, 23999, "Time of day the party ends (sunrise).");
        p.blockSleep = c.getBoolean(
            "blockSleep",
            P,
            d.blockSleep,
            "Players can't sleep on party nights (from dusk on a party day), so the party can't be skipped.");

        p.baseSize = c.getInt("baseSize", P, d.baseSize, 1, 200, "Mobs on Friday 1.");
        p.sizePerWeek = c.getInt("sizePerWeek", P, d.sizePerWeek, 0, 100, "Extra mobs each week.");
        p.maxSize = c.getInt("maxSize", P, d.maxSize, 1, 200, "Most mobs in one party. Bigger costs server time.");
        p.minGroups = c.getInt("minGroups", P, d.minGroups, 1, 8, "Fewest groups (from different sides).");
        p.maxGroups = c.getInt("maxGroups", P, d.maxGroups, 1, 8, "Most groups.");
        p.mobsPerGroup = c.getInt(
            "mobsPerGroup",
            P,
            d.mobsPerGroup,
            1,
            50,
            "Groups = size / this, rounded up, within min..max groups.");

        p.extraTypeWeeks = ints(
            c,
            P,
            "extraTypeWeeks",
            d.extraTypeWeeks,
            "One more mob type joins the zombies on each of these Fridays."
                + " Types: skeletons, spiders, witches, creepers, endermen.");
        p.extraTypeShare = dbl(
            c,
            P,
            "extraTypeShare",
            d.extraTypeShare,
            0,
            1,
            "Share of the party for each extra type.");
        p.maxPerExtraType = c.getInt("maxPerExtraType", P, d.maxPerExtraType, 1, 100, "Most mobs of one extra type.");
        p.maxCreepers = c.getInt("maxCreepers", P, d.maxCreepers, 1, 100, "Most creepers in one party.");

        p.minSpawnDistance = dbl(c, P, "minSpawnDistance", d.minSpawnDistance, 8, 100, "Closest spawn to the target.");
        p.maxSpawnDistance = dbl(
            c,
            P,
            "maxSpawnDistance",
            d.maxSpawnDistance,
            8,
            100,
            "Farthest spawn from the target.");
        p.homeRange = dbl(
            c,
            P,
            "homeRange",
            d.homeRange,
            0,
            1000,
            "The party targets the player's bed (or world spawn without a bed) if the player is within this"
                + " distance of it; otherwise the player.");
        p.guestRange = dbl(
            c,
            P,
            "guestRange",
            d.guestRange,
            0,
            1000,
            "Players this close to the target when the party starts are guests and can earn the reward.");
        p.rallyDistance = dbl(
            c,
            P,
            "rallyDistance",
            d.rallyDistance,
            4,
            100,
            "Groups regroup this far from the target.");
        p.gatherFraction = dbl(
            c,
            P,
            "gatherFraction",
            d.gatherFraction,
            0,
            1,
            "All groups attack when this share of every group is at its rally point...");
        p.gatherMaxSeconds = c
            .getInt("gatherMaxSeconds", P, d.gatherMaxSeconds, 0, 600, "...or after this many seconds.");
        p.attackRange = dbl(
            c,
            P,
            "attackRange",
            d.attackRange,
            4,
            100,
            "Attacking mobs lock on to players this close.");
        p.maxSpawnBlockLight = c.getInt(
            "maxSpawnBlockLight",
            P,
            d.maxSpawnBlockLight,
            0,
            15,
            "Never spawn where torch light is above this.");
        p.groupSpawnIntervalTicks = c.getInt(
            "groupSpawnIntervalTicks",
            P,
            d.groupSpawnIntervalTicks,
            1,
            1200,
            "Ticks between groups spawning (spreads the work out).");
        p.spawnRetrySeconds = c.getInt(
            "spawnRetrySeconds",
            P,
            d.spawnRetrySeconds,
            1,
            600,
            "How long to keep looking for a hidden spawn spot before a group is skipped.");

        p.minerShare = dbl(c, P, "minerShare", d.minerShare, 0, 1, "Share of party zombies that are miners (pickaxe).");
        p.digWeeks = ints(c, P, "digWeeks", d.digWeeks, "Miner block lists 1, 2 and 3 open on these Fridays.");
        p.digTicksPerHardness = dbl(
            c,
            P,
            "digTicksPerHardness",
            d.digTicksPerHardness,
            1,
            1000,
            "Dig time in ticks = block hardness x this (dirt 0.5, stone 1.5, planks and cobblestone 2).");
        p.minDigTicks = c.getInt("minDigTicks", P, d.minDigTicks, 1, 1200, "Shortest dig time in ticks.");
        minerBlocks[0] = c
            .getStringList("minerBlocks1", P, DEFAULT_MINER_BLOCKS_1, "Miner block list 1 (block registry names).");
        minerBlocks[1] = c.getStringList("minerBlocks2", P, DEFAULT_MINER_BLOCKS_2, "Miner block list 2.");
        minerBlocks[2] = c.getStringList("minerBlocks3", P, DEFAULT_MINER_BLOCKS_3, "Miner block list 3.");

        p.builderWeek = c.getInt("builderWeek", P, d.builderWeek, 1, 10000, "Builders join from this Friday.");
        p.builderShare = dbl(c, P, "builderShare", d.builderShare, 0, 1, "Share of party zombies that are builders.");
        p.builderBlocks = c.getInt("builderBlocks", P, d.builderBlocks, 0, 1000, "Dirt blocks each builder carries.");
        p.maxPillarHeight = c
            .getInt("maxPillarHeight", P, d.maxPillarHeight, 0, 64, "Highest a builder pillars up in one go.");

        p.championWeek = c.getInt("championWeek", P, d.championWeek, 1, 10000, "Champions join from this Friday.");
        p.minChampions = c.getInt("minChampions", P, d.minChampions, 0, 50, "Fewest champions per party.");
        p.maxChampions = c.getInt("maxChampions", P, d.maxChampions, 0, 50, "Most champions per party.");
        p.minPowers = c.getInt("minPowers", P, d.minPowers, 1, 4, "Fewest powers per champion.");
        p.maxPowers = c.getInt("maxPowers", P, d.maxPowers, 1, 4, "Most powers per champion.");
        p.vengeanceFraction = dbl(
            c,
            P,
            "vengeanceFraction",
            d.vengeanceFraction,
            0,
            10,
            "Vengeance: share of melee damage reflected to the attacker.");
        p.webCooldownTicks = c
            .getInt("webCooldownTicks", P, d.webCooldownTicks, 20, 72000, "Webber: ticks between cobwebs.");
        p.webRange = dbl(c, P, "webRange", d.webRange, 1, 32, "Webber: places a web when its target is this close.");
        p.websVanishAtSunrise = c
            .getBoolean("websVanishAtSunrise", P, d.websVanishAtSunrise, "Remove the Webbers' cobwebs at sunrise.");
        p.bulwarkKnockbackResistance = dbl(
            c,
            P,
            "bulwarkKnockbackResistance",
            d.bulwarkKnockbackResistance,
            0,
            1,
            "Bulwark: knockback resistance added (plus permanent Resistance I).");
        p.championDropChance = (float) dbl(
            c,
            P,
            "championDropChance",
            d.championDropChance,
            0,
            1,
            "Champion: chance each piece of its equipment drops.");
        p.championLootRolls = c
            .getInt("championLootRolls", P, d.championLootRolls, 0, 64, "Champion: rolls on the reward loot table.");
        p.championXp = c.getInt("championXp", P, d.championXp, 0, 10000, "Champion: extra XP dropped.");

        p.rewardXpBase = c.getInt("rewardXpBase", P, d.rewardXpBase, 0, 100000, "Survival reward XP, plus...");
        p.rewardXpPerMob = c.getInt("rewardXpPerMob", P, d.rewardXpPerMob, 0, 10000, "...this much per party mob.");
        p.rewardRollsPerMob = dbl(
            c,
            P,
            "rewardRollsPerMob",
            d.rewardRollsPerMob,
            0,
            10,
            "Survival reward: loot rolls per party mob (rounded, at least 1).");
        String[] loot = c.getStringList(
            "rewardLoot",
            P,
            DEFAULT_REWARD_LOOT,
            "Loot table for the survival reward and champions: item,min,max,weight.");
        rewardLoot = LootTable.parse(loot);
        for (String bad : rewardLoot.errors()) {
            SleeplessNights.LOG.warn("Ignoring bad rewardLoot line: {}", bad);
        }

        p.repairEnabled = c.getBoolean(
            "repairEnabled",
            P,
            d.repairEnabled,
            "Blocks party mobs dig, place or blow up come back after repairMinutes, if nobody changed them."
                + " While on, dug blocks don't drop items and party creepers don't break chests and other"
                + " tile entities (no duplication).");
        p.repairMinutes = dbl(
            c,
            P,
            "repairMinutes",
            d.repairMinutes,
            0.1,
            10000,
            "Minutes before party damage repairs.");
    }

    private static void loadVillages(Configuration c) {
        final String V = CATEGORY_VILLAGES;
        c.setCategoryComment(V, "Villages: iron golems and villagers.");
        VillageSettings d = new VillageSettings();
        VillageSettings v = villages;
        v.golemSpawnMultiplier = dbl(
            c,
            V,
            "golemSpawnMultiplier",
            d.golemSpawnMultiplier,
            0,
            20,
            "Iron golems spawn this many times as often as in vanilla (vanilla's own rules still apply).");
        v.golemHealthPerTier = dbl(
            c,
            V,
            "golemHealthPerTier",
            d.golemHealthPerTier,
            0,
            10,
            "Extra golem max health per tier, growing smoothly with the tiers. 0.15 = +15% per tier.");
        v.golemDamagePerTier = dbl(
            c,
            V,
            "golemDamagePerTier",
            d.golemDamagePerTier,
            0,
            10,
            "Extra golem damage per tier.");
        v.extraGolemDay = c.getInt("extraGolemDay", V, d.extraGolemDay, 0, 100000, "From this day, villages get...");
        v.extraGolems = c.getInt(
            "extraGolems",
            V,
            d.extraGolems,
            0,
            10,
            "...this many golems above vanilla's cap (any village with villagers, however many doors).");
        v.earlyIndoorsDay = c
            .getInt("earlyIndoorsDay", V, d.earlyIndoorsDay, 0, 100000, "From this day, villagers go indoors...");
        v.earlyIndoorsTime = c.getInt(
            "earlyIndoorsTime",
            V,
            d.earlyIndoorsTime,
            0,
            23999,
            "...from this time of day (vanilla: at dusk, about 12900).");
    }

    private static double num(Configuration c, String key, double def, double min, double max, String comment) {
        return dbl(c, CATEGORY_EFFECTS, key, def, min, max, comment);
    }

    private static double dbl(Configuration c, String cat, String key, double def, double min, double max,
        String comment) {
        double v = c.get(cat, key, def, comment, min, max)
            .getDouble(def);
        return Math.max(min, Math.min(max, v));
    }

    /** Positive, sorted list of week numbers; falls back to the default if empty. */
    private static int[] ints(Configuration c, String cat, String key, int[] def, String comment) {
        int[] raw = c.get(cat, key, def, comment)
            .getIntList();
        int[] clean = java.util.Arrays.stream(raw)
            .filter(w -> w >= 1)
            .sorted()
            .toArray();
        return clean.length == 0 ? def.clone() : clean;
    }

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        double multiplier = configuration
            .get(
                CATEGORY_DIFFICULTY,
                "daySpeedMultiplier",
                1.0,
                "How fast the difficulty day advances compared to Minecraft days. 1.0 = normal (one per 20 minutes)."
                    + " For testing, e.g. 60 makes one difficulty day pass every 20 seconds.",
                0.01,
                1000.0)
            .getDouble(1.0);
        daySpeedMultiplier = Math.max(0.01, Math.min(1000.0, multiplier));

        int[] starts = configuration
            .get(
                CATEGORY_DIFFICULTY,
                "tierStartDays",
                DifficultyClock.DEFAULT_TIER_STARTS,
                "The difficulty day on which each tier begins, in increasing order.")
            .getIntList();
        tierStartDays = DifficultyClock.validateTierStarts(starts);

        tierChatMessages = configuration.getBoolean(
            "tierChatMessages",
            CATEGORY_DIFFICULTY,
            true,
            "Send a chat message to all players when a new tier begins.");

        debugLogging = configuration.getBoolean(
            "debugLogging",
            CATEGORY_DIFFICULTY,
            false,
            "Write a log line for every mob the mod changes (for testing).");

        loadEffects(configuration);
        loadParty(configuration);
        loadVillages(configuration);

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}

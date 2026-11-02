package com.sansslonce.sleepless100.core;

/**
 * Every number of the Friday Night Party. Plain Java (no Minecraft imports); the Minecraft side fills it from the
 * config file. "Week N" means Friday number N (Friday 1 is day 5).
 */
public final class PartySettings {

    public boolean enabled = true;

    // Calendar (day 0 is a Sunday)
    /** The party happens when difficulty day % 7 equals this (5 = Friday). */
    public int partyWeekday = 5;
    /** Time of day (0-23999) when the party starts: dusk. */
    public int startTime = 13000;
    /** The party can still start until this time of day (e.g. after /sleepless setday). */
    public int latestStartTime = 18000;
    /** Time of day when the party ends: sunrise. */
    public int endTime = 23000;
    /** Block sleeping on party nights, so the party can't be skipped. */
    public boolean blockSleep = true;

    // Size and groups
    public int baseSize = 4;
    public int sizePerWeek = 2;
    public int maxSize = 30;
    public int minGroups = 2;
    public int maxGroups = 4;
    /** Group count = party size / this, rounded up, then limited to minGroups..maxGroups. */
    public int mobsPerGroup = 5;

    // Mob types
    /** Number of extra mob types grows by one on each of these Fridays. */
    public int[] extraTypeWeeks = { 3, 6, 10 };
    /** Share of the party for each extra type. */
    public double extraTypeShare = 0.15;
    public int maxPerExtraType = 4;
    public int maxCreepers = 2;

    // Placement and movement
    public double minSpawnDistance = 24;
    public double maxSpawnDistance = 40;
    /** Target the host's bed/spawn point if the host is within this distance of it, else the host. */
    public double homeRange = 48;
    /** Players within this distance of the target at the start are guests. */
    public double guestRange = 64;
    public double rallyDistance = 18;
    /** Groups attack when this share of each group is at its rally point... */
    public double gatherFraction = 0.75;
    /** ...or when this many seconds have passed. */
    public int gatherMaxSeconds = 40;
    /** Attacking mobs lock on to a guest within this distance. */
    public double attackRange = 24;
    public int maxSpawnBlockLight = 7;
    public int groupSpawnIntervalTicks = 40;
    public int spawnRetrySeconds = 30;

    // Miners
    public double minerShare = 0.3;
    /** Miners can dig block list N from Friday digWeeks[N-1]. */
    public int[] digWeeks = { 1, 4, 8 };
    public double digTicksPerHardness = 60;
    public int minDigTicks = 20;

    // Builders
    public int builderWeek = 5;
    public double builderShare = 0.2;
    public int builderBlocks = 16;
    public int maxPillarHeight = 6;

    // Champions
    public int championWeek = 8;
    public int minChampions = 1;
    public int maxChampions = 2;
    public int minPowers = 1;
    public int maxPowers = 2;
    public double vengeanceFraction = 0.3;
    public int webCooldownTicks = 200;
    public double webRange = 6;
    public boolean websVanishAtSunrise = true;
    public double bulwarkKnockbackResistance = 0.5;
    public float championDropChance = 0.5F;
    public int championLootRolls = 3;
    public int championXp = 30;

    // Reward
    public int rewardXpBase = 10;
    public int rewardXpPerMob = 4;
    public double rewardRollsPerMob = 0.3;

    // Repair
    public boolean repairEnabled = false;
    public double repairMinutes = 10;
}

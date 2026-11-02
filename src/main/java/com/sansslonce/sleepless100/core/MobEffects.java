package com.sansslonce.sleepless100.core;

/**
 * Effect strengths for a given difficulty day. Plain Java (no Minecraft imports).
 * <p>
 * Each tier's effects start at 0 on that tier's start day and grow linearly to full strength by the
 * next tier's start day (the final tier ramps over {@link EffectSettings#finalTierRampDays}). Effects
 * then stay at full strength, so every tier adds to the previous ones and nothing jumps suddenly.
 */
public final class MobEffects {

    public static final int TIER_SPAWNS_AND_RANGE = 1;
    public static final int TIER_HEALTH_DAMAGE_DOORS = 2;
    public static final int TIER_ARMOR_WEAPONS = 3;
    public static final int TIER_CREEPERS = 4;
    public static final int TIER_FULL_GEAR = 5;

    /** Vanilla creeper explosion radius. */
    public static final double VANILLA_CREEPER_RADIUS = 3.0;

    public final double extraNightSpawns;
    public final double followRangeBonus;
    public final double healthBonus;
    public final double damageBonus;
    public final double doorBreakChance;
    public final double armorChance;
    public final double weaponChance;
    /** Multiplier for creeper explosion size (before the cap). */
    public final double creeperRadiusScale;
    public final double creeperMaxRadius;
    public final double fullGearChance;
    public final int fullGearEnchantLevel;
    /** False before {@link EffectSettings#diamondMinDay}: diamond armor and weapons become iron. */
    public final boolean diamondAllowed;

    private MobEffects(double day, int[] tierStarts, EffectSettings s) {
        double t1 = ramp(day, TIER_SPAWNS_AND_RANGE, tierStarts, s.finalTierRampDays);
        double t2 = ramp(day, TIER_HEALTH_DAMAGE_DOORS, tierStarts, s.finalTierRampDays);
        double t3 = ramp(day, TIER_ARMOR_WEAPONS, tierStarts, s.finalTierRampDays);
        double t4 = ramp(day, TIER_CREEPERS, tierStarts, s.finalTierRampDays);
        double t5 = ramp(day, TIER_FULL_GEAR, tierStarts, s.finalTierRampDays);

        extraNightSpawns = t1 * nonNegative(s.extraNightSpawns);
        followRangeBonus = t1 * nonNegative(s.followRangeBonus);
        healthBonus = t2 * nonNegative(s.healthBonus);
        damageBonus = t2 * nonNegative(s.damageBonus);
        doorBreakChance = t2 * chance(s.doorBreakChance);
        armorChance = t3 * chance(s.armorChance);
        weaponChance = t3 * chance(s.weaponChance);
        creeperRadiusScale = 1.0 + t4 * nonNegative(s.creeperRadiusBonus);
        creeperMaxRadius = Math.max(VANILLA_CREEPER_RADIUS, s.creeperMaxRadius);
        fullGearChance = t5 * chance(s.fullGearChance);
        fullGearEnchantLevel = Math.max(1, s.fullGearEnchantLevel);
        diamondAllowed = diamondAllowed(day, s);
    }

    public static MobEffects forDay(double day, int[] tierStarts, EffectSettings settings) {
        return new MobEffects(day, tierStarts, settings);
    }

    /**
     * How far a tier has ramped up, from 0.0 (before or on its start day) to 1.0 (fully grown).
     * Returns 0 for tiers that are not configured.
     */
    public static double ramp(double day, int tier, int[] tierStarts, double finalTierRampDays) {
        if (tier < 1 || tier > tierStarts.length) {
            return 0.0;
        }
        double start = tierStarts[tier - 1];
        double end = tier < tierStarts.length ? tierStarts[tier] : start + finalTierRampDays;
        if (day <= start) {
            return 0.0;
        }
        if (end <= start || day >= end) {
            return 1.0;
        }
        return (day - start) / (end - start);
    }

    public static boolean diamondAllowed(double day, EffectSettings s) {
        return day >= s.diamondMinDay;
    }

    /**
     * Creeper explosion radius after scaling, capped at the configured maximum (never made smaller).
     * {@code baseRadius} is the final vanilla size, so a charged creeper arrives here already doubled (6)
     * and the scale cannot push it past the cap.
     */
    public static double creeperRadius(double baseRadius, double scale, double maxRadius) {
        if (scale <= 1.0 || baseRadius >= maxRadius) {
            return baseRadius;
        }
        return Math.min(baseRadius * scale, maxRadius);
    }

    /**
     * Turns a fractional amount of extra spawn passes into a whole number for one tick:
     * e.g. 0.3 gives one extra pass with probability 0.3. {@code random} must be in [0, 1).
     */
    public static int extraPassesThisTick(double extra, double random) {
        int whole = (int) Math.floor(extra);
        return whole + (random < extra - whole ? 1 : 0);
    }

    /** Raised hostile mob cap: vanilla cap × (1 + extra). */
    public static int raisedMobCap(int vanillaCap, double extra) {
        return (int) Math.floor(vanillaCap * (1.0 + extra));
    }

    private static double nonNegative(double v) {
        return Math.max(0.0, v);
    }

    private static double chance(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

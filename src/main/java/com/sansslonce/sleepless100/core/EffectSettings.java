package com.sansslonce.sleepless100.core;

/**
 * Strength of every mob effect once its tier has fully ramped up. Plain Java (no Minecraft imports);
 * the Minecraft side fills it from the config file.
 */
public final class EffectSettings {

    /** Days the final tier takes to ramp from 0 to full strength (other tiers ramp until the next tier starts). */
    public double finalTierRampDays = 25.0;

    // Tier 1
    /** Extra night spawning at full strength: 0.625 = +62.5% hostile mob cap and +62.5% spawn attempts. */
    public double extraNightSpawns = 0.625;
    /** Extra follow/detection range at full strength: 0.5 = +50%. */
    public double followRangeBonus = 0.5;

    // Tier 2
    /** Extra max health at full strength: 0.2 = +20%. */
    public double healthBonus = 0.2;
    /** Extra attack damage at full strength: 0.2 = +20%. */
    public double damageBonus = 0.2;
    /** Chance a new zombie can break wooden doors (on any difficulty) at full strength. */
    public double doorBreakChance = 1.0;

    // Tier 3
    /** Chance a new zombie/skeleton gets extra (unenchanted) armor at full strength. */
    public double armorChance = 0.35;
    /** Chance a new zombie without a weapon gets an iron sword or shovel at full strength. */
    public double weaponChance = 0.25;

    // Tier 4
    /** Extra creeper explosion radius at full strength: 0.6 = +60%. */
    public double creeperRadiusBonus = 0.6;
    /**
     * Creeper explosion radius never exceeds this, charged creepers included (the cap applies after vanilla's
     * doubling). 6 = a vanilla charged creeper, so a tier-4 charged creeper stays at 6 instead of 6 × scale.
     */
    public double creeperMaxRadius = 6.0;

    // Tier 5
    /** Chance a new zombie/skeleton gets full armor and enchanted gear at full strength. */
    public double fullGearChance = 0.15;
    /** Enchantment level used for tier-5 gear (vanilla enchanting table maximum is 30). */
    public int fullGearEnchantLevel = 20;
}

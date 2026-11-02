package com.sansslonce.sleepless100;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.sansslonce.sleepless100.core.DifficultyClock;
import com.sansslonce.sleepless100.core.EffectSettings;

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

    private static void loadEffects(Configuration c) {
        c.setCategoryComment(
            CATEGORY_EFFECTS,
            "Mob effects. Each value is the FULL strength, reached at the end of its tier's ramp:"
                + " an effect starts at 0 on its tier's start day and grows smoothly until the next tier starts."
                + " Effects only apply to newly spawned mobs. Movement speed is never changed.");
        EffectSettings d = new EffectSettings();
        EffectSettings e = effects;

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

    private static double num(Configuration c, String key, double def, double min, double max, String comment) {
        double v = c.get(CATEGORY_EFFECTS, key, def, comment, min, max)
            .getDouble(def);
        return Math.max(min, Math.min(max, v));
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

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}

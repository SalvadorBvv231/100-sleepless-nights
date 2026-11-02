package com.sansslonce.sleepless100.core;

/** Village rules: golem spawning and strength, villager bedtime. Plain Java (no Minecraft imports). */
public final class VillageRules {

    /** Vanilla: a village rolls 1 in 7000 each tick to spawn a golem. */
    public static final double VANILLA_GOLEM_CHANCE_PER_TICK = 1.0 / 7000.0;
    /** Vanilla: one golem per 10 villagers... */
    public static final int VILLAGERS_PER_GOLEM = 10;
    /** ...and only in villages with more than 20 doors. */
    public static final int VANILLA_MIN_DOORS = 21;
    /** Vanilla villagers stop going indoors around this time of day (dawn). */
    public static final int DAWN_TIME = 23000;

    private VillageRules() {}

    /** Sum of all tier ramps (0 before tier 1, up to the tier count when everything is fully grown). Smooth. */
    public static double tierProgress(double day, int[] tierStarts, double finalTierRampDays) {
        double sum = 0;
        for (int tier = 1; tier <= tierStarts.length; tier++) {
            sum += MobEffects.ramp(day, tier, tierStarts, finalTierRampDays);
        }
        return sum;
    }

    public static double golemHealthBonus(double tierProgress, VillageSettings s) {
        return Math.max(0, tierProgress * s.golemHealthPerTier);
    }

    public static double golemDamageMultiplier(double tierProgress, VillageSettings s) {
        return 1.0 + Math.max(0, tierProgress * s.golemDamagePerTier);
    }

    public static int vanillaGolemCap(int villagers) {
        return villagers / VILLAGERS_PER_GOLEM;
    }

    /**
     * Chance per tick that this mod spawns a golem in a village, on top of vanilla's own roll.
     * <ul>
     * <li>Below vanilla's cap (and with vanilla's door rule): the extra share of the multiplier, (m - 1) / 7000.</li>
     * <li>In the extra slot from {@code extraGolemDay}: the full m / 7000, since vanilla never spawns there.
     * Any village with villagers counts, however many doors it has.</li>
     * </ul>
     */
    public static double extraSpawnChancePerTick(int golems, int villagers, int doors, double day, VillageSettings s) {
        double m = Math.max(0, s.golemSpawnMultiplier);
        // Vanilla never spawns golems in villages with too few doors, so its cap is effectively 0 there.
        int vanillaCap = doors >= VANILLA_MIN_DOORS ? vanillaGolemCap(villagers) : 0;
        if (golems < vanillaCap) {
            return Math.max(0, m - 1) * VANILLA_GOLEM_CHANCE_PER_TICK;
        }
        int extra = day >= s.extraGolemDay ? Math.max(0, s.extraGolems) : 0;
        if (villagers > 0 && golems < vanillaCap + extra) {
            return m * VANILLA_GOLEM_CHANCE_PER_TICK;
        }
        return 0;
    }

    /** True when villagers should already be heading indoors, before vanilla's own dusk check kicks in. */
    public static boolean goIndoorsEarly(double day, int timeOfDay, VillageSettings s) {
        return day >= s.earlyIndoorsDay && timeOfDay >= s.earlyIndoorsTime && timeOfDay < DAWN_TIME;
    }
}

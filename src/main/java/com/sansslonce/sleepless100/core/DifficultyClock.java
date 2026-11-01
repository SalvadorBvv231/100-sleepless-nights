package com.sansslonce.sleepless100.core;

import java.util.Arrays;

/**
 * Pure day/tier math for 100 Sleepless Nights.
 * <p>
 * This class must not import anything from Minecraft or Forge, so it can be reused unchanged
 * when the mod is ported to other Minecraft versions.
 */
public final class DifficultyClock {

    /** Length of one Minecraft day in ticks. */
    public static final long TICKS_PER_DAY = 24000L;

    /** Real-world ticks per second when the server runs at full speed. */
    public static final int TICKS_PER_SECOND = 20;

    /** Tier start days used when the config gives nothing usable. */
    public static final int[] DEFAULT_TIER_STARTS = { 10, 25, 50, 75, 100 };

    /**
     * Tolerance for floating-point rounding, so that "/sleepless setday 10" really lands in the
     * day-10 tier even if the sum comes out as 9.9999999999.
     */
    private static final double EPSILON = 1e-6;

    private DifficultyClock() {}

    /** day = totalTicks / 24000 * multiplier + offset (never below 0). */
    public static double computeDay(long totalTicks, double multiplier, double offset) {
        double day = (double) totalTicks / TICKS_PER_DAY * multiplier + offset;
        return Math.max(0.0, day);
    }

    /** The offset that makes {@link #computeDay} return {@code targetDay} right now. */
    public static double offsetForDay(double targetDay, long totalTicks, double multiplier) {
        return targetDay - (double) totalTicks / TICKS_PER_DAY * multiplier;
    }

    /**
     * Current tier: 0 before the first start day, 1 after the first, and so on.
     * {@code tierStarts} must be sorted ascending (see {@link #validateTierStarts}).
     */
    public static int tierForDay(double day, int[] tierStarts) {
        int tier = 0;
        for (int start : tierStarts) {
            if (start <= day + EPSILON) {
                tier++;
            } else {
                break;
            }
        }
        return tier;
    }

    /** Start day of the next tier, or -1 if {@code day} is already in the final tier. */
    public static int nextTierStartDay(double day, int[] tierStarts) {
        int tier = tierForDay(day, tierStarts);
        return tier < tierStarts.length ? tierStarts[tier] : -1;
    }

    /** Start day of the given tier (1-based), or 0 for tier 0. */
    public static int tierStartDay(int tier, int[] tierStarts) {
        if (tier <= 0 || tierStarts.length == 0) {
            return 0;
        }
        return tierStarts[Math.min(tier, tierStarts.length) - 1];
    }

    /** Game ticks until {@code targetDay} is reached at the given speed multiplier (0 if already reached). */
    public static long ticksUntilDay(double targetDay, double currentDay, double multiplier) {
        if (targetDay <= currentDay + EPSILON || multiplier <= 0) {
            return 0;
        }
        return (long) Math.ceil((targetDay - currentDay) * TICKS_PER_DAY / multiplier);
    }

    /** Day rounded down to one decimal for display, so 9.99 shows as 9.9 (not 10.0) while still in tier 0. */
    public static double displayDay(double day) {
        return Math.floor(day * 10 + EPSILON) / 10.0;
    }

    /**
     * Days remaining rounded up to one decimal for display, so it always matches {@link #displayDay}:
     * at day 0.09 (shown as 0.0) the next tier at day 10 is "10.0 days" away, not 9.9.
     */
    public static double displayDaysLeft(double daysLeft) {
        return Math.ceil(daysLeft * 10 - EPSILON) / 10.0;
    }

    /** Sorts ascending, removes negatives and duplicates; falls back to the defaults if nothing is left. */
    public static int[] validateTierStarts(int[] raw) {
        if (raw == null) {
            return DEFAULT_TIER_STARTS.clone();
        }
        int[] sorted = Arrays.stream(raw)
            .filter(d -> d >= 0)
            .distinct()
            .sorted()
            .toArray();
        return sorted.length == 0 ? DEFAULT_TIER_STARTS.clone() : sorted;
    }
}

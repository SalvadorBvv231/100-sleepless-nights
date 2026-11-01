package com.sansslonce.sleepless100.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DifficultyClockTest {

    private static final int[] TIERS = { 10, 25, 50, 75, 100 };
    private static final long DAY = DifficultyClock.TICKS_PER_DAY;

    @Test
    void dayFollowsWorldTime() {
        assertEquals(0.0, DifficultyClock.computeDay(0, 1.0, 0), 1e-9);
        assertEquals(1.5, DifficultyClock.computeDay(DAY + DAY / 2, 1.0, 0), 1e-9);
    }

    @Test
    void multiplierAndOffsetApply() {
        assertEquals(20.0, DifficultyClock.computeDay(2 * DAY, 10.0, 0), 1e-9);
        assertEquals(7.0, DifficultyClock.computeDay(2 * DAY, 1.0, 5.0), 1e-9);
    }

    @Test
    void dayNeverNegative() {
        assertEquals(0.0, DifficultyClock.computeDay(DAY, 1.0, -50.0), 1e-9);
    }

    @Test
    void offsetForDayHitsTargetExactly() {
        long ticks = 123_457;
        for (double multiplier : new double[] { 0.01, 1.0, 3.7, 60.0, 1000.0 }) {
            for (double target : new double[] { 0, 9.99, 10, 25, 100, 250.5 }) {
                double offset = DifficultyClock.offsetForDay(target, ticks, multiplier);
                double day = DifficultyClock.computeDay(ticks, multiplier, offset);
                assertEquals(target, day, 1e-6);
                assertEquals(
                    DifficultyClock.tierForDay(target, TIERS),
                    DifficultyClock.tierForDay(day, TIERS),
                    "tier for target " + target + " at x" + multiplier);
            }
        }
    }

    @Test
    void tierBoundaries() {
        assertEquals(0, DifficultyClock.tierForDay(0, TIERS));
        assertEquals(0, DifficultyClock.tierForDay(9.99, TIERS));
        assertEquals(1, DifficultyClock.tierForDay(10, TIERS));
        assertEquals(1, DifficultyClock.tierForDay(9.99999999999, TIERS));
        assertEquals(2, DifficultyClock.tierForDay(25, TIERS));
        assertEquals(4, DifficultyClock.tierForDay(99.9, TIERS));
        assertEquals(5, DifficultyClock.tierForDay(100, TIERS));
        assertEquals(5, DifficultyClock.tierForDay(5000, TIERS));
    }

    @Test
    void nextTier() {
        assertEquals(10, DifficultyClock.nextTierStartDay(0, TIERS));
        assertEquals(25, DifficultyClock.nextTierStartDay(10, TIERS));
        assertEquals(100, DifficultyClock.nextTierStartDay(80, TIERS));
        assertEquals(-1, DifficultyClock.nextTierStartDay(100, TIERS));
    }

    @Test
    void tierStartDay() {
        assertEquals(0, DifficultyClock.tierStartDay(0, TIERS));
        assertEquals(10, DifficultyClock.tierStartDay(1, TIERS));
        assertEquals(100, DifficultyClock.tierStartDay(5, TIERS));
    }

    @Test
    void ticksUntilDay() {
        assertEquals(10 * DAY, DifficultyClock.ticksUntilDay(10, 0, 1.0));
        assertEquals(DAY / 10, DifficultyClock.ticksUntilDay(10, 9, 10.0));
        assertEquals(0, DifficultyClock.ticksUntilDay(10, 12, 1.0));
    }

    @Test
    void displayDayRoundsDown() {
        assertEquals(9.9, DifficultyClock.displayDay(9.99), 1e-9);
        assertEquals(10.0, DifficultyClock.displayDay(9.99999999999), 1e-9);
        assertEquals(0.0, DifficultyClock.displayDay(0), 1e-9);
    }

    @Test
    void displayedDayPlusDaysLeftMatchesNextTier() {
        for (double day : new double[] { 0, 0.09, 0.1, 3.33, 9.95, 24.99 }) {
            int next = DifficultyClock.nextTierStartDay(day, TIERS);
            double left = (double) DifficultyClock.ticksUntilDay(next, day, 1.0) / DAY;
            assertEquals(
                next,
                DifficultyClock.displayDay(day) + DifficultyClock.displayDaysLeft(left),
                1e-9,
                "day " + day);
        }
    }

    @Test
    void validateTierStarts() {
        assertArrayEquals(new int[] { 5, 10, 30 }, DifficultyClock.validateTierStarts(new int[] { 30, -1, 10, 5, 10 }));
        assertArrayEquals(DifficultyClock.DEFAULT_TIER_STARTS, DifficultyClock.validateTierStarts(new int[0]));
        assertArrayEquals(DifficultyClock.DEFAULT_TIER_STARTS, DifficultyClock.validateTierStarts(null));
    }
}

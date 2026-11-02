package com.sansslonce.sleepless100.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MobEffectsTest {

    private static final int[] TIERS = { 10, 25, 50, 75, 100 };
    private static final EffectSettings S = new EffectSettings();

    private static MobEffects at(double day) {
        return MobEffects.forDay(day, TIERS, S);
    }

    @Test
    void nothingBeforeDay10() {
        MobEffects fx = at(9.99);
        assertEquals(0, fx.extraNightSpawns, 1e-9);
        assertEquals(0, fx.followRangeBonus, 1e-9);
        assertEquals(0, fx.healthBonus, 1e-9);
        assertEquals(0, fx.doorBreakChance, 1e-9);
        assertEquals(0, fx.armorChance, 1e-9);
        assertEquals(1.0, fx.creeperRadiusScale, 1e-9);
        assertEquals(0, fx.fullGearChance, 1e-9);
    }

    @Test
    void rampIsLinearWithinTier() {
        // Tier 1 runs from day 10 to day 25.
        assertEquals(0.0, MobEffects.ramp(10, 1, TIERS, 25), 1e-9);
        assertEquals(0.5, MobEffects.ramp(17.5, 1, TIERS, 25), 1e-9);
        assertEquals(1.0, MobEffects.ramp(25, 1, TIERS, 25), 1e-9);
        assertEquals(1.0, MobEffects.ramp(500, 1, TIERS, 25), 1e-9);
        // Final tier ramps over finalTierRampDays.
        assertEquals(0.4, MobEffects.ramp(110, 5, TIERS, 25), 1e-9);
        assertEquals(1.0, MobEffects.ramp(125, 5, TIERS, 25), 1e-9);
    }

    @Test
    void noSuddenJumps() {
        // Moving forward by 0.01 days never changes any effect by more than a tiny amount.
        for (double day = 0; day < 140; day += 0.01) {
            MobEffects a = at(day), b = at(day + 0.01);
            assertTrue(Math.abs(a.extraNightSpawns - b.extraNightSpawns) < 0.001, "spawns at " + day);
            assertTrue(Math.abs(a.healthBonus - b.healthBonus) < 0.001, "health at " + day);
            assertTrue(Math.abs(a.armorChance - b.armorChance) < 0.001, "armor at " + day);
            assertTrue(Math.abs(a.creeperRadiusScale - b.creeperRadiusScale) < 0.001, "creeper at " + day);
            assertTrue(Math.abs(a.fullGearChance - b.fullGearChance) < 0.001, "gear at " + day);
        }
    }

    @Test
    void tiersAddUp() {
        MobEffects fx = at(200);
        assertEquals(S.extraNightSpawns, fx.extraNightSpawns, 1e-9);
        assertEquals(S.healthBonus, fx.healthBonus, 1e-9);
        assertEquals(S.armorChance, fx.armorChance, 1e-9);
        assertEquals(1 + S.creeperRadiusBonus, fx.creeperRadiusScale, 1e-9);
        assertEquals(S.fullGearChance, fx.fullGearChance, 1e-9);
    }

    @Test
    void tierOrder() {
        MobEffects fx = at(30); // tier 2, partly ramped
        assertEquals(S.extraNightSpawns, fx.extraNightSpawns, 1e-9); // tier 1 complete
        assertEquals(0.2 * S.healthBonus, fx.healthBonus, 1e-9); // 5 of 25 days into tier 2
        assertEquals(0, fx.armorChance, 1e-9); // tier 3 not started
    }

    @Test
    void creeperRadiusIsCapped() {
        double cap = S.creeperMaxRadius;
        assertEquals(6.0, cap, 1e-9); // default cap = vanilla charged creeper
        assertEquals(3.0, MobEffects.creeperRadius(3.0, 1.0, cap), 1e-9);
        assertEquals(4.5, MobEffects.creeperRadius(3.0, 1.5, cap), 1e-9);
        assertEquals(4.8, MobEffects.creeperRadius(3.0, 1.6, cap), 1e-9); // default full strength, under the cap
        assertEquals(6.0, MobEffects.creeperRadius(3.0, 3.0, cap), 1e-9);
        // Charged creeper: vanilla already doubled it to 6; the scale does not stack on top (would be 9.6).
        assertEquals(6.0, MobEffects.creeperRadius(6.0, 1.6, cap), 1e-9);
        assertEquals(6.0, MobEffects.creeperRadius(6.0, 10.0, cap), 1e-9);
        // A higher configured cap still bounds charged creepers.
        assertEquals(8.0, MobEffects.creeperRadius(6.0, 1.6, 8.0), 1e-9);
        // Vanilla explosions above a low cap are left unchanged, never shrunk.
        assertEquals(6.0, MobEffects.creeperRadius(6.0, 1.6, 5.0), 1e-9);
    }

    @Test
    void missingTiersDoNothing() {
        MobEffects fx = MobEffects.forDay(500, new int[] { 10, 25 }, S);
        assertEquals(S.healthBonus, fx.healthBonus, 1e-9);
        assertEquals(0, fx.armorChance, 1e-9);
        assertEquals(1.0, fx.creeperRadiusScale, 1e-9);
        assertEquals(0, fx.fullGearChance, 1e-9);
    }

    @Test
    void extraPassesAndCap() {
        assertEquals(0, MobEffects.extraPassesThisTick(0.3, 0.5));
        assertEquals(1, MobEffects.extraPassesThisTick(0.3, 0.1));
        assertEquals(1, MobEffects.extraPassesThisTick(1.0, 0.9));
        assertEquals(2, MobEffects.extraPassesThisTick(1.5, 0.2));
        assertEquals(70, MobEffects.raisedMobCap(70, 0));
        assertEquals(105, MobEffects.raisedMobCap(70, 0.5));
        assertEquals(113, MobEffects.raisedMobCap(70, S.extraNightSpawns)); // default +62.5%
    }
}

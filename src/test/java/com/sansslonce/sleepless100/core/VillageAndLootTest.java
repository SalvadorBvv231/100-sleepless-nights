package com.sansslonce.sleepless100.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class VillageAndLootTest {

    private static final int[] TIERS = { 10, 25, 50, 75, 100 };
    private static final VillageSettings V = new VillageSettings();
    private static final double P = VillageRules.VANILLA_GOLEM_CHANCE_PER_TICK;

    @Test
    void golemStrengthRampsWithTiers() {
        assertEquals(0, VillageRules.tierProgress(5, TIERS, 25), 1e-9);
        assertEquals(1, VillageRules.tierProgress(25, TIERS, 25), 1e-9);
        assertEquals(5, VillageRules.tierProgress(500, TIERS, 25), 1e-9);
        assertEquals(0.75, VillageRules.golemHealthBonus(5, V), 1e-9);
        assertEquals(1.15, VillageRules.golemDamageMultiplier(1, V), 1e-9);
        assertEquals(1.0, VillageRules.golemDamageMultiplier(0, V), 1e-9);
    }

    @Test
    void golemSpawnChance() {
        // Below vanilla's cap with enough doors: only the extra half of the 1.5x multiplier.
        assertEquals(0.5 * P, VillageRules.extraSpawnChancePerTick(0, 20, 25, 10, V), 1e-12);
        // Few doors before day 50: vanilla rules, nothing extra.
        assertEquals(0, VillageRules.extraSpawnChancePerTick(0, 20, 10, 10, V), 1e-12);
        // At vanilla's cap before day 50: nothing.
        assertEquals(0, VillageRules.extraSpawnChancePerTick(2, 20, 25, 49, V), 1e-12);
        // From day 50 the extra slot spawns at the full multiplied rate, any door count.
        assertEquals(1.5 * P, VillageRules.extraSpawnChancePerTick(2, 20, 25, 50, V), 1e-12);
        assertEquals(1.5 * P, VillageRules.extraSpawnChancePerTick(0, 3, 4, 60, V), 1e-12);
        assertEquals(0, VillageRules.extraSpawnChancePerTick(1, 3, 4, 60, V), 1e-12);
        // No villagers: nothing.
        assertEquals(0, VillageRules.extraSpawnChancePerTick(0, 0, 30, 60, V), 1e-12);
    }

    @Test
    void villagersGoInEarly() {
        assertFalse(VillageRules.goIndoorsEarly(99, 12000, V));
        assertTrue(VillageRules.goIndoorsEarly(100, 11000, V));
        assertFalse(VillageRules.goIndoorsEarly(100, 10999, V));
        assertFalse(VillageRules.goIndoorsEarly(100, 23500, V));
    }

    @Test
    void diamondRule() {
        EffectSettings s = new EffectSettings();
        assertFalse(MobEffects.diamondAllowed(149.9, s));
        assertTrue(MobEffects.diamondAllowed(150, s));
        assertFalse(MobEffects.forDay(120, TIERS, s).diamondAllowed);
    }

    @Test
    void lootParsing() {
        LootTable t = LootTable
            .parse(new String[] { "minecraft:bread, 2, 4, 10", "bad line", "minecraft:apple,3,1,5", "", "x:y,1,1,0" });
        assertEquals(
            1,
            t.entries()
                .size());
        assertEquals(
            3,
            t.errors()
                .size());
        LootTable.Drop d = t.roll(new Random(3));
        assertNotNull(d);
        assertEquals("minecraft:bread", d.item);
        assertTrue(d.count >= 2 && d.count <= 4);
        assertNull(
            LootTable.parse(new String[0])
                .roll(new Random()));
    }

    @Test
    void lootWeights() {
        LootTable t = LootTable.parse(new String[] { "a,1,1,9", "b,1,1,1" });
        Random r = new Random(11);
        int a = 0;
        for (int i = 0; i < 10000; i++) {
            if ("a".equals(t.roll(r).item)) {
                a++;
            }
        }
        assertTrue(a > 8700 && a < 9300, "a rolled " + a);
    }
}

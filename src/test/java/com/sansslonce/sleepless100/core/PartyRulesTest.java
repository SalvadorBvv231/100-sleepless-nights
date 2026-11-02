package com.sansslonce.sleepless100.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.sansslonce.sleepless100.core.PartyRules.MobPlan;
import com.sansslonce.sleepless100.core.PartyRules.MobType;
import com.sansslonce.sleepless100.core.PartyRules.Role;

class PartyRulesTest {

    private static final PartySettings S = new PartySettings();

    @Test
    void calendar() {
        assertEquals("Sunday", PartyRules.weekdayName(0));
        assertEquals("Friday", PartyRules.weekdayName(5));
        assertEquals("Friday", PartyRules.weekdayName(12));
        assertTrue(PartyRules.isPartyDay(5, S));
        assertTrue(PartyRules.isPartyDay(12, S));
        assertFalse(PartyRules.isPartyDay(6, S));
        assertFalse(PartyRules.isPartyDay(-2, S));

        assertEquals(1, PartyRules.fridayNumber(5, S));
        assertEquals(2, PartyRules.fridayNumber(12, S));
        assertEquals(1, PartyRules.fridayNumber(0, S)); // next Friday
        assertEquals(2, PartyRules.fridayNumber(6, S));

        assertEquals(5, PartyRules.nextPartyDay(0, false, S));
        assertEquals(5, PartyRules.nextPartyDay(5, false, S));
        assertEquals(12, PartyRules.nextPartyDay(5, true, S));
        assertEquals(12, PartyRules.nextPartyDay(6, false, S));
    }

    @Test
    void nightWindow() {
        assertFalse(PartyRules.inStartWindow(12999, S));
        assertTrue(PartyRules.inStartWindow(13000, S));
        assertTrue(PartyRules.inStartWindow(17999, S));
        assertFalse(PartyRules.inStartWindow(18000, S));
        assertTrue(PartyRules.isPartyNight(22999, S));
        assertFalse(PartyRules.isPartyNight(23000, S));
        assertEquals(10000, PartyRules.ticksUntilEnd(13000, S));
        assertEquals(17000, PartyRules.ticksUntilEnd(6000, S)); // started by command at noon
        assertEquals(24000, PartyRules.ticksUntilEnd(23000, S));
        assertEquals(1, PartyRules.timeOfDay(24001));
    }

    @Test
    void sizeGrowsAndCaps() {
        assertEquals(4, PartyRules.partySize(1, S));
        assertEquals(6, PartyRules.partySize(2, S));
        assertEquals(22, PartyRules.partySize(10, S));
        assertEquals(30, PartyRules.partySize(14, S));
        assertEquals(30, PartyRules.partySize(100, S));
    }

    @Test
    void groups() {
        assertEquals(2, PartyRules.groupCount(4, S));
        assertEquals(2, PartyRules.groupCount(10, S));
        assertEquals(3, PartyRules.groupCount(12, S));
        assertEquals(4, PartyRules.groupCount(30, S));
    }

    @Test
    void mobTypesByWeek() {
        Random r = new Random(1);
        assertEquals(0, PartyRules.extraTypeCount(2, S));
        assertEquals(1, PartyRules.extraTypeCount(3, S));
        assertEquals(1, PartyRules.extraTypeCount(5, S));
        assertEquals(2, PartyRules.extraTypeCount(6, S));
        assertEquals(2, PartyRules.extraTypeCount(9, S));
        assertEquals(3, PartyRules.extraTypeCount(10, S));
        for (int friday = 1; friday <= 20; friday++) {
            for (int i = 0; i < 50; i++) {
                Map<MobType, Integer> c = PartyRules.composition(friday, S, r);
                int total = c.values()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .sum();
                assertEquals(PartyRules.partySize(friday, S), total);
                assertEquals(1 + PartyRules.extraTypeCount(friday, S), c.size(), "types on Friday " + friday);
                assertTrue(c.get(MobType.ZOMBIE) >= 1);
                assertTrue(c.getOrDefault(MobType.CREEPER, 0) <= S.maxCreepers);
            }
        }
    }

    @Test
    void digLevels() {
        assertEquals(1, PartyRules.digLevel(1, S));
        assertEquals(1, PartyRules.digLevel(3, S));
        assertEquals(2, PartyRules.digLevel(4, S));
        assertEquals(2, PartyRules.digLevel(7, S));
        assertEquals(3, PartyRules.digLevel(8, S));
    }

    @Test
    void planRoles() {
        Random r = new Random(7);
        for (int friday = 1; friday <= 20; friday++) {
            for (int i = 0; i < 30; i++) {
                List<List<MobPlan>> groups = PartyRules.plan(friday, S, r);
                assertEquals(PartyRules.groupCount(PartyRules.partySize(friday, S), S), groups.size());
                int total = 0, champions = 0, builders = 0, miners = 0;
                for (List<MobPlan> g : groups) {
                    assertFalse(g.isEmpty());
                    for (MobPlan m : g) {
                        total++;
                        if (m.role == Role.CHAMPION) {
                            champions++;
                            assertTrue(m.type == MobType.ZOMBIE || m.type == MobType.SKELETON);
                            assertTrue(m.powers.size() >= 1 && m.powers.size() <= 2);
                        } else {
                            assertTrue(m.powers.isEmpty());
                        }
                        if (m.role == Role.BUILDER) {
                            builders++;
                        }
                        if (m.role == Role.MINER) {
                            miners++;
                        }
                        if (m.role == Role.MINER || m.role == Role.BUILDER) {
                            assertEquals(MobType.ZOMBIE, m.type);
                        }
                    }
                }
                assertEquals(PartyRules.partySize(friday, S), total);
                assertEquals(friday >= 8, champions >= 1, "champions on Friday " + friday);
                assertTrue(champions <= 2);
                if (friday < 5) {
                    assertEquals(0, builders);
                }
                assertTrue(miners >= 1, "miners on Friday " + friday);
            }
        }
    }

    @Test
    void rewardsScale() {
        assertEquals(26, PartyRules.rewardXp(4, S));
        assertEquals(130, PartyRules.rewardXp(30, S));
        assertEquals(1, PartyRules.rewardRolls(4, S));
        assertEquals(9, PartyRules.rewardRolls(30, S));
    }

    @Test
    void digTicks() {
        assertEquals(30, PartyRules.digTicks(0.5F, S)); // dirt
        assertEquals(20, PartyRules.digTicks(0.3F, S)); // glass, minimum 1 s
        assertEquals(120, PartyRules.digTicks(2.0F, S)); // cobblestone, planks
    }

    @Test
    void groupAnglesSpread() {
        double a0 = PartyRules.groupAngle(0, 0, 4, 0.5);
        double a1 = PartyRules.groupAngle(0, 1, 4, 0.5);
        assertEquals(Math.PI / 2, a1 - a0, 1e-9);
        // jitter is at most 20 degrees
        assertEquals(Math.toRadians(20), PartyRules.groupAngle(0, 0, 4, 1.0), 1e-9);
    }
}

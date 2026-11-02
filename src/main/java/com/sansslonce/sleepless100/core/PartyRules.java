package com.sansslonce.sleepless100.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Friday Night Party rules: calendar, size, mob mix, roles and rewards. Plain Java (no Minecraft imports).
 */
public final class PartyRules {

    public static final int DAYS_PER_WEEK = 7;
    public static final int TICKS_PER_DAY = 24000;
    private static final String[] WEEKDAYS = { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
        "Saturday" };

    public enum MobType {
        ZOMBIE,
        SKELETON,
        SPIDER,
        WITCH,
        CREEPER,
        ENDERMAN
    }

    /** Types that can join the zombies, in a fixed order (the party picks some at random). */
    public static final MobType[] EXTRA_TYPES = { MobType.SKELETON, MobType.SPIDER, MobType.WITCH, MobType.CREEPER,
        MobType.ENDERMAN };

    public enum Role {
        NONE,
        MINER,
        BUILDER,
        CHAMPION
    }

    public enum Power {
        REGEN,
        BULWARK,
        VENGEANCE,
        WEBBER
    }

    /** One planned party mob. */
    public static final class MobPlan {

        public final MobType type;
        public final Role role;
        public final List<Power> powers;

        public MobPlan(MobType type, Role role, List<Power> powers) {
            this.type = type;
            this.role = role;
            this.powers = Collections.unmodifiableList(new ArrayList<>(powers));
        }
    }

    private PartyRules() {}

    // ---- Calendar ----

    public static String weekdayName(long day) {
        return WEEKDAYS[(int) Math.floorMod(day, (long) DAYS_PER_WEEK)];
    }

    public static boolean isPartyDay(long day, PartySettings s) {
        return day >= 0 && Math.floorMod(day, (long) DAYS_PER_WEEK) == s.partyWeekday;
    }

    /** Friday number of a party day (day 5 is Friday 1); for other days, the number of the next Friday. */
    public static int fridayNumber(long day, PartySettings s) {
        return (int) Math.floorDiv(nextPartyDay(day, false, s) - s.partyWeekday, (long) DAYS_PER_WEEK) + 1;
    }

    /** The day of the next party: today if it is a party day and today's party hasn't started yet. */
    public static long nextPartyDay(long day, boolean heldToday, PartySettings s) {
        long d = Math.max(0, day);
        if (heldToday) {
            d++;
        }
        long offset = Math.floorMod(s.partyWeekday - d, (long) DAYS_PER_WEEK);
        return d + offset;
    }

    public static int timeOfDay(long worldTime) {
        return (int) Math.floorMod(worldTime, (long) TICKS_PER_DAY);
    }

    /** True from dusk until the latest start time. */
    public static boolean inStartWindow(int timeOfDay, PartySettings s) {
        return inWindow(timeOfDay, s.startTime, s.latestStartTime);
    }

    /** True from dusk until sunrise: the party night. */
    public static boolean isPartyNight(int timeOfDay, PartySettings s) {
        return inWindow(timeOfDay, s.startTime, s.endTime);
    }

    /** Ticks from {@code timeOfDay} until the next sunrise (never 0: a party lasts at least until the next one). */
    public static int ticksUntilEnd(int timeOfDay, PartySettings s) {
        int t = Math.floorMod(s.endTime - timeOfDay, TICKS_PER_DAY);
        return t == 0 ? TICKS_PER_DAY : t;
    }

    private static boolean inWindow(int t, int from, int to) {
        return from <= to ? t >= from && t < to : t >= from || t < to;
    }

    // ---- Size and mix ----

    public static int partySize(int friday, PartySettings s) {
        long size = (long) s.baseSize + (long) Math.max(0, friday - 1) * s.sizePerWeek;
        return (int) Math.max(1, Math.min(s.maxSize, size));
    }

    public static int groupCount(int size, PartySettings s) {
        int min = Math.max(1, s.minGroups);
        int max = Math.max(min, s.maxGroups);
        int groups = (int) Math.ceil(size / (double) Math.max(1, s.mobsPerGroup));
        return Math.max(1, Math.min(size, Math.max(min, Math.min(max, groups))));
    }

    /** How many weekly thresholds have been reached by this Friday. */
    public static int stepsReached(int friday, int[] weeks) {
        int n = 0;
        for (int w : weeks) {
            if (friday >= w) {
                n++;
            }
        }
        return n;
    }

    public static int extraTypeCount(int friday, PartySettings s) {
        return Math.min(EXTRA_TYPES.length, stepsReached(friday, s.extraTypeWeeks));
    }

    /** Which miner block lists are open this week: 0 = none, 1 = dirt/sand/gravel, 2 = + wood/glass, 3 = + stone. */
    public static int digLevel(int friday, PartySettings s) {
        return stepsReached(friday, s.digWeeks);
    }

    /** Mob counts for a party; zombies are the rest and always at least one. */
    public static Map<MobType, Integer> composition(int friday, PartySettings s, Random rand) {
        int size = partySize(friday, s);
        List<MobType> pool = new ArrayList<>();
        Collections.addAll(pool, EXTRA_TYPES);
        Collections.shuffle(pool, rand);
        Map<MobType, Integer> counts = new EnumMap<>(MobType.class);
        int extras = 0;
        int types = extraTypeCount(friday, s);
        for (int i = 0; i < types; i++) {
            MobType type = pool.get(i);
            int cap = type == MobType.CREEPER ? s.maxCreepers : s.maxPerExtraType;
            int n = Math.max(1, Math.min(cap, (int) Math.round(size * s.extraTypeShare)));
            n = Math.min(n, size - 1 - extras); // keep at least one zombie
            if (n > 0) {
                counts.put(type, n);
                extras += n;
            }
        }
        counts.put(MobType.ZOMBIE, size - extras);
        return counts;
    }

    /** The full plan: mobs split into groups, roles and champion powers decided. */
    public static List<List<MobPlan>> plan(int friday, PartySettings s, Random rand) {
        Map<MobType, Integer> counts = composition(friday, s, rand);
        int zombies = counts.get(MobType.ZOMBIE);
        int skeletons = counts.getOrDefault(MobType.SKELETON, 0);

        int champions = 0;
        if (friday >= s.championWeek) {
            int lo = Math.max(0, s.minChampions);
            int hi = Math.max(lo, s.maxChampions);
            champions = Math.min(zombies + skeletons, lo + rand.nextInt(hi - lo + 1));
        }
        int miners = digLevel(friday, s) > 0 ? (int) Math.round(zombies * s.minerShare) : 0;
        int builders = friday >= s.builderWeek ? (int) Math.round(zombies * s.builderShare) : 0;

        // Champions come from zombies and skeletons; miners and builders from the other zombies.
        List<MobPlan> zombiePlans = new ArrayList<>();
        List<MobPlan> skeletonPlans = new ArrayList<>();
        List<Boolean> championSlots = new ArrayList<>();
        for (int i = 0; i < zombies + skeletons; i++) {
            championSlots.add(i < champions);
        }
        Collections.shuffle(championSlots, rand);
        int zombieChampions = 0;
        for (int i = 0; i < zombies + skeletons; i++) {
            boolean zombie = i < zombies;
            boolean champion = championSlots.get(i);
            MobType type = zombie ? MobType.ZOMBIE : MobType.SKELETON;
            if (champion) {
                (zombie ? zombiePlans : skeletonPlans).add(new MobPlan(type, Role.CHAMPION, powers(s, rand)));
                if (zombie) {
                    zombieChampions++;
                }
            } else if (!zombie) {
                skeletonPlans.add(new MobPlan(type, Role.NONE, Collections.emptyList()));
            }
        }
        int plainZombies = zombies - zombieChampions;
        miners = Math.min(miners, plainZombies);
        builders = Math.min(builders, plainZombies - miners);
        for (int i = 0; i < plainZombies; i++) {
            Role role = i < miners ? Role.MINER : i < miners + builders ? Role.BUILDER : Role.NONE;
            zombiePlans.add(new MobPlan(MobType.ZOMBIE, role, Collections.emptyList()));
        }

        List<MobPlan> all = new ArrayList<>(zombiePlans);
        all.addAll(skeletonPlans);
        for (MobType type : EXTRA_TYPES) {
            if (type == MobType.SKELETON) {
                continue;
            }
            for (int i = 0; i < counts.getOrDefault(type, 0); i++) {
                all.add(new MobPlan(type, Role.NONE, Collections.emptyList()));
            }
        }
        Collections.shuffle(all, rand);

        int groupCount = groupCount(all.size(), s);
        List<List<MobPlan>> groups = new ArrayList<>();
        for (int g = 0; g < groupCount; g++) {
            groups.add(new ArrayList<>());
        }
        for (int i = 0; i < all.size(); i++) {
            groups.get(i % groupCount)
                .add(all.get(i));
        }
        return groups;
    }

    static List<Power> powers(PartySettings s, Random rand) {
        List<Power> pool = new ArrayList<>();
        Collections.addAll(pool, Power.values());
        Collections.shuffle(pool, rand);
        int lo = Math.max(1, Math.min(pool.size(), s.minPowers));
        int hi = Math.max(lo, Math.min(pool.size(), s.maxPowers));
        int n = lo + rand.nextInt(hi - lo + 1);
        List<Power> chosen = new ArrayList<>(pool.subList(0, n));
        Collections.sort(chosen);
        return chosen;
    }

    // ---- Geometry ----

    /** Angle (radians) of group {@code g} of {@code groups}: evenly spread, plus up to ±20° of jitter. */
    public static double groupAngle(double baseAngle, int g, int groups, double jitter01) {
        double spread = 2 * Math.PI / Math.max(1, groups);
        double jitter = (jitter01 * 2 - 1) * Math.toRadians(20);
        return baseAngle + g * spread + jitter;
    }

    // ---- Work and rewards ----

    public static int digTicks(float hardness, PartySettings s) {
        return Math.max(s.minDigTicks, (int) Math.round(hardness * s.digTicksPerHardness));
    }

    public static int rewardXp(int size, PartySettings s) {
        return Math.max(0, s.rewardXpBase + s.rewardXpPerMob * size);
    }

    public static int rewardRolls(int size, PartySettings s) {
        return Math.max(1, (int) Math.round(size * s.rewardRollsPerMob));
    }

    public static long repairDelayTicks(PartySettings s) {
        return Math.max(20L, Math.round(s.repairMinutes * 60 * 20));
    }
}

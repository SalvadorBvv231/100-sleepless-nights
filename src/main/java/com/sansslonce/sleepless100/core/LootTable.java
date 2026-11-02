package com.sansslonce.sleepless100.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A weighted loot table read from config lines like {@code "minecraft:iron_ingot,1,3,8"} (item, min, max, weight).
 * Plain Java (no Minecraft imports); item names are resolved on the Minecraft side.
 */
public final class LootTable {

    public static final class Entry {

        public final String item;
        public final int min;
        public final int max;
        public final int weight;

        public Entry(String item, int min, int max, int weight) {
            this.item = item;
            this.min = min;
            this.max = max;
            this.weight = weight;
        }
    }

    /** One rolled stack: item name and count. */
    public static final class Drop {

        public final String item;
        public final int count;

        Drop(String item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    private final List<Entry> entries;
    private final List<String> errors;
    private final int totalWeight;

    private LootTable(List<Entry> entries, List<String> errors) {
        this.entries = Collections.unmodifiableList(entries);
        this.errors = Collections.unmodifiableList(errors);
        int w = 0;
        for (Entry e : entries) {
            w += e.weight;
        }
        this.totalWeight = w;
    }

    /** Parses config lines; bad lines are skipped and listed in {@link #errors()}. */
    public static LootTable parse(String[] lines) {
        List<Entry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                Entry e = parseLine(line);
                if (e != null) {
                    entries.add(e);
                } else if (line != null && !line.trim()
                    .isEmpty()) {
                        errors.add(line);
                    }
            }
        }
        return new LootTable(entries, errors);
    }

    static Entry parseLine(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim()
            .split("\\s*,\\s*");
        if (parts.length != 4 || parts[0].isEmpty()) {
            return null;
        }
        try {
            int min = Integer.parseInt(parts[1]);
            int max = Integer.parseInt(parts[2]);
            int weight = Integer.parseInt(parts[3]);
            if (min < 1 || max < min || max > 64 || weight < 1) {
                return null;
            }
            return new Entry(parts[0], min, max, weight);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<String> errors() {
        return errors;
    }

    /** One weighted roll, or null if the table is empty. */
    public Drop roll(Random rand) {
        if (totalWeight <= 0) {
            return null;
        }
        int r = rand.nextInt(totalWeight);
        for (Entry e : entries) {
            r -= e.weight;
            if (r < 0) {
                return new Drop(e.item, e.min + rand.nextInt(e.max - e.min + 1));
            }
        }
        return null;
    }
}

package com.sansslonce.sleepless100;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.sansslonce.sleepless100.core.DifficultyClock;

/** Settings loaded from config/sleepless100.cfg. */
public class Config {

    public static final String CATEGORY_DIFFICULTY = "difficulty";

    public static double daySpeedMultiplier = 1.0;
    public static int[] tierStartDays = DifficultyClock.DEFAULT_TIER_STARTS.clone();
    public static boolean tierChatMessages = true;

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

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}

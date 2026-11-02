package com.sansslonce.sleepless100.core;

/** Village numbers. Plain Java (no Minecraft imports); filled from the config file. */
public final class VillageSettings {

    /** Iron golems spawn this many times as often as in vanilla. */
    public double golemSpawnMultiplier = 1.5;
    /** Extra golem health per fully ramped tier: 0.15 = +15% per tier. */
    public double golemHealthPerTier = 0.15;
    /** Extra golem damage per fully ramped tier. */
    public double golemDamagePerTier = 0.15;
    /** From this day, each village may have this many golems above vanilla's cap. */
    public int extraGolemDay = 50;
    public int extraGolems = 1;
    /** From this day, villagers go indoors from earlyIndoorsTime instead of at dusk. */
    public int earlyIndoorsDay = 100;
    public int earlyIndoorsTime = 11000;
}

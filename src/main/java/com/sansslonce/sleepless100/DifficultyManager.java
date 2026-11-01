package com.sansslonce.sleepless100;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;

import com.sansslonce.sleepless100.core.DifficultyClock;
import com.sansslonce.sleepless100.world.SleeplessWorldData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Connects the pure {@link DifficultyClock} math to the running Minecraft server. */
public class DifficultyManager {

    private static final int CHECK_INTERVAL_TICKS = 20;

    private static final String[] TIER_FLAVOR = { "The world is calm.", "The nights grow restless...",
        "The dead grow stronger...", "They come prepared...", "Something in the dark grows bolder...",
        "One hundred sleepless nights. Nothing is safe." };

    private int tickCounter;

    private static WorldServer overworld(MinecraftServer server) {
        return server.worldServerForDimension(0);
    }

    public static double getDay(MinecraftServer server) {
        WorldServer world = overworld(server);
        return DifficultyClock.computeDay(
            world.getTotalWorldTime(),
            Config.daySpeedMultiplier,
            SleeplessWorldData.get(world)
                .getDayOffset());
    }

    public static int getTier(MinecraftServer server) {
        return DifficultyClock.tierForDay(getDay(server), Config.tierStartDays);
    }

    /** Makes the current difficulty day equal {@code targetDay}, then announces any new tier. */
    public static void setDay(MinecraftServer server, double targetDay) {
        WorldServer world = overworld(server);
        SleeplessWorldData data = SleeplessWorldData.get(world);
        data.setDayOffset(
            DifficultyClock.offsetForDay(targetDay, world.getTotalWorldTime(), Config.daySpeedMultiplier));
        checkTier(server);
    }

    /** Announces a newly reached tier once; silently lowers the stored tier if the day went back. */
    public static void checkTier(MinecraftServer server) {
        SleeplessWorldData data = SleeplessWorldData.get(overworld(server));
        int tier = getTier(server);
        if (tier == data.getLastTier()) {
            return;
        }
        boolean rising = tier > data.getLastTier();
        data.setLastTier(tier);
        if (rising) {
            SleeplessNights.LOG.info("Tier {} begins (day {})", tier, getDay(server));
            if (Config.tierChatMessages) {
                announceTier(server, tier);
            }
        }
    }

    private static void announceTier(MinecraftServer server, int tier) {
        int startDay = DifficultyClock.tierStartDay(tier, Config.tierStartDays);
        String flavor = TIER_FLAVOR[Math.min(tier, TIER_FLAVOR.length - 1)];
        String message = EnumChatFormatting.DARK_RED + "[100 Sleepless Nights] "
            + EnumChatFormatting.GOLD
            + "Day "
            + startDay
            + ": Tier "
            + tier
            + " begins. "
            + EnumChatFormatting.GRAY
            + EnumChatFormatting.ITALIC
            + flavor;
        server.getConfigurationManager()
            .sendChatMsg(new ChatComponentText(message));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.worldServers != null && server.worldServers.length > 0) {
            checkTier(server);
        }
    }
}

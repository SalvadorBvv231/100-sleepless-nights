package com.sansslonce.sleepless100.command;

import java.util.List;
import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.DifficultyManager;
import com.sansslonce.sleepless100.core.DifficultyClock;
import com.sansslonce.sleepless100.core.MobEffects;
import com.sansslonce.sleepless100.core.PartyRules;
import com.sansslonce.sleepless100.party.PartyManager;

/**
 * /sleepless status and /sleepless effects (everyone); /sleepless setday &lt;n&gt; and
 * /sleepless party start [friday] | stop (ops only).
 */
public class CommandSleepless extends CommandBase {

    private static final int SETDAY_PERMISSION_LEVEL = 2;

    @Override
    public String getCommandName() {
        return "sleepless";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/sleepless status  |  /sleepless effects  |  /sleepless setday <day>  |  /sleepless party start [friday] | stop";
    }

    /** Everyone may run the command; setday checks op permission itself. */
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        MinecraftServer server = MinecraftServer.getServer();
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            // Refuse extra words instead of silently ignoring them, so "/sleepless status 10"
            // (easy to get by pressing Tab after "/sleepless s") doesn't look like setday did nothing.
            if (args.length > 1) {
                throw new WrongUsageException(
                    "/sleepless status takes no extra words. To change the day, use: /sleepless setday " + args[1]);
            }
            showStatus(sender, server);
        } else if ("effects".equalsIgnoreCase(args[0])) {
            showEffects(sender, server);
        } else if ("party".equalsIgnoreCase(args[0])) {
            requireOp(sender);
            party(sender, server, args);
        } else if ("setday".equalsIgnoreCase(args[0])) {
            requireOp(sender);
            if (args.length != 2) {
                throw new WrongUsageException("/sleepless setday <day>");
            }
            double targetDay = parseDoubleWithMin(sender, args[1], 0.0);
            DifficultyManager.setDay(server, targetDay);
            double day = DifficultyManager.getDay(server);
            send(
                sender,
                EnumChatFormatting.GREEN + "Difficulty day set to "
                    + formatDay(day)
                    + " (Tier "
                    + DifficultyManager.getTier(server)
                    + ").");
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void requireOp(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(SETDAY_PERMISSION_LEVEL, getCommandName())) {
            throw new CommandException("commands.generic.permission");
        }
    }

    private static void party(ICommandSender sender, MinecraftServer server, String[] args) {
        if (args.length >= 2 && "start".equalsIgnoreCase(args[1]) && args.length <= 3) {
            int friday = args.length == 3 ? parseIntBounded(sender, args[2], 1, 10000) : 0;
            String error = PartyManager.start(server, friday);
            if (error != null) {
                throw new CommandException(error);
            }
            int tod = PartyRules.timeOfDay(
                server.worldServerForDimension(0)
                    .getWorldTime());
            if (!PartyRules.isPartyNight(tod, Config.party)) {
                send(
                    sender,
                    EnumChatFormatting.YELLOW
                        + "It's daytime: undead burn and the party lasts until the next sunrise. Try /time set 13000 first.");
            }
        } else if (args.length == 2 && "stop".equalsIgnoreCase(args[1])) {
            if (!PartyManager.stop(server)) {
                throw new CommandException("No party is running.");
            }
        } else {
            throw new WrongUsageException("/sleepless party start [friday]  |  /sleepless party stop");
        }
    }

    private static void showStatus(ICommandSender sender, MinecraftServer server) {
        int[] starts = Config.tierStartDays;
        double day = DifficultyManager.getDay(server);
        int tier = DifficultyManager.getTier(server);

        send(
            sender,
            EnumChatFormatting.GOLD + "[100 Sleepless Nights] "
                + EnumChatFormatting.WHITE
                + "Day "
                + formatDay(day)
                + " ("
                + PartyRules.weekdayName((long) Math.floor(day))
                + "), Tier "
                + tier
                + " of "
                + starts.length);

        int nextStart = DifficultyClock.nextTierStartDay(day, starts);
        if (nextStart < 0) {
            send(sender, EnumChatFormatting.GRAY + "This is the final tier.");
        } else {
            long ticks = DifficultyClock.ticksUntilDay(nextStart, day, Config.daySpeedMultiplier);
            double daysLeft = DifficultyClock.displayDaysLeft((double) ticks / DifficultyClock.TICKS_PER_DAY);
            send(
                sender,
                EnumChatFormatting.GRAY + "Next: Tier "
                    + (tier + 1)
                    + " on day "
                    + nextStart
                    + ", in "
                    + String.format(Locale.ROOT, "%.1f", daysLeft)
                    + " Minecraft days (about "
                    + formatRealTime(ticks / DifficultyClock.TICKS_PER_SECOND)
                    + " of play).");
        }
        for (String line : PartyManager.statusLines(server)) {
            send(sender, line);
        }
        if (Config.daySpeedMultiplier != 1.0) {
            send(
                sender,
                EnumChatFormatting.GRAY + "Day speed multiplier: "
                    + String.format(Locale.ROOT, "%.2f", Config.daySpeedMultiplier)
                    + "x");
        }
    }

    /** Current strength of every effect, for newly spawned mobs. */
    private static void showEffects(ICommandSender sender, MinecraftServer server) {
        MobEffects fx = DifficultyManager.currentEffects(server);
        send(
            sender,
            EnumChatFormatting.GOLD + "[100 Sleepless Nights] "
                + EnumChatFormatting.WHITE
                + "Effects for new mobs on day "
                + formatDay(DifficultyManager.getDay(server))
                + ":");
        line(sender, "T1 extra night spawns", "+" + pct(fx.extraNightSpawns));
        line(sender, "T1 follow range", "+" + pct(fx.followRangeBonus));
        line(sender, "T2 health / damage", "+" + pct(fx.healthBonus) + " / +" + pct(fx.damageBonus));
        line(sender, "T2 zombies break doors", pct(fx.doorBreakChance) + " chance");
        line(sender, "T3 armor / weapon", pct(fx.armorChance) + " / " + pct(fx.weaponChance) + " chance");
        line(
            sender,
            "T4 creeper explosion",
            "x" + String.format(Locale.ROOT, "%.2f", fx.creeperRadiusScale)
                + " (radius "
                + String.format(
                    Locale.ROOT,
                    "%.1f",
                    MobEffects
                        .creeperRadius(MobEffects.VANILLA_CREEPER_RADIUS, fx.creeperRadiusScale, fx.creeperMaxRadius))
                + ", max "
                + String.format(Locale.ROOT, "%.1f", fx.creeperMaxRadius)
                + ")");
        line(sender, "T5 full enchanted gear", pct(fx.fullGearChance) + " chance");
    }

    private static void line(ICommandSender sender, String label, String value) {
        send(sender, EnumChatFormatting.GRAY + label + ": " + EnumChatFormatting.WHITE + value);
    }

    private static String pct(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    private static String formatDay(double day) {
        return String.format(Locale.ROOT, "%.1f", DifficultyClock.displayDay(day));
    }

    private static String formatRealTime(long seconds) {
        if (seconds < 60) {
            return seconds + " s";
        }
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) {
            return minutes + " min";
        }
        return (minutes / 60) + " h " + (minutes % 60) + " min";
    }

    private static void send(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "effects", "setday", "party");
        }
        if (args.length == 2 && "party".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "start", "stop");
        }
        return null;
    }
}

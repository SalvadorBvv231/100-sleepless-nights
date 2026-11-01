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

/** /sleepless status (everyone) and /sleepless setday &lt;n&gt; (ops only). */
public class CommandSleepless extends CommandBase {

    private static final int SETDAY_PERMISSION_LEVEL = 2;

    @Override
    public String getCommandName() {
        return "sleepless";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/sleepless status  |  /sleepless setday <day>";
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
        } else if ("setday".equalsIgnoreCase(args[0])) {
            if (!sender.canCommandSenderUseCommand(SETDAY_PERMISSION_LEVEL, getCommandName())) {
                throw new CommandException("commands.generic.permission");
            }
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
                + ", Tier "
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
        if (Config.daySpeedMultiplier != 1.0) {
            send(
                sender,
                EnumChatFormatting.GRAY + "Day speed multiplier: "
                    + String.format(Locale.ROOT, "%.2f", Config.daySpeedMultiplier)
                    + "x");
        }
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
            return getListOfStringsMatchingLastWord(args, "status", "setday");
        }
        return null;
    }
}

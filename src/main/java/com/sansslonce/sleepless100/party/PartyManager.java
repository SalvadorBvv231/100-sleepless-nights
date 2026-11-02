package com.sansslonce.sleepless100.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.DifficultyManager;
import com.sansslonce.sleepless100.SleeplessNights;
import com.sansslonce.sleepless100.core.LootTable;
import com.sansslonce.sleepless100.core.PartyRules;
import com.sansslonce.sleepless100.core.PartyRules.MobPlan;
import com.sansslonce.sleepless100.core.PartyRules.Power;
import com.sansslonce.sleepless100.core.PartyRules.Role;
import com.sansslonce.sleepless100.core.PartySettings;
import com.sansslonce.sleepless100.mobs.GearRules;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Runs the Friday Night Party: starts it at dusk on Fridays, spawns the groups, makes them regroup and attack,
 * runs champion powers, rewards survivors at sunrise and repairs party damage. Registered on the Forge and FML
 * buses. All the rules (when, how many, which mobs, rewards) come from {@link PartyRules}.
 */
public class PartyManager {

    private static final String PREFIX = EnumChatFormatting.DARK_RED + "[100 Sleepless Nights] ";
    private static final int DRIVE_TICKS = 10;
    private static final int TARGET_TICKS = 100;
    private static final double RALLY_RADIUS = 5;

    /** Cached for the AI tasks; refreshed every server tick. */
    private static PartyData data;
    private static final Map<Integer, List<EntityLiving>> members = new HashMap<>();
    private static int alive;

    private long ticks;

    // ---- Queries used by AI tasks and the command ----

    static PartyData.Party party() {
        return data == null ? null : data.party;
    }

    private static PartyData.Party partyOf(Entity mob) {
        PartyData.Party p = party();
        return p != null && PartyMob.partyId(mob) == p.id ? p : null;
    }

    static boolean isAttacking(Entity mob) {
        PartyData.Party p = partyOf(mob);
        return p != null && p.attacking;
    }

    static int digLevel() {
        PartyData.Party p = party();
        return p == null ? 0 : PartyRules.digLevel(p.friday, Config.party);
    }

    /** Where a party mob should walk now: its rally point, then the target. Null if it should stay put. */
    static double[] walkGoal(EntityLiving mob) {
        PartyData.Party p = partyOf(mob);
        if (p == null) {
            return null;
        }
        if (!p.attacking) {
            int g = PartyMob.group(mob);
            if (g < 0 || g >= p.groups.size() || !p.groups.get(g).spawned) {
                return null;
            }
            PartyData.Group group = p.groups.get(g);
            return mob.getDistanceSq(group.rx, group.ry, group.rz) > RALLY_RADIUS * RALLY_RADIUS
                ? new double[] { group.rx, group.ry, group.rz }
                : null;
        }
        return mob.getDistanceSq(p.tx, p.ty, p.tz) > 4 ? new double[] { p.tx, p.ty, p.tz } : null;
    }

    public static boolean isRunning() {
        return party() != null;
    }

    /** Lines for /sleepless status. */
    public static List<String> statusLines(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        PartySettings s = Config.party;
        if (!s.enabled) {
            lines.add(EnumChatFormatting.GRAY + "Friday Night Party: turned off in the config.");
            return lines;
        }
        WorldServer world = server.worldServerForDimension(0);
        PartyData d = PartyData.get(world);
        if (d.party != null) {
            PartyData.Party p = d.party;
            long left = Math.max(0, p.endTime - world.getTotalWorldTime());
            lines.add(
                EnumChatFormatting.RED + "Friday Night Party in progress: Friday "
                    + p.friday
                    + ", "
                    + (p.attacking ? "attacking" : "regrouping")
                    + ", "
                    + alive
                    + " of "
                    + p.size
                    + " mobs left, sunrise in "
                    + (left / 20 / 60)
                    + " min.");
            return lines;
        }
        double day = DifficultyManager.getDay(server);
        long today = (long) Math.floor(day);
        // Today's party already happened, or its start window has passed.
        boolean heldToday = d.lastPartyDay == today || timeOfDay(world) >= s.latestStartTime;
        long next = PartyRules.nextPartyDay(today, heldToday, s);
        int friday = PartyRules.fridayNumber(next, s);
        int types = PartyRules.extraTypeCount(friday, s);
        String when = next == today ? "tonight at dusk"
            : "on day " + next
                + " (in "
                + String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, next + s.startTime / 24000.0 - day))
                + " days)";
        lines.add(
            EnumChatFormatting.GRAY + "Next Friday Night Party: "
                + when
                + ", Friday "
                + friday
                + ": "
                + PartyRules.partySize(friday, s)
                + " mobs, zombies"
                + (types == 0 ? " only" : " + " + types + " random type" + (types > 1 ? "s" : ""))
                + ".");
        return lines;
    }

    private static int timeOfDay(World w) {
        return PartyRules.timeOfDay(w.getWorldTime());
    }

    // ---- Start and end ----

    /** Starts a party now. {@code friday} <= 0 means the current week. Returns an error message or null. */
    public static String start(MinecraftServer server, int friday) {
        WorldServer world = server.worldServerForDimension(0);
        PartyData d = PartyData.get(world);
        if (d.party != null) {
            return "A party is already running.";
        }
        if (world.playerEntities.isEmpty()) {
            return "Nobody is in the overworld.";
        }
        long today = (long) Math.floor(DifficultyManager.getDay(server));
        int f = friday > 0 ? friday : PartyRules.fridayNumber(today, Config.party);
        if (PartyRules.isPartyDay(today, Config.party)) {
            d.lastPartyDay = today;
        }
        begin(world, d, f);
        return null;
    }

    public static boolean stop(MinecraftServer server) {
        WorldServer world = server.worldServerForDimension(0);
        PartyData d = PartyData.get(world);
        if (d.party == null) {
            return false;
        }
        end(world, d, false);
        return true;
    }

    private static void begin(WorldServer world, PartyData d, int friday) {
        PartySettings s = Config.party;
        Random rand = world.rand;
        PartyData.Party p = new PartyData.Party();
        long now = world.getTotalWorldTime();
        p.id = now;
        p.friday = friday;
        p.size = PartyRules.partySize(friday, s);
        p.endTime = now + PartyRules.ticksUntilEnd(timeOfDay(world), s);
        p.nightStart = PartyRules.isPartyNight(timeOfDay(world), s);
        EntityPlayer host = (EntityPlayer) world.playerEntities.get(rand.nextInt(world.playerEntities.size()));
        p.host = host.getUniqueID();
        double[] target = target(world, host, s);
        p.tx = target[0];
        p.ty = target[1];
        p.tz = target[2];
        for (Object o : world.playerEntities) {
            EntityPlayer pl = (EntityPlayer) o;
            if (pl == host || pl.getDistanceSq(p.tx, p.ty, p.tz) <= s.guestRange * s.guestRange) {
                p.guests.add(pl.getUniqueID());
            }
        }
        p.baseAngle = rand.nextDouble() * Math.PI * 2;
        List<List<MobPlan>> plan = PartyRules.plan(friday, s, rand);
        for (int i = 0; i < plan.size(); i++) {
            PartyData.Group g = new PartyData.Group();
            g.index = i;
            g.angle = PartyRules.groupAngle(p.baseAngle, i, plan.size(), rand.nextDouble());
            g.plans.addAll(plan.get(i));
            p.groups.add(g);
        }
        p.nextSpawnTime = now;
        d.party = p;
        d.markDirty();
        data = d;
        members.clear();
        alive = 0;

        SleeplessNights.LOG.info(
            "Friday Night Party starts: Friday {}, {} mobs in {} groups, target {},{},{}",
            friday,
            p.size,
            plan.size(),
            (int) p.tx,
            (int) p.ty,
            (int) p.tz);
        broadcast(
            world,
            PREFIX + EnumChatFormatting.GOLD
                + "Friday Night Party"
                + EnumChatFormatting.GRAY
                + EnumChatFormatting.ITALIC
                + " — they're coming...");
        // A distant groan from the direction of the first group.
        double a = p.groups.get(0).angle;
        for (Object o : world.playerEntities) {
            EntityPlayerMP pl = (EntityPlayerMP) o;
            if (p.guests.contains(pl.getUniqueID())) {
                pl.playerNetServerHandler.sendPacket(
                    new S29PacketSoundEffect(
                        "mob.zombie.say",
                        pl.posX + Math.cos(a) * 12,
                        pl.posY,
                        pl.posZ + Math.sin(a) * 12,
                        1.0F,
                        0.5F));
            }
        }
    }

    /** The host's bed (or world spawn without a bed) if the host is near it, else the host's position. */
    private static double[] target(WorldServer world, EntityPlayer host, PartySettings s) {
        ChunkCoordinates bed = host.getBedLocation(0);
        ChunkCoordinates home = bed == null ? null
            : EntityPlayer.verifyRespawnCoordinates(world, bed, host.isSpawnForced(0));
        if (home == null) {
            home = world.getSpawnPoint();
        }
        double hx = home.posX + 0.5, hy = home.posY, hz = home.posZ + 0.5;
        if (host.getDistanceSq(hx, hy, hz) <= s.homeRange * s.homeRange) {
            return new double[] { hx, hy, hz };
        }
        return new double[] { host.posX, host.posY, host.posZ };
    }

    private static void end(WorldServer world, PartyData d, boolean sunrise) {
        PartyData.Party p = d.party;
        PartySettings s = Config.party;
        refreshMembers(world, p);
        for (List<EntityLiving> group : members.values()) {
            for (EntityLiving mob : group) {
                if (mob instanceof EntityEnderman) {
                    ((EntityEnderman) mob).setTarget(null); // calm again: only party endermen attack unprovoked
                }
            }
        }
        if (s.websVanishAtSunrise) {
            for (int[] w : p.webs) {
                if (world.blockExists(w[0], w[1], w[2]) && world.getBlock(w[0], w[1], w[2]) == Blocks.web) {
                    world.setBlockToAir(w[0], w[1], w[2]);
                }
            }
        }
        if (sunrise) {
            boolean diamonds = GearRules.diamondAllowedNow();
            for (Object o : world.playerEntities) {
                EntityPlayerMP pl = (EntityPlayerMP) o;
                UUID id = pl.getUniqueID();
                if (!p.guests.contains(id)) {
                    continue;
                }
                if (p.fallen.contains(id) || !pl.isEntityAlive()) {
                    send(
                        pl,
                        PREFIX + EnumChatFormatting.GRAY + "The sun rises. You fell tonight: no reward this week.");
                    continue;
                }
                int xp = PartyRules.rewardXp(p.size, s);
                int rolls = PartyRules.rewardRolls(p.size, s);
                pl.addExperience(xp);
                int items = 0;
                for (ItemStack stack : rollLoot(rolls, world.rand, diamonds)) {
                    items += stack.stackSize;
                    if (!pl.inventory.addItemStackToInventory(stack)) {
                        pl.dropPlayerItemWithRandomChoice(stack, false);
                    }
                }
                pl.inventoryContainer.detectAndSendChanges();
                send(
                    pl,
                    PREFIX + EnumChatFormatting.GOLD
                        + "The sun rises. You survived the Friday Night Party! "
                        + EnumChatFormatting.YELLOW
                        + "+"
                        + xp
                        + " XP, "
                        + items
                        + " items.");
            }
        } else {
            broadcast(world, PREFIX + EnumChatFormatting.GRAY + "The Friday Night Party was called off.");
        }
        SleeplessNights.LOG.info("Friday Night Party ended ({})", sunrise ? "sunrise" : "stopped");
        d.party = null;
        d.markDirty();
        members.clear();
        alive = 0;
    }

    static List<ItemStack> rollLoot(int rolls, Random rand, boolean diamonds) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            LootTable.Drop drop = Config.rewardLoot.roll(rand);
            if (drop == null) {
                break;
            }
            Object item = Item.itemRegistry.getObject(drop.item);
            if (item instanceof Item) {
                out.add(GearRules.filter(new ItemStack((Item) item, drop.count), diamonds));
            } else {
                SleeplessNights.LOG.warn("Unknown item in rewardLoot: {}", drop.item);
            }
        }
        return out;
    }

    // ---- Ticking ----

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null || server.worldServers.length == 0) {
            return;
        }
        WorldServer world = server.worldServerForDimension(0);
        PartyData d = PartyData.get(world);
        data = d;
        ticks++;
        if (d.party != null) {
            tickParty(world, d);
        } else if (ticks % 20 == 0) {
            maybeStart(server, world, d);
        }
        if (ticks % 20 == 0 && !d.repairs.isEmpty()) {
            tickRepairs(world, d);
        }
    }

    private static void maybeStart(MinecraftServer server, WorldServer world, PartyData d) {
        PartySettings s = Config.party;
        if (!s.enabled || world.playerEntities.isEmpty()) {
            return;
        }
        long today = (long) Math.floor(DifficultyManager.getDay(server));
        if (!PartyRules.isPartyDay(today, s) || d.lastPartyDay == today
            || !PartyRules.inStartWindow(timeOfDay(world), s)) {
            return;
        }
        d.lastPartyDay = today;
        begin(world, d, PartyRules.fridayNumber(today, s));
    }

    private void tickParty(WorldServer world, PartyData d) {
        PartyData.Party p = d.party;
        PartySettings s = Config.party;
        long now = world.getTotalWorldTime();
        // Sunrise; or the time was set to day (e.g. /time set day) during a party that began at night.
        if (now >= p.endTime || (p.nightStart && !PartyRules.isPartyNight(timeOfDay(world), s))) {
            end(world, d, true);
            return;
        }
        if (now >= p.nextSpawnTime && !p.allGroupsDone()) {
            spawnNextGroup(world, p, now, s);
            d.markDirty();
        }
        if (ticks % TARGET_TICKS == 0) {
            updateTarget(world, p, s);
            d.markDirty();
        }
        if (ticks % DRIVE_TICKS == 0) {
            refreshMembers(world, p);
            if (!p.attacking && readyToAttack(p, now, s)) {
                p.attacking = true;
                d.markDirty();
                if (Config.debugLogging) {
                    SleeplessNights.LOG.info("Party groups attack");
                }
            }
            drive(world, p, now, s);
        }
    }

    private static void spawnNextGroup(WorldServer world, PartyData.Party p, long now, PartySettings s) {
        for (PartyData.Group g : p.groups) {
            if (g.spawned || g.abandoned) {
                continue;
            }
            if (PartySpawner.spawnGroup(world, p, g)) {
                p.nextSpawnTime = now + s.groupSpawnIntervalTicks;
            } else if (now - p.id > (long) s.spawnRetrySeconds * 20) {
                g.abandoned = true;
                SleeplessNights.LOG.info("Party group {} found no hidden spawn spot and was skipped", g.index);
            } else {
                p.nextSpawnTime = now + 20;
            }
            return;
        }
    }

    private static void updateTarget(WorldServer world, PartyData.Party p, PartySettings s) {
        for (Object o : world.playerEntities) {
            EntityPlayer pl = (EntityPlayer) o;
            if (pl.getUniqueID()
                .equals(p.host)) {
                double[] t = target(world, pl, s);
                p.tx = t[0];
                p.ty = t[1];
                p.tz = t[2];
                return;
            }
        }
        // Host is away: keep the last target.
    }

    private static void refreshMembers(WorldServer world, PartyData.Party p) {
        members.clear();
        alive = 0;
        for (Object o : world.loadedEntityList) {
            if (o instanceof EntityLiving) {
                EntityLiving mob = (EntityLiving) o;
                if (mob.isEntityAlive() && PartyMob.partyId(mob) == p.id) {
                    members.computeIfAbsent(PartyMob.group(mob), k -> new ArrayList<>())
                        .add(mob);
                    alive++;
                }
            }
        }
    }

    private static boolean readyToAttack(PartyData.Party p, long now, PartySettings s) {
        if (now - p.id >= (long) s.gatherMaxSeconds * 20) {
            return true;
        }
        if (!p.allGroupsDone()) {
            return false;
        }
        for (PartyData.Group g : p.groups) {
            List<EntityLiving> list = members.get(g.index);
            if (!g.spawned || list == null || list.isEmpty()) {
                continue;
            }
            int there = 0;
            for (EntityLiving mob : list) {
                if (mob.getDistanceSq(g.rx, g.ry, g.rz) <= (RALLY_RADIUS + 1) * (RALLY_RADIUS + 1)) {
                    there++;
                }
            }
            if (there < Math.ceil(list.size() * s.gatherFraction)) {
                return false;
            }
        }
        return true;
    }

    /** Targets, old-style AI movement (spiders, endermen) and champion powers. */
    private static void drive(WorldServer world, PartyData.Party p, long now, PartySettings s) {
        for (List<EntityLiving> group : members.values()) {
            for (EntityLiving mob : group) {
                // Never go after villages: drop villager targets.
                if (mob.getAttackTarget() instanceof EntityVillager) {
                    mob.setAttackTarget(null);
                }
                boolean oldAi = mob instanceof EntitySpider || mob instanceof EntityEnderman;
                EntityPlayer victim = p.attacking ? nearestVictim(world, mob, s.attackRange) : null;
                if (oldAi) {
                    driveOldAi((EntityCreature) mob, p, victim);
                } else if (victim != null && mob.getAttackTarget() == null) {
                    mob.setAttackTarget(victim);
                }
                if (PartyMob.role(mob) == Role.CHAMPION) {
                    PartySpawner.applyPowerEffects(mob, s);
                    if (PartyMob.hasPower(mob, Power.WEBBER)) {
                        tryWeb(world, p, mob, now, s);
                    }
                }
            }
        }
    }

    /** Spiders and endermen use the old AI: steer them with paths and setTarget. */
    private static void driveOldAi(EntityCreature mob, PartyData.Party p, EntityPlayer victim) {
        if (victim != null) {
            if (mob.getEntityToAttack() == null) {
                mob.setTarget(victim);
            }
            return;
        }
        if (mob.getEntityToAttack() != null || mob.hasPath()) {
            return;
        }
        double[] goal;
        if (p.attacking) {
            goal = new double[] { p.tx, p.ty, p.tz };
        } else {
            int g = PartyMob.group(mob);
            if (g < 0 || g >= p.groups.size()) {
                return;
            }
            PartyData.Group group = p.groups.get(g);
            goal = new double[] { group.rx, group.ry, group.rz };
        }
        if (mob.getDistanceSq(goal[0], goal[1], goal[2]) > RALLY_RADIUS * RALLY_RADIUS) {
            mob.setPathToEntity(
                mob.worldObj.getEntityPathToXYZ(
                    mob,
                    (int) Math.floor(goal[0]),
                    (int) Math.floor(goal[1]),
                    (int) Math.floor(goal[2]),
                    32.0F,
                    true,
                    false,
                    false,
                    true));
        }
    }

    private static EntityPlayer nearestVictim(World world, EntityLiving mob, double range) {
        EntityPlayer best = null;
        double bestSq = range * range;
        for (Object o : world.playerEntities) {
            EntityPlayer pl = (EntityPlayer) o;
            if (!pl.isEntityAlive() || pl.capabilities.disableDamage) {
                continue; // creative players are left alone, like vanilla
            }
            double dsq = mob.getDistanceSqToEntity(pl);
            if (dsq < bestSq) {
                bestSq = dsq;
                best = pl;
            }
        }
        return best;
    }

    private static void tryWeb(WorldServer world, PartyData.Party p, EntityLiving mob, long now, PartySettings s) {
        Entity target = mob.getAttackTarget();
        if (!(target instanceof EntityPlayer) || now < PartyMob.webReadyAt(mob)
            || mob.getDistanceSqToEntity(target) > s.webRange * s.webRange
            || !mob.canEntityBeSeen(target)) {
            return;
        }
        int x = (int) Math.floor(target.posX), y = (int) Math.floor(target.boundingBox.minY + 0.01),
            z = (int) Math.floor(target.posZ);
        if (world.isAirBlock(x, y, z) && PartyBlocks.place(world, x, y, z, Blocks.web)) {
            p.webs.add(new int[] { x, y, z });
            PartyMob.setWebReadyAt(mob, now + s.webCooldownTicks);
            data.markDirty();
        }
    }

    private static void tickRepairs(WorldServer world, PartyData d) {
        List<PartyData.Repair> due = d.takeDueRepairs(world.getTotalWorldTime(), 64);
        for (PartyData.Repair r : due) {
            if (!world.blockExists(r.x, r.y, r.z)) {
                d.putBack(r); // chunk not loaded: try later
                continue;
            }
            if (world.getBlock(r.x, r.y, r.z) == r.placed && world.getBlockMetadata(r.x, r.y, r.z) == r.placedMeta) {
                world.setBlock(r.x, r.y, r.z, r.original, r.originalMeta, 3);
            }
        }
    }

    // ---- Events ----

    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote || !(event.entity instanceof EntityLiving) || PartyMob.tag(event.entity) == null) {
            return;
        }
        EntityLiving mob = (EntityLiving) event.entity;
        if (mob instanceof EntitySpider || mob instanceof EntityEnderman) {
            return; // old-style AI: steered by drive()
        }
        for (Object entry : mob.tasks.taskEntries) {
            if (((net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry) entry).action instanceof AIPartyMarch) {
                return; // already attached
            }
        }
        mob.tasks.addTask(1, new AIPartyMarch(mob));
        Role role = PartyMob.role(mob);
        if (mob instanceof EntityZombie && (role == Role.MINER || role == Role.BUILDER)) {
            mob.tasks.addTask(1, new AIPartyWorker(mob, role));
        }
    }

    @SubscribeEvent
    public void onAllowDespawn(LivingSpawnEvent.AllowDespawn event) {
        if (partyOf(event.entityLiving) != null) {
            event.setResult(Result.DENY);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP)) {
            return;
        }
        PartyData.Party p = party();
        UUID id = event.entityLiving.getUniqueID();
        if (p != null && p.guests.contains(id) && p.fallen.add(id)) {
            data.markDirty();
        }
    }

    @SubscribeEvent
    public void onSleep(PlayerSleepInBedEvent event) {
        EntityPlayer pl = event.entityPlayer;
        PartySettings s = Config.party;
        if (pl.worldObj.isRemote || !s.enabled || !s.blockSleep || pl.dimension != 0) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        PartyData d = PartyData.get(pl.worldObj);
        long today = (long) Math.floor(DifficultyManager.getDay(server));
        int tod = timeOfDay(pl.worldObj);
        boolean partyNightAhead = PartyRules.isPartyDay(today, s) && d.lastPartyDay != today
            && tod >= 12000
            && tod < s.latestStartTime;
        if (d.party != null || partyNightAhead) {
            event.result = EntityPlayer.EnumStatus.NOT_SAFE;
            send(
                pl,
                PREFIX + EnumChatFormatting.GRAY
                    + EnumChatFormatting.ITALIC
                    + "Not tonight. It's Friday... they're coming.");
        }
    }

    /** Vengeance: reflects part of melee damage back to the attacker. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if (victim.worldObj.isRemote || PartyMob.role(victim) != Role.CHAMPION
            || !PartyMob.hasPower(victim, Power.VENGEANCE)) {
            return;
        }
        Entity attacker = event.source.getEntity();
        if (attacker instanceof EntityLivingBase && attacker == event.source.getSourceOfDamage()
            && !"thorns".equals(event.source.getDamageType())) {
            float back = (float) (event.ammount * Config.party.vengeanceFraction);
            if (back > 0) {
                attacker.attackEntityFrom(DamageSource.causeThornsDamage(victim), back);
            }
        }
    }

    /** Champions: better loot and extra XP. */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        EntityLivingBase mob = event.entityLiving;
        if (mob.worldObj.isRemote || PartyMob.role(mob) != Role.CHAMPION || !event.recentlyHit) {
            return;
        }
        PartySettings s = Config.party;
        boolean diamonds = GearRules.diamondAllowedNow();
        for (ItemStack stack : rollLoot(s.championLootRolls, mob.getRNG(), diamonds)) {
            event.drops
                .add(new net.minecraft.entity.item.EntityItem(mob.worldObj, mob.posX, mob.posY, mob.posZ, stack));
        }
        int xp = s.championXp;
        while (xp > 0) {
            int orb = EntityXPOrb.getXPSplit(xp);
            xp -= orb;
            mob.worldObj.spawnEntityInWorld(new EntityXPOrb(mob.worldObj, mob.posX, mob.posY, mob.posZ, orb));
        }
    }

    /** Repair mode: party creeper damage is recorded for repair instead of dropping blocks. */
    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate event) {
        Entity exploder = event.explosion.exploder;
        if (event.world.isRemote || !Config.party.repairEnabled || exploder == null || partyOf(exploder) == null) {
            return;
        }
        // Repair mode: remove the blocks ourselves (no drops), record them, and keep tile entities intact.
        List<ChunkPosition> blocks = event.getAffectedBlocks();
        for (ChunkPosition pos : blocks) {
            int x = pos.chunkPosX, y = pos.chunkPosY, z = pos.chunkPosZ;
            net.minecraft.block.Block b = event.world.getBlock(x, y, z);
            int meta = event.world.getBlockMetadata(x, y, z);
            if (b.getMaterial() == net.minecraft.block.material.Material.air || b.hasTileEntity(meta)
                || b.getBlockHardness(event.world, x, y, z) < 0) {
                continue;
            }
            PartyBlocks.record(event.world, x, y, z, b, meta, Blocks.air, 0);
            event.world.setBlock(x, y, z, Blocks.air, 0, 2);
        }
        blocks.clear();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) {
            data = null;
            members.clear();
            alive = 0;
        }
    }

    // ---- Chat ----

    private static void broadcast(WorldServer world, String text) {
        for (Object o : world.playerEntities) {
            send((EntityPlayer) o, text);
        }
    }

    private static void send(EntityPlayer player, String text) {
        player.addChatMessage(new ChatComponentText(text));
    }
}

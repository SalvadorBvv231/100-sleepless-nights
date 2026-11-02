package com.sansslonce.sleepless100.party;

import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.SleeplessNights;
import com.sansslonce.sleepless100.core.PartyRules;
import com.sansslonce.sleepless100.core.PartyRules.MobPlan;
import com.sansslonce.sleepless100.core.PartyRules.Power;
import com.sansslonce.sleepless100.core.PartyRules.Role;
import com.sansslonce.sleepless100.core.PartySettings;

import cpw.mods.fml.common.eventhandler.Event.Result;

/** Finds hidden spawn spots for party groups and spawns their mobs. */
final class PartySpawner {

    private static final UUID BULWARK_ID = UUID.fromString("5b0e7f5e-3c1a-4c55-9d43-1a0f6c2b8e10");
    private static final int SPOT_TRIES = 16;
    private static final int VERTICAL_SEARCH = 16;
    private static final double VIEW_COS = 0.5; // 60 degrees either side of where the player looks
    private static final int POTION_TICKS = 1_000_000;

    private enum Sight {
        HIDDEN,
        OUT_OF_VIEW,
        VISIBLE
    }

    private PartySpawner() {}

    /** Spawns one group. Returns false if no acceptable spot was found (try again later). */
    static boolean spawnGroup(WorldServer world, PartyData.Party party, PartyData.Group group) {
        PartySettings s = Config.party;
        Random rand = world.rand;
        int[] spot = findSpot(world, party, group.angle, rand, s);
        if (spot == null) {
            return false;
        }
        group.sx = spot[0] + 0.5;
        group.sy = spot[1];
        group.sz = spot[2] + 0.5;
        double[] rally = rallyPoint(world, party, spot, s);
        group.rx = rally[0];
        group.ry = rally[1];
        group.rz = rally[2];

        int digLevel = PartyRules.digLevel(party.friday, s);
        int spawned = 0;
        for (MobPlan plan : group.plans) {
            if (spawnMob(world, party, group, plan, spot, digLevel, rand, s)) {
                spawned++;
            }
        }
        group.spawned = true;
        if (Config.debugLogging) {
            SleeplessNights.LOG.info(
                "Party group {} spawned {} mobs at {},{},{} (rally {},{},{})",
                group.index,
                spawned,
                spot[0],
                spot[1],
                spot[2],
                (int) rally[0],
                (int) rally[1],
                (int) rally[2]);
        }
        return true;
    }

    private static boolean spawnMob(WorldServer world, PartyData.Party party, PartyData.Group group, MobPlan plan,
        int[] spot, int digLevel, Random rand, PartySettings s) {
        EntityLiving mob = create(plan.type, world);
        int[] pos = memberSpot(world, spot, rand);
        float x = pos[0] + 0.5F, y = pos[1], z = pos[2] + 0.5F;
        mob.setLocationAndAngles(x, y, z, rand.nextFloat() * 360.0F, 0.0F);
        if (ForgeEventFactory.canEntitySpawn(mob, world, x, y, z) == Result.DENY) {
            return false; // another mod said no
        }
        int blocks = plan.role == Role.BUILDER ? s.builderBlocks : 0;
        PartyMob.mark(mob, party.id, group.index, plan, blocks);
        world.spawnEntityInWorld(mob);
        if (!ForgeEventFactory.doSpecialSpawn(mob, world, x, y, z)) {
            mob.onSpawnWithEgg(null); // vanilla equipment, babies, etc.
        }

        // Role gear goes on after vanilla's; the tier gear (milestone 2) follows at the end of the tick.
        if (plan.role == Role.MINER) {
            mob.setCurrentItemOrArmor(0, new ItemStack(digLevel >= 3 ? Items.iron_pickaxe : Items.stone_pickaxe));
        } else if (plan.role == Role.BUILDER && blocks > 0) {
            mob.setCurrentItemOrArmor(0, new ItemStack(Blocks.dirt));
        } else if (plan.role == Role.CHAMPION) {
            makeChampion(mob, plan, s);
        }
        return true;
    }

    private static EntityLiving create(PartyRules.MobType type, World world) {
        switch (type) {
            case SKELETON:
                return new EntitySkeleton(world);
            case SPIDER:
                return new EntitySpider(world);
            case WITCH:
                return new EntityWitch(world);
            case CREEPER:
                return new EntityCreeper(world);
            case ENDERMAN:
                return new EntityEnderman(world);
            case ZOMBIE:
            default:
                return new EntityZombie(world);
        }
    }

    static void makeChampion(EntityLiving mob, MobPlan plan, PartySettings s) {
        StringBuilder powers = new StringBuilder();
        for (Power p : plan.powers) {
            if (powers.length() > 0) {
                powers.append(", ");
            }
            String n = p.name()
                .toLowerCase(Locale.ROOT);
            powers.append(Character.toUpperCase(n.charAt(0)))
                .append(n.substring(1));
        }
        String kind = mob instanceof EntitySkeleton ? "Skeleton" : "Zombie";
        mob.setCustomNameTag("§6Champion " + kind + " §7[" + powers + "]");
        for (int slot = 0; slot <= 4; slot++) {
            mob.setEquipmentDropChance(slot, s.championDropChance);
        }
        applyPowerEffects(mob, s);
    }

    /** Regen and Bulwark potion effects (also re-applied if they ever wore off). */
    static void applyPowerEffects(EntityLiving mob, PartySettings s) {
        if (PartyMob.hasPower(mob, Power.REGEN) && !mob.isPotionActive(Potion.regeneration)) {
            mob.addPotionEffect(new PotionEffect(Potion.regeneration.id, POTION_TICKS, 0, true));
        }
        if (PartyMob.hasPower(mob, Power.BULWARK)) {
            if (!mob.isPotionActive(Potion.resistance)) {
                mob.addPotionEffect(new PotionEffect(Potion.resistance.id, POTION_TICKS, 0, true));
            }
            IAttributeInstance kb = mob.getEntityAttribute(SharedMonsterAttributes.knockbackResistance);
            if (kb != null && kb.getModifier(BULWARK_ID) == null && s.bulwarkKnockbackResistance > 0) {
                kb.applyModifier(
                    new AttributeModifier(BULWARK_ID, "100 Sleepless Nights bulwark", s.bulwarkKnockbackResistance, 0));
            }
        }
    }

    // ---- Spots ----

    private static int[] findSpot(WorldServer world, PartyData.Party party, double angle, Random rand,
        PartySettings s) {
        double min = Math.min(s.minSpawnDistance, s.maxSpawnDistance);
        double max = Math.max(s.minSpawnDistance, s.maxSpawnDistance);
        int[] outOfView = null;
        for (int t = 0; t < SPOT_TRIES; t++) {
            double a = angle + (rand.nextDouble() - 0.5) * Math.toRadians(30);
            double d = min + rand.nextDouble() * (max - min);
            int x = (int) Math.floor(party.tx + Math.cos(a) * d);
            int z = (int) Math.floor(party.tz + Math.sin(a) * d);
            if (!world.getChunkProvider()
                .chunkExists(x >> 4, z >> 4)) {
                continue; // never load chunks for this
            }
            int y = findGround(world, x, (int) Math.floor(party.ty), z, VERTICAL_SEARCH, 3);
            if (y < 0 || isLeavesOrLog(world, x, y - 1, z)
                || world.getSavedLightValue(EnumSkyBlock.Block, x, y, z) > s.maxSpawnBlockLight
                || tooCloseToPlayer(world, x, y, z, min * 0.75)) {
                continue;
            }
            Sight sight = sight(world, x, y, z);
            if (sight == Sight.HIDDEN) {
                return new int[] { x, y, z };
            }
            if (sight == Sight.OUT_OF_VIEW && outOfView == null) {
                outOfView = new int[] { x, y, z };
            }
        }
        return outOfView;
    }

    /** Nearest standable y to {@code nearY} within the search range, or -1. */
    static int findGround(World world, int x, int nearY, int z, int range, int height) {
        for (int i = 0; i <= range * 2; i++) {
            int dy = (i + 1) / 2 * (i % 2 == 0 ? -1 : 1); // 0, +1, -1, +2, -2, ...
            int y = nearY + dy;
            if (y >= 1 && y + height < world.getHeight() && standable(world, x, y, z, height)) {
                return y;
            }
        }
        return -1;
    }

    static boolean standable(World world, int x, int y, int z, int height) {
        if (!World.doesBlockHaveSolidTopSurface(world, x, y - 1, z)) {
            return false;
        }
        for (int h = 0; h < height; h++) {
            Block b = world.getBlock(x, y + h, z);
            if (b.getMaterial()
                .blocksMovement()
                || b.getMaterial()
                    .isLiquid()) {
                return false;
            }
        }
        return true;
    }

    private static int[] memberSpot(World world, int[] spot, Random rand) {
        for (int t = 0; t < 6; t++) {
            int x = spot[0] + rand.nextInt(5) - 2;
            int z = spot[2] + rand.nextInt(5) - 2;
            int y = findGround(world, x, spot[1], z, 2, 2);
            if (y >= 0) {
                return new int[] { x, y, z };
            }
        }
        return spot;
    }

    private static double[] rallyPoint(World world, PartyData.Party party, int[] spot, PartySettings s) {
        double dx = spot[0] + 0.5 - party.tx, dz = spot[2] + 0.5 - party.tz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > s.rallyDistance + 4 && dist > 0) {
            int x = (int) Math.floor(party.tx + dx / dist * s.rallyDistance);
            int z = (int) Math.floor(party.tz + dz / dist * s.rallyDistance);
            int y = findGround(world, x, (spot[1] + (int) Math.floor(party.ty)) / 2, z, 8, 2);
            if (y >= 0) {
                return new double[] { x + 0.5, y, z + 0.5 };
            }
        }
        return new double[] { spot[0] + 0.5, spot[1], spot[2] + 0.5 };
    }

    private static boolean tooCloseToPlayer(World world, int x, int y, int z, double min) {
        for (Object o : world.playerEntities) {
            if (((EntityPlayer) o).getDistanceSq(x + 0.5, y, z + 0.5) < min * min) {
                return true;
            }
        }
        return false;
    }

    /** Hidden if no player has a clear line of sight; out of view if those who do are looking away. */
    private static Sight sight(World world, int x, int y, int z) {
        Sight result = Sight.HIDDEN;
        for (Object o : world.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            double ex = p.posX, ey = p.posY + 1.62, ez = p.posZ;
            double tx = x + 0.5, ty = y + 1.5, tz = z + 0.5;
            double dx = tx - ex, dy = ty - ey, dz = tz - ez;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 96) {
                continue;
            }
            // rayTraceBlocks changes its first vector, so use fresh ones.
            MovingObjectPosition hit = world
                .rayTraceBlocks(Vec3.createVectorHelper(ex, ey, ez), Vec3.createVectorHelper(tx, ty, tz));
            if (hit != null) {
                continue; // something solid in between
            }
            Vec3 look = p.getLook(1.0F);
            double cos = (look.xCoord * dx + look.yCoord * dy + look.zCoord * dz) / Math.max(1e-6, len);
            if (cos > VIEW_COS) {
                return Sight.VISIBLE;
            }
            result = Sight.OUT_OF_VIEW;
        }
        return result;
    }

    private static boolean isLeavesOrLog(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        return block.isLeaves(world, x, y, z) || block.isWood(world, x, y, z);
    }
}

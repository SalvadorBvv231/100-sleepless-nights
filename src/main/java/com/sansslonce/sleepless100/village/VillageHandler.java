package com.sansslonce.sleepless100.village;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.village.Village;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.DifficultyManager;
import com.sansslonce.sleepless100.SleeplessNights;
import com.sansslonce.sleepless100.core.VillageRules;
import com.sansslonce.sleepless100.mobs.GearRules;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Villages: more iron golems (and one extra from day 50), golems that grow with the tiers, villagers that go
 * indoors earlier from day 100, and the diamond rule for villager trades. Registered on the Forge and FML buses.
 */
public class VillageHandler {

    private static final UUID GOLEM_HEALTH_ID = UUID.fromString("5b0e7f5e-3c1a-4c55-9d43-1a0f6c2b8e20");
    private static final int SPAWN_CHECK_TICKS = 20;
    private static final int GOLEM_UPDATE_TICKS = 600;

    private long ticks;

    private static double day() {
        return DifficultyManager.getDay(MinecraftServer.getServer());
    }

    private static double tierProgress(double day) {
        return VillageRules.tierProgress(day, Config.tierStartDays, Config.effects.finalTierRampDays);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null || server.worldServers.length == 0) {
            return;
        }
        ticks++;
        if (ticks % SPAWN_CHECK_TICKS == 0) {
            WorldServer world = server.worldServerForDimension(0);
            if (world.villageCollectionObj != null) {
                spawnGolems(world, day());
            }
        }
        if (ticks % GOLEM_UPDATE_TICKS == 0) {
            double progress = tierProgress(day());
            for (WorldServer world : server.worldServers) {
                for (Object o : world.loadedEntityList) {
                    if (o instanceof EntityIronGolem) {
                        updateGolem((EntityIronGolem) o, progress);
                    }
                }
            }
        }
    }

    /** Extra golem rolls on top of vanilla's, with vanilla's placement rules. */
    private static void spawnGolems(WorldServer world, double day) {
        Random rand = world.rand;
        for (Village village : world.villageCollectionObj.getVillageList()) {
            ChunkCoordinates c = village.getCenter();
            if (!world.blockExists(c.posX, c.posY, c.posZ)) {
                continue;
            }
            int r = village.getVillageRadius();
            List<?> golems = world.getEntitiesWithinAABB(
                EntityIronGolem.class,
                AxisAlignedBB.getBoundingBox(c.posX - r, c.posY - 4, c.posZ - r, c.posX + r, c.posY + 4, c.posZ + r));
            double chance = VillageRules.extraSpawnChancePerTick(
                golems.size(),
                village.getNumVillagers(),
                village.getNumVillageDoors(),
                day,
                Config.villages) * SPAWN_CHECK_TICKS;
            if (chance > 0 && rand.nextDouble() < chance) {
                spawnGolem(world, village, rand);
            }
        }
    }

    /** Same spot search as vanilla's Village.tryGetIronGolemSpawningLocation (10 tries, 2x4x2 space). */
    private static void spawnGolem(World world, Village village, Random rand) {
        ChunkCoordinates c = village.getCenter();
        for (int t = 0; t < 10; t++) {
            int x = c.posX + rand.nextInt(16) - 8;
            int y = c.posY + rand.nextInt(6) - 3;
            int z = c.posZ + rand.nextInt(16) - 8;
            if (village.isInRange(x, y, z) && validGolemSpot(world, x, y, z)) {
                EntityIronGolem golem = new EntityIronGolem(world);
                golem.setPosition(x + 0.5, y, z + 0.5);
                world.spawnEntityInWorld(golem);
                if (Config.debugLogging) {
                    SleeplessNights.LOG.info("Extra iron golem spawned at {},{},{}", x, y, z);
                }
                return;
            }
        }
    }

    private static boolean validGolemSpot(World world, int x, int y, int z) {
        if (!World.doesBlockHaveSolidTopSurface(world, x, y - 1, z)) {
            return false;
        }
        for (int i = x - 1; i < x + 1; i++) {
            for (int j = y; j < y + 4; j++) {
                for (int k = z - 1; k < z + 1; k++) {
                    if (world.getBlock(i, j, k)
                        .isNormalCube()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Golem max health follows the tiers; when it rises the golem is healed by the difference. */
    private static void updateGolem(EntityIronGolem golem, double progress) {
        IAttributeInstance hp = golem.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        double bonus = VillageRules.golemHealthBonus(progress, Config.villages);
        AttributeModifier old = hp.getModifier(GOLEM_HEALTH_ID);
        if (old != null && Math.abs(old.getAmount() - bonus) < 1e-3 || old == null && bonus <= 0) {
            return;
        }
        float before = golem.getMaxHealth();
        if (old != null) {
            hp.removeModifier(old);
        }
        if (bonus > 0) {
            hp.applyModifier(new AttributeModifier(GOLEM_HEALTH_ID, "100 Sleepless Nights golem health", bonus, 1));
        }
        float after = golem.getMaxHealth();
        if (after > before) {
            golem.heal(after - before);
        } else if (golem.getHealth() > after) {
            golem.setHealth(after);
        }
    }

    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote) {
            return;
        }
        if (event.entity instanceof EntityIronGolem) {
            updateGolem((EntityIronGolem) event.entity, tierProgress(day()));
        } else if (event.entity instanceof EntityVillager) {
            EntityVillager villager = (EntityVillager) event.entity;
            for (Object entry : villager.tasks.taskEntries) {
                if (((EntityAITasks.EntityAITaskEntry) entry).action instanceof AIVillagerEarlyIndoors) {
                    return;
                }
            }
            // Same priorities as vanilla's EntityAIMoveIndoors (2) and EntityAIRestrictOpenDoor (3).
            villager.tasks.addTask(2, new AIVillagerEarlyIndoors(villager));
            villager.tasks.addTask(3, new AIVillagerEarlyRestrictDoor(villager));
        }
    }

    /** Golem damage grows with the tiers (vanilla golems ignore the attack attribute, so scale the hit). */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!event.entityLiving.worldObj.isRemote && event.source.getEntity() instanceof EntityIronGolem) {
            event.ammount *= (float) VillageRules.golemDamageMultiplier(tierProgress(day()), Config.villages);
        }
    }

    /** Diamond rule: before the diamond day, diamond gear in a villager's trades becomes iron. */
    @SubscribeEvent
    public void onInteract(EntityInteractEvent event) {
        if (event.entityPlayer.worldObj.isRemote || !(event.target instanceof EntityVillager)
            || GearRules.diamondAllowedNow()) {
            return;
        }
        MerchantRecipeList list = ((EntityVillager) event.target).getRecipes(event.entityPlayer);
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            MerchantRecipe r = (MerchantRecipe) list.get(i);
            ItemStack sell = r.getItemToSell();
            if (GearRules.isDiamondGear(sell)) {
                list.set(i, new MerchantRecipe(r.getItemToBuy(), r.getSecondItemToBuy(), GearRules.downgrade(sell)));
            }
        }
    }
}

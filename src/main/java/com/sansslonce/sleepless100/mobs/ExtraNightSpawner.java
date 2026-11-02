package com.sansslonce.sleepless100.mobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.DifficultyManager;
import com.sansslonce.sleepless100.SleeplessNights;
import com.sansslonce.sleepless100.core.MobEffects;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Tier 1: extra hostile spawn passes at night, on top of vanilla's own spawning.
 * <p>
 * Each pass is a copy of vanilla's hostile spawning (SpawnerAnimals.findChunksForSpawning) with the same
 * rules: same mob mix from the biome, same light/solid-block/slab checks, Forge spawn events, the 24-block
 * distance from players and world spawn. Differences: the hostile mob cap is raised by the tier-1 amount,
 * and mobs never spawn standing on leaves or logs.
 */
public class ExtraNightSpawner {

    private static final int CHUNK_RADIUS = 8;
    private static final int TRIES_PER_CHUNK = 3;
    private static final int TRIES_PER_GROUP = 4;

    private long debugSpawned;
    private long debugLastLogTime;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        if (world.provider.hasNoSky || world.isDaytime()
            || world.difficultySetting == EnumDifficulty.PEACEFUL
            || world.playerEntities.isEmpty()
            || !world.getGameRules()
                .getGameRuleBooleanValue("doMobSpawning")) {
            return;
        }
        MobEffects fx = DifficultyManager.currentEffects(MinecraftServer.getServer());
        if (fx.extraNightSpawns <= 0) {
            return;
        }
        int passes = MobEffects.extraPassesThisTick(fx.extraNightSpawns, world.rand.nextDouble());
        for (int i = 0; i < passes; i++) {
            debugSpawned += spawnPass(world, fx.extraNightSpawns);
        }
        if (Config.debugLogging && world.getTotalWorldTime() - debugLastLogTime >= 600) {
            debugLastLogTime = world.getTotalWorldTime();
            SleeplessNights.LOG.info(
                "Extra night spawns in {} over the last 30 s: {} (extra {}%)",
                world.provider.getDimensionName(),
                debugSpawned,
                Math.round(fx.extraNightSpawns * 100));
            debugSpawned = 0;
        }
    }

    /** One vanilla-style hostile spawning pass with a raised cap. Returns how many mobs spawned. */
    private static int spawnPass(WorldServer world, double extra) {
        Set<ChunkCoordIntPair> edge = new HashSet<>();
        Set<ChunkCoordIntPair> eligible = new HashSet<>();
        for (Object o : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) o;
            int cx = MathHelper.floor_double(player.posX / 16.0D);
            int cz = MathHelper.floor_double(player.posZ / 16.0D);
            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                    ChunkCoordIntPair pos = new ChunkCoordIntPair(cx + dx, cz + dz);
                    boolean isEdge = Math.abs(dx) == CHUNK_RADIUS || Math.abs(dz) == CHUNK_RADIUS;
                    if (isEdge) {
                        edge.add(pos);
                    } else {
                        eligible.add(pos);
                    }
                }
            }
        }
        edge.removeAll(eligible);
        int chunkCount = eligible.size() + edge.size();

        EnumCreatureType type = EnumCreatureType.monster;
        int vanillaCap = type.getMaxNumberOfCreature() * chunkCount / 256;
        if (world.countEntities(type, true) > MobEffects.raisedMobCap(vanillaCap, extra)) {
            return 0;
        }

        List<ChunkCoordIntPair> chunks = new ArrayList<>(eligible);
        Collections.shuffle(chunks, world.rand);
        ChunkCoordinates spawn = world.getSpawnPoint();
        int spawned = 0;

        chunkLoop: for (ChunkCoordIntPair chunk : chunks) {
            int x0 = chunk.chunkXPos * 16 + world.rand.nextInt(16);
            int z0 = chunk.chunkZPos * 16 + world.rand.nextInt(16);
            Chunk c = world.getChunkFromChunkCoords(chunk.chunkXPos, chunk.chunkZPos);
            int y0 = world.rand.nextInt(c == null ? world.getActualHeight() : c.getTopFilledSegment() + 16 - 1);
            Block start = world.getBlock(x0, y0, z0);
            if (start.isNormalCube() || start.getMaterial() != type.getCreatureMaterial()) {
                continue;
            }

            int groupSize = 0;
            for (int attempt = 0; attempt < TRIES_PER_CHUNK; attempt++) {
                int x = x0, y = y0, z = z0;
                BiomeGenBase.SpawnListEntry entry = null;
                IEntityLivingData groupData = null;
                for (int t = 0; t < TRIES_PER_GROUP; t++) {
                    x += world.rand.nextInt(6) - world.rand.nextInt(6);
                    z += world.rand.nextInt(6) - world.rand.nextInt(6);
                    if (!SpawnerAnimals.canCreatureTypeSpawnAtLocation(type, world, x, y, z)
                        || isLeavesOrLog(world, x, y - 1, z)) {
                        continue;
                    }
                    float fx = x + 0.5F, fy = y, fz = z + 0.5F;
                    if (world.getClosestPlayer(fx, fy, fz, 24.0D) != null) {
                        continue;
                    }
                    float sx = fx - spawn.posX, sy = fy - spawn.posY, sz = fz - spawn.posZ;
                    if (sx * sx + sy * sy + sz * sz < 576.0F) {
                        continue;
                    }
                    if (entry == null) {
                        entry = world.spawnRandomCreature(type, x, y, z); // vanilla biome mob mix
                        if (entry == null) {
                            break;
                        }
                    }
                    EntityLiving mob;
                    try {
                        mob = (EntityLiving) entry.entityClass.getConstructor(World.class)
                            .newInstance(world);
                    } catch (Exception e) {
                        SleeplessNights.LOG.warn("Could not create {}", entry.entityClass, e);
                        return spawned;
                    }
                    mob.setLocationAndAngles(fx, fy, fz, world.rand.nextFloat() * 360.0F, 0.0F);
                    Result canSpawn = ForgeEventFactory.canEntitySpawn(mob, world, fx, fy, fz);
                    if (canSpawn == Result.ALLOW || (canSpawn == Result.DEFAULT && mob.getCanSpawnHere())) {
                        world.spawnEntityInWorld(mob);
                        if (!ForgeEventFactory.doSpecialSpawn(mob, world, fx, fy, fz)) {
                            groupData = mob.onSpawnWithEgg(groupData); // vanilla equipment, babies, etc.
                        }
                        spawned++;
                        groupSize++;
                        if (groupSize >= ForgeEventFactory.getMaxSpawnPackSize(mob)) {
                            continue chunkLoop; // pack is full, like vanilla
                        }
                    }
                }
            }
        }
        return spawned;
    }

    private static boolean isLeavesOrLog(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        return block.isLeaves(world, x, y, z) || block.isWood(world, x, y, z);
    }
}

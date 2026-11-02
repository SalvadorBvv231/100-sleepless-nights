package com.sansslonce.sleepless100.party;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.SleeplessNights;
import com.sansslonce.sleepless100.core.PartyRules;

/** What party mobs may dig and place, and how those changes are dropped or recorded for repair. */
final class PartyBlocks {

    private static List<Set<Block>> digLists;

    private PartyBlocks() {}

    static boolean griefing(World world) {
        return world.getGameRules()
            .getGameRuleBooleanValue("mobGriefing");
    }

    /** Block lists from the config, resolved once (after all mods registered their blocks). */
    private static List<Set<Block>> lists() {
        if (digLists == null) {
            List<Set<Block>> out = new ArrayList<>();
            for (String[] names : Config.minerBlocks) {
                Set<Block> set = new HashSet<>();
                if (names != null) {
                    for (String name : names) {
                        Block b = Block.getBlockFromName(name.trim());
                        if (b == null || b == Blocks.air) {
                            SleeplessNights.LOG.warn("Unknown block in miner list: {}", name);
                        } else {
                            set.add(b);
                        }
                    }
                }
                out.add(set);
            }
            digLists = out;
        }
        return digLists;
    }

    /** Never dug, whatever the config says. */
    static boolean forbidden(World world, int x, int y, int z, Block block, int meta) {
        if (block == Blocks.obsidian || block == Blocks.iron_block
            || block == Blocks.bedrock
            || block instanceof BlockOre
            || block instanceof BlockRedstoneOre
            || block.hasTileEntity(meta)
            || block.getBlockHardness(world, x, y, z) < 0) {
            return true;
        }
        Item item = Item.getItemFromBlock(block);
        if (item != null) {
            for (int id : OreDictionary.getOreIDs(new ItemStack(item, 1, block.damageDropped(meta)))) {
                if (OreDictionary.getOreName(id)
                    .startsWith("ore")) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean canDig(World world, int x, int y, int z, int digLevel) {
        if (digLevel <= 0 || !griefing(world)) {
            return false;
        }
        Block block = world.getBlock(x, y, z);
        if (block.getMaterial() == Material.air || block.getMaterial()
            .isLiquid()) {
            return false;
        }
        int meta = world.getBlockMetadata(x, y, z);
        if (forbidden(world, x, y, z, block, meta)) {
            return false;
        }
        List<Set<Block>> lists = lists();
        for (int i = 0; i < Math.min(digLevel, lists.size()); i++) {
            if (lists.get(i)
                .contains(block)) {
                return true;
            }
        }
        return false;
    }

    /** True if a mob can't walk through this block. */
    static boolean solid(World world, int x, int y, int z) {
        return world.getBlock(x, y, z)
            .getMaterial()
            .blocksMovement();
    }

    static boolean replaceable(World world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        return b.getMaterial() == Material.air || b.getMaterial()
            .isLiquid() || b.isReplaceable(world, x, y, z);
    }

    /** Digs a block: drops it as items (or records it for repair), with the vanilla break effect. */
    static void dig(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (meta << 12));
        if (Config.party.repairEnabled) {
            record(world, x, y, z, block, meta, Blocks.air, 0);
        } else {
            List<ItemStack> drops = block.getDrops(world, x, y, z, meta, 0);
            if (drops.isEmpty() && Item.getItemFromBlock(block) != null) {
                // Glass and panes drop nothing in vanilla; party digging drops the block itself.
                drops = new ArrayList<>();
                drops.add(new ItemStack(Item.getItemFromBlock(block), 1, block.damageDropped(meta)));
            }
            for (ItemStack stack : drops) {
                drop(world, x, y, z, stack);
            }
        }
        world.setBlock(x, y, z, Blocks.air, 0, 3);
    }

    /** Places a block for a party mob (builder dirt, webs) and records it for repair if that's on. */
    static boolean place(World world, int x, int y, int z, Block block) {
        if (!griefing(world) || !replaceable(world, x, y, z)) {
            return false;
        }
        Block old = world.getBlock(x, y, z);
        int oldMeta = world.getBlockMetadata(x, y, z);
        if (Config.party.repairEnabled) {
            record(world, x, y, z, old, oldMeta, block, 0);
        }
        world.setBlock(x, y, z, block, 0, 3);
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, block.stepSound.func_150496_b(), 1.0F, 0.8F);
        return true;
    }

    static void record(World world, int x, int y, int z, Block original, int meta, Block placed, int placedMeta) {
        long due = world.getTotalWorldTime() + PartyRules.repairDelayTicks(Config.party);
        PartyData.get(world)
            .recordRepair(x, y, z, original, meta, placed, placedMeta, due);
    }

    private static void drop(World world, int x, int y, int z, ItemStack stack) {
        EntityItem item = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack);
        item.delayBeforeCanPickup = 10;
        world.spawnEntityInWorld(item);
    }
}

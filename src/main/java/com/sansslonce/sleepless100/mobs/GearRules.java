package com.sansslonce.sleepless100.mobs;

import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.server.MinecraftServer;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.DifficultyManager;
import com.sansslonce.sleepless100.core.MobEffects;

/**
 * The diamond rule: no diamond armor or weapons before {@code diamondGearMinDay}, from any mob or mod source.
 * Diamond pieces become their iron version, keeping enchantments and names.
 */
public final class GearRules {

    private static final String DIAMOND_TOOL = "EMERALD"; // vanilla's name for the diamond tool material

    private GearRules() {}

    public static boolean diamondAllowedNow() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null || MobEffects.diamondAllowed(DifficultyManager.getDay(server), Config.effects);
    }

    /** Diamond armor, swords and tools (also modded items made of the vanilla diamond material). */
    public static boolean isDiamondGear(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof ItemArmor) {
            return ((ItemArmor) item).getArmorMaterial() == ItemArmor.ArmorMaterial.DIAMOND;
        }
        if (item instanceof ItemSword) {
            return DIAMOND_TOOL.equals(((ItemSword) item).getToolMaterialName());
        }
        if (item instanceof ItemTool) {
            return DIAMOND_TOOL.equals(((ItemTool) item).getToolMaterialName());
        }
        if (item instanceof ItemHoe) {
            return DIAMOND_TOOL.equals(((ItemHoe) item).getToolMaterialName());
        }
        return false;
    }

    /** The iron version of a diamond piece (same count, enchantments, name; wear scaled), or the stack itself. */
    public static ItemStack downgrade(ItemStack stack) {
        if (!isDiamondGear(stack)) {
            return stack;
        }
        Item item = stack.getItem();
        Item iron;
        if (item instanceof ItemArmor) {
            int type = ((ItemArmor) item).armorType; // 0 helmet, 1 chestplate, 2 leggings, 3 boots
            iron = type == 0 ? Items.iron_helmet
                : type == 1 ? Items.iron_chestplate : type == 2 ? Items.iron_leggings : Items.iron_boots;
        } else if (item instanceof ItemPickaxe) {
            iron = Items.iron_pickaxe;
        } else if (item instanceof ItemAxe) {
            iron = Items.iron_axe;
        } else if (item instanceof ItemSpade) {
            iron = Items.iron_shovel;
        } else if (item instanceof ItemHoe) {
            iron = Items.iron_hoe;
        } else {
            iron = Items.iron_sword;
        }
        ItemStack result = new ItemStack(iron, stack.stackSize);
        if (stack.hasTagCompound()) {
            result.setTagCompound(
                (net.minecraft.nbt.NBTTagCompound) stack.getTagCompound()
                    .copy());
        }
        int oldMax = stack.getMaxDamage();
        if (oldMax > 0 && stack.getItemDamage() > 0) {
            result.setItemDamage((int) ((long) stack.getItemDamage() * result.getMaxDamage() / oldMax));
        }
        return result;
    }

    /** Applies the rule to an item this mod is about to give out. */
    public static ItemStack filter(ItemStack stack, boolean diamondAllowed) {
        return diamondAllowed ? stack : downgrade(stack);
    }

    /** Applies the rule to everything a mob holds or wears. */
    public static void enforce(EntityLiving mob, boolean diamondAllowed) {
        if (diamondAllowed) {
            return;
        }
        for (int slot = 0; slot <= 4; slot++) {
            ItemStack stack = mob.getEquipmentInSlot(slot);
            if (isDiamondGear(stack)) {
                mob.setCurrentItemOrArmor(slot, downgrade(stack));
            }
        }
    }
}

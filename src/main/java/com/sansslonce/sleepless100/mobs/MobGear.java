package com.sansslonce.sleepless100.mobs;

import java.util.Random;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.EnumDifficulty;

/** Armor and weapons for tier 3 (plain) and tier 5 (full, enchanted). */
final class MobGear {

    /** Equipment slots: 0 = held item, 1 = boots, 2 = leggings, 3 = chestplate, 4 = helmet. */
    private static final int HELD = 0;

    private MobGear() {}

    /** Tier 3: extra armor using vanilla's material odds, filling only empty slots; never enchanted. */
    static int addPlainArmor(EntityLiving mob, Random rand) {
        // Same material odds as vanilla EntityLiving.addRandomArmor: mostly leather/gold, rarely better.
        int material = rand.nextInt(2);
        for (int i = 0; i < 3; i++) {
            if (rand.nextFloat() < 0.095F) {
                material++;
            }
        }
        float stopChance = mob.worldObj.difficultySetting == EnumDifficulty.HARD ? 0.1F : 0.25F;
        int added = 0;
        for (int slot = 4; slot >= 1; slot--) { // helmet first, like vanilla
            if (slot < 4 && rand.nextFloat() < stopChance) {
                break;
            }
            if (mob.getEquipmentInSlot(slot) == null) {
                Item item = EntityLiving.getArmorItemForSlot(slot, material);
                if (item != null) {
                    mob.setCurrentItemOrArmor(slot, new ItemStack(item));
                    added++;
                }
            }
        }
        return added;
    }

    /** Tier 3: an iron sword (1 in 3) or iron shovel, like vanilla zombies; only if the hand is empty. */
    static boolean addPlainWeapon(EntityZombie zombie, Random rand) {
        if (zombie.getEquipmentInSlot(HELD) != null) {
            return false;
        }
        zombie.setCurrentItemOrArmor(HELD, new ItemStack(rand.nextInt(3) == 0 ? Items.iron_sword : Items.iron_shovel));
        return true;
    }

    /**
     * Tier 5: full armor (chainmail, iron or diamond), all enchanted, plus an enchanted weapon.
     * Before the diamond day, the diamond share becomes iron.
     */
    static void addFullEnchantedGear(EntityLiving mob, Random rand, int enchantLevel, boolean diamondAllowed) {
        float roll = rand.nextFloat();
        int material = roll < 0.15F ? 4 : roll < 0.6F ? 3 : 2; // 15% diamond, 45% iron, 40% chainmail
        if (material == 4 && !diamondAllowed) {
            material = 3;
        }
        for (int slot = 1; slot <= 4; slot++) {
            ItemStack armor = new ItemStack(EntityLiving.getArmorItemForSlot(slot, material));
            EnchantmentHelper.addRandomEnchantment(rand, armor, enchantLevel);
            mob.setCurrentItemOrArmor(slot, armor);
        }

        ItemStack held = mob.getEquipmentInSlot(HELD);
        if (mob instanceof EntityZombie && (held == null || held.getItem() == Items.iron_shovel)) {
            held = new ItemStack(Items.iron_sword);
        } else if (mob instanceof EntitySkeleton && held == null) {
            held = new ItemStack(Items.bow);
        }
        if (held != null) {
            held = held.copy();
            if (!held.isItemEnchanted()) {
                EnchantmentHelper.addRandomEnchantment(rand, held, enchantLevel);
            }
            mob.setCurrentItemOrArmor(HELD, held);
        }
    }
}

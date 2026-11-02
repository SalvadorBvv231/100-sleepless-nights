package com.sansslonce.sleepless100.mobs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.DifficultyManager;
import com.sansslonce.sleepless100.SleeplessNights;
import com.sansslonce.sleepless100.core.MobEffects;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Applies the tier effects to newly spawned hostile mobs. Registered on both the Forge and FML event buses.
 * <p>
 * New mobs are collected when they join the world and changed at the end of that world tick, after vanilla
 * has finished giving them their normal equipment. A tag in the mob's saved data marks it as done, so mobs
 * loaded from disk are never changed twice; effects are decided once, at spawn time.
 */
public class MobEffectHandler {

    private static final String TAG_DONE = "sleepless100.done";
    private static final String TAG_DAMAGE = "sleepless100.damageMultiplier";
    private static final String TAG_DOORS = "sleepless100.breaksDoors";
    private static final String TAG_CREEPER = "sleepless100.creeperScale";

    private static final UUID FOLLOW_RANGE_ID = UUID.fromString("5b0e7f5e-3c1a-4c55-9d43-1a0f6c2b8e01");
    private static final UUID HEALTH_ID = UUID.fromString("5b0e7f5e-3c1a-4c55-9d43-1a0f6c2b8e02");
    /** AttributeModifier operation 1: adds (base value × amount). */
    private static final int OP_MULTIPLY_BASE = 1;

    private final List<EntityLiving> pending = new ArrayList<>();

    /** Hostile, vanilla-style mobs that the tiers affect (bosses are left alone). */
    static boolean isAffected(Entity entity) {
        return entity instanceof EntityLiving && entity instanceof IMob && !(entity instanceof IBossDisplayData);
    }

    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote || !isAffected(event.entity)) {
            return;
        }
        EntityLiving mob = (EntityLiving) event.entity;
        NBTTagCompound data = mob.getEntityData();
        if (data.getBoolean(TAG_DONE)) {
            // Loaded from disk (or changed dimension): AI tasks are not saved, so re-attach them.
            if (data.getBoolean(TAG_DOORS) && mob instanceof EntityZombie) {
                attachDoorBreaking((EntityZombie) mob);
            }
            return;
        }
        pending.add(mob);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || pending.isEmpty()) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        MobEffects effects = null;
        Iterator<EntityLiving> it = pending.iterator();
        while (it.hasNext()) {
            EntityLiving mob = it.next();
            if (mob.worldObj != event.world) {
                continue;
            }
            it.remove();
            if (mob.isDead || mob.getEntityData()
                .getBoolean(TAG_DONE)) {
                continue;
            }
            if (effects == null) {
                effects = DifficultyManager.currentEffects(server);
            }
            apply(mob, effects);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        pending.removeIf(mob -> mob.worldObj == event.world);
    }

    private static void apply(EntityLiving mob, MobEffects fx) {
        NBTTagCompound data = mob.getEntityData();
        data.setBoolean(TAG_DONE, true);
        Random rand = mob.getRNG();
        StringBuilder log = Config.debugLogging ? new StringBuilder() : null;

        // Tier 1: longer follow/detection range.
        if (fx.followRangeBonus > 0) {
            addModifier(mob, SharedMonsterAttributes.followRange, FOLLOW_RANGE_ID, "follow range", fx.followRangeBonus);
            note(log, "range +%.0f%%", fx.followRangeBonus * 100);
        }

        // Tier 2: more health and damage.
        if (fx.healthBonus > 0) {
            addModifier(mob, SharedMonsterAttributes.maxHealth, HEALTH_ID, "health", fx.healthBonus);
            mob.setHealth(mob.getMaxHealth());
            note(log, "health %.1f", mob.getMaxHealth());
        }
        if (fx.damageBonus > 0) {
            data.setFloat(TAG_DAMAGE, (float) (1.0 + fx.damageBonus));
            note(log, "damage x%.2f", 1.0 + fx.damageBonus);
        }

        boolean zombie = mob instanceof EntityZombie && !(mob instanceof EntityPigZombie);
        boolean armorWearer = zombie || mob instanceof EntitySkeleton;

        // Tier 2: zombies break wooden doors.
        if (zombie && rand.nextDouble() < fx.doorBreakChance) {
            data.setBoolean(TAG_DOORS, true);
            attachDoorBreaking((EntityZombie) mob);
            note(log, "breaks doors");
        }

        // Tier 5 (full enchanted gear) or else tier 3 (some plain armor / a weapon).
        if (armorWearer) {
            if (rand.nextDouble() < fx.fullGearChance) {
                MobGear.addFullEnchantedGear(mob, rand, fx.fullGearEnchantLevel, fx.diamondAllowed);
                note(log, "FULL ENCHANTED GEAR");
            } else {
                if (rand.nextDouble() < fx.armorChance) {
                    note(log, "+%d armor", MobGear.addPlainArmor(mob, rand));
                }
                if (zombie && rand.nextDouble() < fx.weaponChance && MobGear.addPlainWeapon((EntityZombie) mob, rand)) {
                    note(log, "weapon");
                }
            }
        }

        // Diamond rule: also catches vanilla's rare diamond armor and anything other mods gave the mob.
        GearRules.enforce(mob, fx.diamondAllowed);

        // Tier 4: bigger creeper explosions (applied when it explodes).
        if (mob instanceof EntityCreeper && fx.creeperRadiusScale > 1.0) {
            data.setFloat(TAG_CREEPER, (float) fx.creeperRadiusScale);
            note(log, "explosion x%.2f", fx.creeperRadiusScale);
        }

        if (log != null && log.length() > 0) {
            SleeplessNights.LOG.info(
                "Changed new {} at {},{},{}:{}",
                EntityList.getEntityString(mob),
                (int) mob.posX,
                (int) mob.posY,
                (int) mob.posZ,
                log);
        }
    }

    /** Tier 2 damage: scales damage dealt by changed mobs (melee, arrows, etc. but not explosions). */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.entity.worldObj.isRemote || event.source.isExplosion()) {
            return;
        }
        Entity attacker = event.source.getEntity(); // for arrows this is the shooter
        if (attacker instanceof EntityLiving && isAffected(attacker)) {
            float multiplier = attacker.getEntityData()
                .getFloat(TAG_DAMAGE);
            if (multiplier > 1.0F) {
                event.ammount *= multiplier;
            }
        }
    }

    /** Tier 4: grows the explosion of creepers that spawned while tier 4 was active, up to the configured cap. */
    @SubscribeEvent
    public void onExplosionStart(ExplosionEvent.Start event) {
        Entity exploder = event.explosion.exploder;
        if (!(exploder instanceof EntityCreeper)) {
            return;
        }
        float scale = exploder.getEntityData()
            .getFloat(TAG_CREEPER);
        if (scale > 1.0F) {
            float before = event.explosion.explosionSize;
            event.explosion.explosionSize = (float) MobEffects
                .creeperRadius(before, scale, Config.effects.creeperMaxRadius);
            if (Config.debugLogging) {
                SleeplessNights.LOG.info("Creeper explosion radius {} -> {}", before, event.explosion.explosionSize);
            }
        }
    }

    /** Replaces vanilla's Hard-only door breaking with ours, which works on every difficulty. */
    private static void attachDoorBreaking(EntityZombie zombie) {
        zombie.func_146070_a(false); // removes vanilla's door-breaking task (setBreakDoorsAItask)
        for (Object entry : zombie.tasks.taskEntries) {
            if (((EntityAITasks.EntityAITaskEntry) entry).action instanceof AIBreakWoodenDoor) {
                return;
            }
        }
        zombie.tasks.addTask(1, new AIBreakWoodenDoor(zombie));
    }

    private static void addModifier(EntityLiving mob, net.minecraft.entity.ai.attributes.IAttribute attribute, UUID id,
        String name, double amount) {
        IAttributeInstance instance = mob.getEntityAttribute(attribute);
        if (instance != null && instance.getModifier(id) == null) {
            instance.applyModifier(new AttributeModifier(id, "100 Sleepless Nights " + name, amount, OP_MULTIPLY_BASE));
        }
    }

    private static void note(StringBuilder log, String format, Object... args) {
        if (log != null) {
            log.append(' ')
                .append(String.format(Locale.ROOT, format, args));
        }
    }
}

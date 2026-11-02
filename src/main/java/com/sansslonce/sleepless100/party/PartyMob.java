package com.sansslonce.sleepless100.party;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.NBTTagCompound;

import com.sansslonce.sleepless100.core.PartyRules.MobPlan;
import com.sansslonce.sleepless100.core.PartyRules.Power;
import com.sansslonce.sleepless100.core.PartyRules.Role;

/** Reads and writes the party tag in a mob's saved data. */
final class PartyMob {

    static final String TAG = "sleepless100.party";

    private PartyMob() {}

    static NBTTagCompound tag(Entity e) {
        NBTTagCompound data = e.getEntityData();
        return data.hasKey(TAG) ? data.getCompoundTag(TAG) : null;
    }

    static void mark(EntityLiving mob, long partyId, int group, MobPlan plan, int blocks) {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("id", partyId);
        t.setInteger("group", group);
        t.setString("role", plan.role.name());
        t.setString("powers", PartyData.joinPowers(plan.powers));
        t.setInteger("blocks", blocks);
        mob.getEntityData()
            .setTag(TAG, t);
    }

    static long partyId(Entity e) {
        NBTTagCompound t = tag(e);
        return t == null ? 0 : t.getLong("id");
    }

    static int group(Entity e) {
        NBTTagCompound t = tag(e);
        return t == null ? -1 : t.getInteger("group");
    }

    static Role role(Entity e) {
        NBTTagCompound t = tag(e);
        if (t == null) {
            return Role.NONE;
        }
        try {
            return Role.valueOf(t.getString("role"));
        } catch (IllegalArgumentException ex) {
            return Role.NONE;
        }
    }

    static List<Power> powers(Entity e) {
        NBTTagCompound t = tag(e);
        return PartyData.parsePowers(t == null ? "" : t.getString("powers"));
    }

    static boolean hasPower(Entity e, Power p) {
        return powers(e).contains(p);
    }

    static int blocks(Entity e) {
        NBTTagCompound t = tag(e);
        return t == null ? 0 : t.getInteger("blocks");
    }

    static void setBlocks(Entity e, int n) {
        NBTTagCompound t = tag(e);
        if (t != null) {
            t.setInteger("blocks", n);
        }
    }

    static long webReadyAt(Entity e) {
        NBTTagCompound t = tag(e);
        return t == null ? 0 : t.getLong("webAt");
    }

    static void setWebReadyAt(Entity e, long time) {
        NBTTagCompound t = tag(e);
        if (t != null) {
            t.setLong("webAt", time);
        }
    }
}

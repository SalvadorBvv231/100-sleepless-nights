package com.sansslonce.sleepless100.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import com.sansslonce.sleepless100.SleeplessNights;

/** Mod data saved with the world, in the save folder as data/sleepless100.dat. */
public class SleeplessWorldData extends WorldSavedData {

    public static final String NAME = SleeplessNights.MODID;

    /** Added to the computed day; changed only by /sleepless setday. */
    private double dayOffset;

    /** Highest tier already announced, so a tier is not announced again after reloading. */
    private int lastTier;

    /** Minecraft creates this class by reflection with exactly this constructor. */
    public SleeplessWorldData(String name) {
        super(name);
    }

    public static SleeplessWorldData get(World world) {
        MapStorage storage = world.mapStorage;
        SleeplessWorldData data = (SleeplessWorldData) storage.loadData(SleeplessWorldData.class, NAME);
        if (data == null) {
            data = new SleeplessWorldData(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    public double getDayOffset() {
        return dayOffset;
    }

    public void setDayOffset(double dayOffset) {
        this.dayOffset = dayOffset;
        markDirty();
    }

    public int getLastTier() {
        return lastTier;
    }

    public void setLastTier(int lastTier) {
        this.lastTier = lastTier;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        dayOffset = tag.getDouble("dayOffset");
        lastTier = tag.getInteger("lastTier");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setDouble("dayOffset", dayOffset);
        tag.setInteger("lastTier", lastTier);
    }
}

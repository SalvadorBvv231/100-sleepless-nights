package com.sansslonce.sleepless100.mobs;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIDoorInteract;
import net.minecraft.world.EnumDifficulty;

/**
 * Like vanilla's zombie door-breaking AI, but works on every difficulty (vanilla only breaks doors on
 * Hard). Only wooden doors (the parent class only finds wooden doors). Respects the mobGriefing gamerule.
 */
public class AIBreakWoodenDoor extends EntityAIDoorInteract {

    private static final int BREAK_TIME_TICKS = 240;

    private int breakingTime;
    private int lastProgress = -1;

    public AIBreakWoodenDoor(EntityLiving entity) {
        super(entity);
    }

    @Override
    public boolean shouldExecute() {
        return super.shouldExecute() && theEntity.worldObj.getGameRules()
            .getGameRuleBooleanValue("mobGriefing") && !isDoorOpen();
    }

    @Override
    public void startExecuting() {
        super.startExecuting();
        breakingTime = 0;
    }

    @Override
    public boolean continueExecuting() {
        double distanceSq = theEntity.getDistanceSq(entityPosX, entityPosY, entityPosZ);
        return breakingTime <= BREAK_TIME_TICKS && !isDoorOpen() && distanceSq < 4.0D;
    }

    @Override
    public void resetTask() {
        super.resetTask();
        theEntity.worldObj
            .destroyBlockInWorldPartially(theEntity.getEntityId(), entityPosX, entityPosY, entityPosZ, -1);
    }

    @Override
    public void updateTask() {
        super.updateTask();
        if (theEntity.getRNG()
            .nextInt(20) == 0) {
            theEntity.worldObj.playAuxSFX(1010, entityPosX, entityPosY, entityPosZ, 0); // knocking sound
        }
        breakingTime++;
        int progress = (int) (breakingTime / (float) BREAK_TIME_TICKS * 10.0F);
        if (progress != lastProgress) {
            theEntity.worldObj
                .destroyBlockInWorldPartially(theEntity.getEntityId(), entityPosX, entityPosY, entityPosZ, progress);
            lastProgress = progress;
        }
        if (breakingTime == BREAK_TIME_TICKS && theEntity.worldObj.difficultySetting != EnumDifficulty.PEACEFUL) {
            theEntity.worldObj.setBlockToAir(entityPosX, entityPosY, entityPosZ);
            theEntity.worldObj.playAuxSFX(1012, entityPosX, entityPosY, entityPosZ, 0); // door break sound
            theEntity.worldObj
                .playAuxSFX(2001, entityPosX, entityPosY, entityPosZ, Block.getIdFromBlock(field_151504_e));
        }
    }

    private boolean isDoorOpen() {
        // func_150015_f = BlockDoor.isDoorOpen in 1.7.10 mappings
        return field_151504_e.func_150015_f(theEntity.worldObj, entityPosX, entityPosY, entityPosZ);
    }
}

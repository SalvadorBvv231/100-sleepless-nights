package com.sansslonce.sleepless100.village;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.MathHelper;
import net.minecraft.village.Village;
import net.minecraft.village.VillageDoorInfo;

/**
 * Vanilla's EntityAIRestrictOpenDoor (keeps villagers inside once they're in), but active in the early window
 * of {@link AIVillagerEarlyIndoors} too.
 */
public class AIVillagerEarlyRestrictDoor extends EntityAIBase {

    private final EntityCreature villager;
    private VillageDoorInfo door;

    public AIVillagerEarlyRestrictDoor(EntityCreature villager) {
        this.villager = villager;
    }

    @Override
    public boolean shouldExecute() {
        if (!AIVillagerEarlyIndoors.early(villager)) {
            return false;
        }
        int x = MathHelper.floor_double(villager.posX);
        int y = MathHelper.floor_double(villager.posY);
        int z = MathHelper.floor_double(villager.posZ);
        Village village = villager.worldObj.villageCollectionObj.findNearestVillage(x, y, z, 16);
        if (village == null) {
            return false;
        }
        door = village.findNearestDoor(x, y, z);
        return door != null && door.getInsideDistanceSquare(x, y, z) < 2.25D;
    }

    @Override
    public boolean continueExecuting() {
        return AIVillagerEarlyIndoors.early(villager) && !door.isDetachedFromVillageFlag
            && door.isInside(MathHelper.floor_double(villager.posX), MathHelper.floor_double(villager.posZ));
    }

    @Override
    public void startExecuting() {
        villager.getNavigator()
            .setBreakDoors(false);
        villager.getNavigator()
            .setEnterDoors(false);
    }

    @Override
    public void resetTask() {
        villager.getNavigator()
            .setBreakDoors(true);
        villager.getNavigator()
            .setEnterDoors(true);
        door = null;
    }

    @Override
    public void updateTask() {
        door.incrementDoorOpeningRestrictionCounter();
    }
}

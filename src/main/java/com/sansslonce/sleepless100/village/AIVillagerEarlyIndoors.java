package com.sansslonce.sleepless100.village;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.village.Village;
import net.minecraft.village.VillageDoorInfo;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.DifficultyManager;
import com.sansslonce.sleepless100.core.PartyRules;
import com.sansslonce.sleepless100.core.VillageRules;

/**
 * Vanilla's EntityAIMoveIndoors, but it already runs from {@code earlyIndoorsTime} once the difficulty day reaches
 * {@code earlyIndoorsDay}. Vanilla's own task still handles the night itself. Normal walking speed.
 */
public class AIVillagerEarlyIndoors extends EntityAIBase {

    private final EntityCreature villager;
    private VillageDoorInfo door;
    private int insideX = -1, insideZ = -1;

    public AIVillagerEarlyIndoors(EntityCreature villager) {
        this.villager = villager;
        setMutexBits(1);
    }

    static boolean early(EntityCreature villager) {
        if (villager.worldObj.provider.hasNoSky) {
            return false;
        }
        int tod = PartyRules.timeOfDay(villager.worldObj.getWorldTime());
        // Cheap time check first; the day lookup only when it matters.
        return tod >= Config.villages.earlyIndoorsTime && tod < VillageRules.DAWN_TIME
            && VillageRules.goIndoorsEarly(DifficultyManager.getDay(MinecraftServer.getServer()), tod, Config.villages);
    }

    @Override
    public boolean shouldExecute() {
        if (villager.getRNG()
            .nextInt(50) != 0 || !early(villager)) {
            return false;
        }
        if (insideX != -1 && villager.getDistanceSq(insideX, villager.posY, insideZ) < 4.0D) {
            return false;
        }
        int x = MathHelper.floor_double(villager.posX);
        int y = MathHelper.floor_double(villager.posY);
        int z = MathHelper.floor_double(villager.posZ);
        Village village = villager.worldObj.villageCollectionObj.findNearestVillage(x, y, z, 14);
        if (village == null) {
            return false;
        }
        door = village.findNearestDoorUnrestricted(x, y, z);
        return door != null;
    }

    @Override
    public boolean continueExecuting() {
        return !villager.getNavigator()
            .noPath();
    }

    @Override
    public void startExecuting() {
        insideX = -1;
        double dx = door.getInsidePosX() + 0.5D, dy = door.getInsidePosY(), dz = door.getInsidePosZ() + 0.5D;
        if (villager.getDistanceSq(door.getInsidePosX(), door.posY, door.getInsidePosZ()) > 256.0D) {
            Vec3 step = RandomPositionGenerator
                .findRandomTargetBlockTowards(villager, 14, 3, Vec3.createVectorHelper(dx, dy, dz));
            if (step != null) {
                villager.getNavigator()
                    .tryMoveToXYZ(step.xCoord, step.yCoord, step.zCoord, 1.0D);
            }
        } else {
            villager.getNavigator()
                .tryMoveToXYZ(dx, dy, dz, 1.0D);
        }
    }

    @Override
    public void resetTask() {
        if (door != null) {
            insideX = door.getInsidePosX();
            insideZ = door.getInsidePosZ();
        }
        door = null;
    }
}

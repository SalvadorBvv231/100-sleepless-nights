package com.sansslonce.sleepless100.party;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * Party mobs (task-AI ones: zombies, skeletons, creepers, witches) walk to their group's rally point, then to the
 * party target. Stops as soon as the mob has an attack target, so vanilla's attack tasks take over.
 * Always at normal speed (1.0): movement speed is never changed.
 */
public class AIPartyMarch extends EntityAIBase {

    private static final int REPATH_TICKS = 20;

    private final EntityLiving mob;
    private int repathTimer;

    public AIPartyMarch(EntityLiving mob) {
        this.mob = mob;
        setMutexBits(1); // movement: keeps wander/village walking from pulling the mob away
    }

    @Override
    public boolean shouldExecute() {
        return mob.getAttackTarget() == null && PartyManager.walkGoal(mob) != null;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        repathTimer = 0;
    }

    @Override
    public void resetTask() {
        mob.getNavigator()
            .clearPathEntity();
    }

    @Override
    public void updateTask() {
        if (--repathTimer > 0) {
            return;
        }
        repathTimer = REPATH_TICKS + mob.getRNG()
            .nextInt(5);
        double[] goal = PartyManager.walkGoal(mob);
        if (goal != null) {
            // The path finder stops at its search range and returns the best partial path, so far goals work too.
            mob.getNavigator()
                .tryMoveToXYZ(goal[0], goal[1], goal[2], 1.0D);
        }
    }
}

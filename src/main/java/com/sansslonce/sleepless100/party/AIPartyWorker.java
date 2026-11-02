package com.sansslonce.sleepless100.party;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.sansslonce.sleepless100.Config;
import com.sansslonce.sleepless100.core.PartyRules;
import com.sansslonce.sleepless100.core.PartyRules.Role;

/**
 * Miners dig and builders pillar/bridge when a party zombie is stuck on its way to its target.
 * <p>
 * Runs alongside the attack task (no mutex bits). Every 10 ticks it checks whether the mob has moved; after about
 * 30 ticks without moving while its target is still away, it acts once in the direction of the target.
 */
public class AIPartyWorker extends EntityAIBase {

    private static final int CHECK_TICKS = 10;
    private static final int STUCK_CHECKS = 3;
    private static final int PILLAR_TIMEOUT = 40;

    private final EntityLiving mob;
    private final boolean miner;

    private int checkTimer;
    private double lastX, lastZ;
    private int stuckChecks;
    private int pillarHeight;

    private boolean digging;
    private int digX, digY, digZ, digTime, digTotal, lastCrack = -1;
    private Block digBlock;

    private boolean pillaring;
    private int pillarX, pillarY, pillarZ, pillarTicks;

    public AIPartyWorker(EntityLiving mob, Role role) {
        this.mob = mob;
        this.miner = role == Role.MINER;
        setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        return PartyManager.isAttacking(mob) && PartyBlocks.griefing(mob.worldObj);
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void resetTask() {
        stopDigging();
        pillaring = false;
    }

    @Override
    public void updateTask() {
        if (digging) {
            continueDig();
            return;
        }
        if (pillaring) {
            continuePillar();
            return;
        }
        if (++checkTimer < CHECK_TICKS) {
            return;
        }
        checkTimer = 0;
        double[] goal = goal();
        double dx = mob.posX - lastX, dz = mob.posZ - lastZ;
        boolean moved = dx * dx + dz * dz > 0.4 * 0.4;
        lastX = mob.posX;
        lastZ = mob.posZ;
        if (goal == null || moved) {
            stuckChecks = 0;
            if (moved) {
                pillarHeight = 0;
            }
            return;
        }
        double gx = goal[0] - mob.posX, gz = goal[2] - mob.posZ, gy = goal[1] - mob.posY;
        double horizontal = Math.sqrt(gx * gx + gz * gz);
        if (horizontal < 1.5 && Math.abs(gy) < 2) {
            stuckChecks = 0; // arrived
            return;
        }
        if (++stuckChecks < STUCK_CHECKS) {
            return;
        }
        stuckChecks = STUCK_CHECKS - 1; // try again on the next check if still stuck
        act(gx, gy, gz, horizontal);
    }

    /** The attack target if there is one, else where the party wants the mob to walk. */
    private double[] goal() {
        EntityLivingBase target = mob.getAttackTarget();
        if (target != null && target.isEntityAlive()) {
            return new double[] { target.posX, target.posY, target.posZ };
        }
        return PartyManager.walkGoal(mob);
    }

    private void act(double gx, double gy, double gz, double horizontal) {
        World w = mob.worldObj;
        int bx = MathHelper.floor_double(mob.posX);
        int by = MathHelper.floor_double(mob.boundingBox.minY + 0.01);
        int bz = MathHelper.floor_double(mob.posZ);
        int sx = 0, sz = 0;
        if (Math.abs(gx) >= Math.abs(gz)) {
            sx = gx > 0 ? 1 : -1;
        } else {
            sz = gz > 0 ? 1 : -1;
        }
        int fx = bx + sx, fz = bz + sz;
        boolean feetBlocked = PartyBlocks.solid(w, fx, by, fz);
        boolean headBlocked = PartyBlocks.solid(w, fx, by + 1, fz);

        if (miner) {
            int level = PartyManager.digLevel();
            if (gy < -1.5 && horizontal < 2.5 && startDig(bx, by - 1, bz, level)) {
                return; // target is below: dig down
            }
            if (headBlocked && startDig(fx, by + 1, fz, level)) {
                return;
            }
            if (feetBlocked && startDig(fx, by, fz, level)) {
                return;
            }
            if (gy > 1.5 && PartyBlocks.solid(w, bx, by + 2, bz) && startDig(bx, by + 2, bz, level)) {
                return; // target is above: make headroom
            }
            return;
        }

        // Builder
        int blocks = PartyMob.blocks(mob);
        if (blocks <= 0) {
            return;
        }
        boolean wall = feetBlocked || headBlocked;
        if ((wall || gy > 1.5) && pillarHeight < Config.party.maxPillarHeight
            && !PartyBlocks.solid(w, bx, by + 2, bz)
            && PartyBlocks.replaceable(w, bx, by, bz)) {
            pillaring = true;
            pillarX = bx;
            pillarY = by;
            pillarZ = bz;
            pillarTicks = 0;
            mob.getNavigator()
                .clearPathEntity();
            return;
        }
        if (!wall && gy > -3 && !PartyBlocks.solid(w, fx, by - 1, fz) && PartyBlocks.replaceable(w, fx, by - 1, fz)) {
            if (PartyBlocks.place(w, fx, by - 1, fz, Blocks.dirt)) {
                useBlock(blocks);
                mob.getNavigator()
                    .tryMoveToXYZ(fx + 0.5, by, fz + 0.5, 1.0D);
            }
        }
    }

    // ---- Digging ----

    private boolean startDig(int x, int y, int z, int level) {
        World w = mob.worldObj;
        if (!PartyBlocks.canDig(w, x, y, z, level)) {
            return false;
        }
        digging = true;
        digX = x;
        digY = y;
        digZ = z;
        digBlock = w.getBlock(x, y, z);
        digTime = 0;
        digTotal = PartyRules.digTicks(digBlock.getBlockHardness(w, x, y, z), Config.party);
        lastCrack = -1;
        mob.getNavigator()
            .clearPathEntity();
        return true;
    }

    private void continueDig() {
        World w = mob.worldObj;
        if (w.getBlock(digX, digY, digZ) != digBlock
            || mob.getDistanceSq(digX + 0.5, digY + 0.5, digZ + 0.5) > 3.5 * 3.5) {
            stopDigging();
            return;
        }
        mob.getLookHelper()
            .setLookPosition(digX + 0.5, digY + 0.5, digZ + 0.5, 30.0F, 30.0F);
        if (digTime % 5 == 0) {
            mob.swingItem();
            w.playSoundEffect(digX + 0.5, digY + 0.5, digZ + 0.5, digBlock.stepSound.getStepResourcePath(), 0.5F, 0.6F);
        }
        digTime++;
        int crack = Math.min(9, digTime * 10 / Math.max(1, digTotal));
        if (crack != lastCrack) {
            w.destroyBlockInWorldPartially(mob.getEntityId(), digX, digY, digZ, crack);
            lastCrack = crack;
        }
        if (digTime >= digTotal) {
            stopDigging();
            PartyBlocks.dig(w, digX, digY, digZ);
            stuckChecks = STUCK_CHECKS - 1; // look at the next block soon
        }
    }

    private void stopDigging() {
        if (digging) {
            mob.worldObj.destroyBlockInWorldPartially(mob.getEntityId(), digX, digY, digZ, -1);
        }
        digging = false;
    }

    // ---- Pillaring ----

    private void continuePillar() {
        World w = mob.worldObj;
        if (++pillarTicks > PILLAR_TIMEOUT) {
            pillaring = false;
            return;
        }
        if (mob.onGround && mob.boundingBox.minY < pillarY + 0.5) {
            mob.getJumpHelper()
                .setJumping();
            return;
        }
        if (mob.boundingBox.minY >= pillarY + 1.0 && PartyBlocks.replaceable(w, pillarX, pillarY, pillarZ)) {
            pillaring = false;
            if (PartyBlocks.place(w, pillarX, pillarY, pillarZ, Blocks.dirt)) {
                useBlock(PartyMob.blocks(mob));
                pillarHeight++;
                stuckChecks = STUCK_CHECKS - 1;
            }
        }
    }

    private void useBlock(int blocksBefore) {
        int left = blocksBefore - 1;
        PartyMob.setBlocks(mob, left);
        if (left <= 0) {
            mob.setCurrentItemOrArmor(0, null); // out of dirt
        }
    }
}

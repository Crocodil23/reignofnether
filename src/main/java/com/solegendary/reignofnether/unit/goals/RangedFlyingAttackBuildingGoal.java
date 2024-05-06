package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;


public class RangedFlyingAttackBuildingGoal<T extends net.minecraft.world.entity.Mob> extends Goal {
    private final T mob;
    private BlockPos groundTarget = null;
    private final UnitBowAttackGoal<?> bowAttackGoal;
    private Building buildingTarget = null;

    public RangedFlyingAttackBuildingGoal(T mob, UnitBowAttackGoal<?> bowAttackGoal) {
        this.mob = mob;
        this.bowAttackGoal = bowAttackGoal;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private void setNextGroundTarget() {
        if (this.buildingTarget != null && !buildingTarget.getBlocks().isEmpty()) {
            Random rand = new Random();
            List<BuildingBlock> nonAirBlocks = buildingTarget.getBlocks().stream().filter(b -> b.isPlaced(this.mob.level)).toList();
            BuildingBlock block = nonAirBlocks.get(rand.nextInt(nonAirBlocks.size()));
            this.groundTarget = block.getBlockPos();
        }
    }

    public void setBuildingTarget(BlockPos blockPos) {
        if (blockPos != null) {
            if (this.mob.level.isClientSide()) {
                this.buildingTarget = BuildingUtils.findBuilding(true, blockPos);
                if (this.buildingTarget != null) {
                    MiscUtil.addUnitCheckpoint(((Unit) mob), new BlockPos(
                            buildingTarget.centrePos.getX(),
                            buildingTarget.originPos.getY() + 1,
                            buildingTarget.centrePos.getZ())
                    );
                    ((Unit) mob).setIsCheckpointGreen(false);
                }
            }
            else {
                this.buildingTarget = BuildingUtils.findBuilding(false, blockPos);
                setNextGroundTarget();
            }
            this.start();
        }
    }

    public Building getBuildingTarget() {
        return buildingTarget;
    }

    public boolean canUse() {
        return this.groundTarget != null;
    }

    public boolean canContinueToUse() {
        if (!this.canUse() && this.isDoneMoving())
            return false;
        return true;
    }

    public void start() {
        super.start();
        this.mob.setAggressive(true);
    }

    public void stop() {
        super.stop();
        groundTarget = null;
        buildingTarget = null;
        this.mob.setAggressive(false);
    }

    public void tick() {
        if (buildingTarget != null && buildingTarget.getBlocksPlaced() <= 0) {
            stop();
        }
        if (groundTarget != null) {
            float tx = groundTarget.getX() + 0.5f;
            float ty = groundTarget.getY() + 0.5f;
            float tz = groundTarget.getZ() + 0.5f;

            this.mob.getLookControl().setLookAt(tx, ty, tz);

            if (this.mob.level.isClientSide())
                return;

            float attackRange = ((AttackerUnit) this.mob).getAttackRange();

            double distToTarget = Math.sqrt(this.mob.distanceToSqr(tx, ty, tz));

            if ((distToTarget > attackRange - 1) &&
                !((Unit) this.mob).getHoldPosition()) {
                this.moveTo(this.groundTarget);
            } else {
                this.stopMoving();
            }
            if (distToTarget <= attackRange) { // start drawing bowstring
                if (bowAttackGoal.getAttackCooldown() <= 0) {
                    if (mob instanceof RangedAttackerUnit rangedAttackerUnit) {
                        rangedAttackerUnit.performUnitRangedAttack(tx, ty, tz, 20);
                        if (!mob.level.isClientSide() && buildingTarget != null)
                            FogOfWarClientboundPacket.revealRangedUnit(buildingTarget.ownerName, mob.getId());
                    }
                    bowAttackGoal.setToMaxAttackCooldown();
                    setNextGroundTarget();
                }
            }
        }
    }

    // moveGoal controllers
    private boolean isDoneMoving() {
        Unit unit = (Unit) this.mob;
        if (unit.getMoveGoal() instanceof FlyingMoveToTargetGoal flyingMoveGoal)
            return flyingMoveGoal.isAtDestination();
        else
            return this.mob.getNavigation().isDone();
    }

    private void stopMoving() {
        Unit unit = (Unit) this.mob;
        if (unit.getMoveGoal() instanceof FlyingMoveToTargetGoal flyingMoveGoal)
            flyingMoveGoal.stopMoving();
        else
            this.mob.getNavigation().stop();
    }

    private void moveTo(BlockPos bp) {
        Unit unit = (Unit) this.mob;
        if (unit.getMoveGoal() instanceof FlyingMoveToTargetGoal flyingMoveGoal)
            flyingMoveGoal.setMoveTarget(bp);
        else
            this.mob.getNavigation().moveTo(bp.getX(), bp.getY(), bp.getZ(), 1.0f);
    }
}

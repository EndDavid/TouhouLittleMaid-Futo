package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockAction;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import java.util.Optional;

/**
 * 「到达之后动手」任务，移植自上游同名类。
 *
 * <p>它和 {@link MaidStealEdibleMoveBlockTask} 是一对：前者只负责把女仆送到目标旁边
 * 并写两个记忆（{@code TARGET_POS} + {@code MAID_EDIBLE_BLOCK_ACTION}），
 * 本类负责在<b>够近</b>的时候真正执行 {@code consume} / {@code placeAsFood}。</p>
 *
 * <h2>为什么够不到时要擦掉 {@code TARGET_POS}</h2>
 * <p>女仆走到一半被别的东西打断（主人走远、目标被拆）时，{@code TARGET_POS} 会留在脑子里，
 * 而寻路目标已经没了。不擦掉的话她会一直「以为要去那儿」，站着发呆。
 * 所以这里判断：离得太远<b>且</b>{@code WALK_TARGET} 已经不再指向同一个点时，清掉记忆。</p>
 *
 * <h2>为什么吃之前再确认一次方块</h2>
 * <p>从「找到」到「走到」之间有几十 tick，那段时间方块可能已经被玩家挖了 / 被别的女仆吃完了。
 * 所以 {@code shouldMoveTo} 要再跑一次 —— 上游同样如此。</p>
 */
public class MaidStealEdibleUseTask extends Behavior<EntityMaid> {
    /**
     * 够近的判定距离（格）。上游传 2。
     */
    private final double closeEnoughDist;

    public MaidStealEdibleUseTask(double closeEnoughDist) {
        super(ImmutableMap.of(
                InitEntities.TARGET_POS, MemoryStatus.VALUE_PRESENT,
                InitEntities.MAID_EDIBLE_BLOCK_ACTION, MemoryStatus.VALUE_PRESENT
        ));
        this.closeEnoughDist = closeEnoughDist;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        Brain<EntityMaid> brain = owner.getBrain();
        return brain.getMemory(InitEntities.TARGET_POS).map(targetPos -> {
            Vec3 targetV3d = targetPos.currentPosition();
            if (owner.distanceToSqr(targetV3d) > Math.pow(this.closeEnoughDist, 2)) {
                Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);
                if (walkTarget.isEmpty() || !walkTarget.get().getTarget().currentPosition().equals(targetV3d)) {
                    brain.eraseMemory(InitEntities.TARGET_POS);
                }
                return false;
            }
            return true;
        }).orElse(false);
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTimeIn) {
        Brain<EntityMaid> brain = maid.getBrain();
        brain.getMemory(InitEntities.TARGET_POS).ifPresent(posWrapper ->
                brain.getMemory(InitEntities.MAID_EDIBLE_BLOCK_ACTION).ifPresent(action ->
                        this.handle(world, maid, posWrapper, action)));
    }

    private void handle(ServerLevel world, EntityMaid maid, PositionTracker posWrapper,
                        MaidEdibleBlockAction action) {
        BlockPos blockPos = posWrapper.currentBlockPosition();
        BlockState blockState = world.getBlockState(blockPos);
        if (action == MaidEdibleBlockAction.TRY_STEAL) {
            this.trySteal(maid, blockPos, blockState);
        } else {
            this.tryPlace(maid, blockPos);
        }
    }

    private void trySteal(EntityMaid maid, BlockPos blockPos, BlockState blockState) {
        for (IMaidEdibleBlock edibleBlock : MaidEdibleBlockManager.getEdibleBlocks()) {
            if (!edibleBlock.shouldMoveTo(maid, blockPos, blockState)) {
                continue;
            }
            boolean ate = edibleBlock.consume(maid, blockPos, blockState);
            if (ate) {
                int points = edibleBlock.getFavorabilityPoints(maid, blockPos, blockState);
                maid.getFavorabilityManager().apply(Type.STEAL_EDIBLE_BLOCK, points);
                maid.swing(InteractionHand.MAIN_HAND);
            }
            this.clearTarget(maid);
            return;
        }
    }

    private void tryPlace(EntityMaid maid, BlockPos blockPos) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (IMaidEdibleBlock edibleBlock : MaidEdibleBlockManager.getEdibleBlocks()) {
                if (!edibleBlock.canPlaceAsFood(maid, stack, i)) {
                    continue;
                }
                boolean placed = edibleBlock.placeAsFood(maid, blockPos, stack, i);
                if (placed) {
                    maid.swing(InteractionHand.MAIN_HAND);
                }
                // 摆完之后切回「偷吃」：下一次就能吃自己刚放的东西
                maid.getBrain().setMemory(InitEntities.MAID_EDIBLE_BLOCK_ACTION, MaidEdibleBlockAction.TRY_STEAL);
                this.clearTarget(maid);
                return;
            }
        }
    }

    private void clearTarget(EntityMaid maid) {
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}


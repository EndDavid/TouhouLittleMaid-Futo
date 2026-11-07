package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IFarmTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 到达地块后收获 + 补种，移植自上游 {@code MaidFarmPlantTask}。
 *
 * <p>先收获（成熟作物），再尝试把背包里的种子种回去 —— 一次到达可以同时做两件事，
 * 这样收完的地块不会空着。</p>
 *
 * <p><b>与上游的唯一差异：加入挖掘进度。</b>上游是「到达即 {@code destroyBlock}」，
 * 任何方块都瞬间消失；对小麦这类硬度为 0 的作物没问题（原版也是秒破），
 * 但西瓜 / 南瓜硬度是 1.0，玩家视角看就是「方块凭空爆掉，给什么工具都一样快」。
 * 现在按原版公式（{@code EntityMaid#getDestroyProgress}）逐 tick 累积进度：</p>
 * <ul>
 *     <li>硬度 0 的作物 → 进度直接是 1，仍然是瞬间，<b>保持上游手感</b>；</li>
 *     <li>硬度 &gt; 0 的方块（西瓜 / 南瓜 / 可可）→ 按工具速度累积，
 *         徒手 30 tick、木斧 15 tick，与原版挖方块一致。</li>
 * </ul>
 *
 * <p>与上游的其它差异：上游收获/种植后会触发进度 {@code MAID_FARM}，进度系统尚未移植，省略。</p>
 */
public class MaidFarmPlantTask extends Behavior<EntityMaid> {
    /**
     * 破坏阈值，与原版一致：进度累加到 1.0 即破坏方块。
     */
    private static final float BREAK_THRESHOLD = 1.0F;
    /**
     * 行为最长持续时间。
     *
     * <p>默认的 60 tick 不够：徒手挖西瓜就要 30 tick，若中途被别的行为打断会前功尽弃。
 * 这里放宽到 400 tick，真正的结束条件是 {@link #canStillUse}。</p>
     */
    private static final int MAX_DURATION = 400;

    private final IFarmTask task;
    /**
     * 正在挖掘的方块位置；{@code null} 表示当前没有挖掘任务。
     */
    @Nullable
    private BlockPos breakingPos;
    private float breakProgress;

    public MaidFarmPlantTask(IFarmTask task) {
        super(ImmutableMap.of(InitEntities.TARGET_POS, MemoryStatus.VALUE_PRESENT), MAX_DURATION);
        this.task = task;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        Brain<EntityMaid> brain = owner.getBrain();
        return brain.getMemory(InitEntities.TARGET_POS).map(targetPos -> {
            Vec3 targetV3d = targetPos.currentPosition();
            if (owner.distanceToSqr(targetV3d) > Math.pow(this.task.getCloseEnoughDist(), 2)) {
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
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        if (this.breakingPos == null) {
            // 上一 tick 已经把活干完了（收获 / 种植都是一次性动作）
            return false;
        }
        if (!maid.canDestroyBlock(this.breakingPos)) {
            return false;
        }
        // 距离放宽 2 格：她需要站在方块旁边挖，不能因为碰撞推挤抖动就中断
        double maxDist = this.task.getCloseEnoughDist() + 2.0;
        return maid.distanceToSqr(Vec3.atCenterOf(this.breakingPos)) <= maxDist * maxDist;
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTimeIn) {
        this.breakingPos = null;
        this.breakProgress = 0.0F;

        maid.getBrain().getMemory(InitEntities.TARGET_POS).ifPresent(posWrapper -> {
            BlockPos basePos = posWrapper.currentBlockPosition();
            BlockPos cropPos = basePos.above();
            BlockState cropState = world.getBlockState(cropPos);

            if (maid.canDestroyBlock(cropPos) && this.task.canHarvest(maid, cropPos, cropState)) {
                if (maid.getDestroyProgress(cropState, cropPos) >= BREAK_THRESHOLD) {
                    // 硬度为 0 的作物：原版也是瞬间破坏，这里保持上游的即时手感
                    this.harvestNow(world, maid, cropPos, cropState);
                } else {
                    // 需要挖掘：站定，然后逐 tick 累积进度
                    this.breakingPos = cropPos.immutable();
                    this.breakProgress = 0.0F;
                    Brain<?> brain = maid.getBrain();
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    brain.eraseMemory(MemoryModuleType.PATH);
                    maid.getNavigation().stop();
                    maid.getLookControl().setLookAt(
                            cropPos.getX() + 0.5D, cropPos.getY() + 0.5D, cropPos.getZ() + 0.5D);
                    maid.swing(InteractionHand.MAIN_HAND);
                }
                return;
            }

            CombinedInvWrapper availableInv = maid.getAvailableInv(true);
            List<Integer> slots = ItemsUtil.getFilterStackSlots(availableInv, this.task::isSeed);
            if (!slots.isEmpty()) {
                for (int slot : slots) {
                    ItemStack seed = availableInv.getStackInSlot(slot);
                    BlockState baseState = world.getBlockState(basePos);
                    if (this.task.canPlant(maid, basePos, baseState, seed)) {
                        ItemStack remain = this.task.plant(maid, basePos, baseState, seed);
                        availableInv.setStackInSlot(slot, remain);
                        maid.swing(InteractionHand.MAIN_HAND);
                        maid.getBrain().eraseMemory(InitEntities.TARGET_POS);
                        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                        return;
                    }
                }
            }
        });
    }

    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        if (this.breakingPos == null) {
            return;
        }
        BlockState cropState = world.getBlockState(this.breakingPos);
        if (cropState.isAir()) {
            this.breakingPos = null;
            return;
        }
        float perTick = maid.getDestroyProgress(cropState, this.breakingPos);
        if (perTick <= 0.0F) {
            // 硬度为负（挖不动）或没有可用工具 —— 放弃，交给下一次重新挑点
            this.breakingPos = null;
            return;
        }
        this.breakProgress += perTick;
        // 与原版一致：挖掘期间每 tick 挥一次手
        maid.swing(InteractionHand.MAIN_HAND);
        maid.getLookControl().setLookAt(
                this.breakingPos.getX() + 0.5D, this.breakingPos.getY() + 0.5D, this.breakingPos.getZ() + 0.5D);
        if (this.breakProgress >= BREAK_THRESHOLD) {
            BlockPos target = this.breakingPos;
            this.breakingPos = null;
            this.breakProgress = 0.0F;
            this.harvestNow(world, maid, target, cropState);
        }
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTimeIn) {
        this.breakingPos = null;
        this.breakProgress = 0.0F;
    }

    private void harvestNow(ServerLevel world, EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        this.task.harvest(maid, cropPos, cropState);
        maid.swing(InteractionHand.MAIN_HAND);
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}


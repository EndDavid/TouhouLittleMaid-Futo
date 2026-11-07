package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.api.entity.fishing.IFishingType;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.fishing.FishingTypeManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 岸边钓鱼：走到水边然后抛钩，移植自上游 {@code MaidRideFindWaterTask}，但**不要求骑乘**。
 *
 * <p>为什么不用上游那种「先坐上船 / 坐垫再抛钩」：</p>
 * <ul>
 *     <li>上游的可坐对象是 {@code EntityChair}（独立方块实体）与 {@code Boat}。
 *         坐垫系统尚未移植；而 {@code Boat} 不是 {@link net.minecraft.world.entity.LivingEntity}，
 *         上游那套「从 {@code NEAREST_VISIBLE_LIVING_ENTITIES} 里筛 Boat」的写法<b>永远筛不到</b>。</li>
 *     <li>本移植版的 GeckoLib 渲染管线在「骑乘 + 非人形模型」时会把模型上下翻转，
 *         让女仆坐船反而更难看。</li>
 * </ul>
 *
 * <p>因此改为最直观的形态：<b>走到离水够近的位置就抛钩</b>。
 * 骑乘状态下仍会走 {@code MaidRideFindWaterTask}（上游逻辑），两条路互不冲突。</p>
 */
public class MaidFishingCastTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 20;
    /**
     * 抛钩所需的最大水平距离（格）。
     */
    private static final double CAST_DISTANCE = 5.0;

    private final int searchRange;
    private final int verticalSearchRange;

    public MaidFishingCastTask(int searchRange, int verticalSearchRange) {
        super(ImmutableMap.of());
        this.searchRange = searchRange;
        this.verticalSearchRange = verticalSearchRange;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        if (!super.checkExtraStartConditions(worldIn, owner)) {
            return false;
        }
        if (owner.fishing != null || !owner.canBrainMoving()) {
            return false;
        }
        ItemStack rod = owner.getMainHandItem();
        return FishingTypeManager.getFishingType(rod).isFishingRod(rod);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        IFishingType fishingType = FishingTypeManager.getFishingType(maid.getMainHandItem());
        BlockPos waterPos = this.findWater(worldIn, maid);
        if (waterPos == null) {
            return;
        }

        // 还不够近 —— 先走过去（留 2 格容差，避免踩进水里）
        if (maid.distanceToSqr(Vec3.atCenterOf(waterPos)) > CAST_DISTANCE * CAST_DISTANCE) {
            BehaviorUtils.setWalkAndLookTargetMemories(maid, waterPos, 0.6F, 2);
            this.setNextCheckTickCount(5);
            return;
        }

        ItemStack rod = maid.getMainHandItem();
        if (!fishingType.suitableFishingHook(maid, worldIn, rod, waterPos)) {
            return;
        }

        Vec3 centerPos = Vec3.atCenterOf(waterPos);
        MaidFishingHook hook = fishingType.getFishingHook(maid, worldIn, rod, centerPos);
        worldIn.addFreshEntity(hook);
        worldIn.playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F,
                0.4F / (worldIn.getRandom().nextFloat() * 0.4F + 0.8F));
        maid.swing(InteractionHand.MAIN_HAND);
        maid.getLookControl().setLookAt(centerPos);
        // 抛出去以后先歇一会儿，别每 tick 都重新找水
        this.setNextCheckTickCount(40);
    }

    /**
     * 螺旋扫描附近的水面方块，返回离女仆最近且可达的那一格。
     */
    private BlockPos findWater(ServerLevel worldIn, EntityMaid maid) {
        BlockPos centrePos = maid.getBrainSearchPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int y = -this.verticalSearchRange; y <= this.verticalSearchRange; y++) {
            for (int i = 0; i < this.searchRange; ++i) {
                for (int x = 0; x <= i; x = x > 0 ? -x : 1 - x) {
                    for (int z = x < i && x > -i ? i : 0; z <= i; z = z > 0 ? -z : 1 - z) {
                        cursor.setWithOffset(centrePos, x, y, z);
                        if (!maid.isWithinRestriction(cursor)) {
                            continue;
                        }
                        if (!worldIn.getFluidState(cursor).is(net.minecraft.tags.FluidTags.WATER)) {
                            continue;
                        }
                        // 水面必须露天（否则浮漂会卡在方块里）
                        if (!worldIn.getBlockState(cursor.above()).getMaterial().isReplaceable()) {
                            continue;
                        }
                        double dist = maid.distanceToSqr(Vec3.atCenterOf(cursor));
                        if (dist >= bestDist) {
                            continue;
                        }
                        if (!maid.canPathReach(cursor)) {
                            continue;
                        }
                        bestDist = dist;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }
}


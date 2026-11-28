package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ride;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.fishing.FishingTypeManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook;
import com.github.tartaricacid.touhoulittlemaid.api.entity.fishing.IFishingType;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 骑乘状态下找水面并抛出鱼钩，移植自上游 {@code MaidRideFindWaterTask}。
 *
 * <p>它跑在 RIDE_WORK 活动里（女仆坐进船之后），是真正「开始钓鱼」的地方：
 * 螺旋扫描找到一片水，然后 {@code IFishingType} 造出鱼钩实体扔进去。
 * 之后的一切（等待、咬钩、结算）都由 {@link MaidFishingHook} 自己推进。</p>
 */
public class MaidRideFindWaterTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 100;
    /** 诊断状态：主手不是鱼竿。 */
    private static final int STATE_NO_ROD = 1;
    /** 诊断状态：搜索半径内没有水面。 */
    private static final int STATE_NO_WATER = 2;
    /** 诊断状态：先前找到的水面失效（离开范围 / 超出活动范围 / 已不是水）。 */
    private static final int STATE_WATER_LOST = 3;
    /** 诊断状态：成功抛出鱼钩。 */
    private static final int STATE_CAST = 4;

    private final int verticalSearchRange;
    private final int searchRange;

    /** 上一次诊断状态，用来避免重复打印。 */
    private int lastState = 0;

    protected int verticalSearchStart;
    private IFishingType fishingType = null;
    private BlockPos waterPos = null;

    public MaidRideFindWaterTask(int searchRange, int verticalSearchRange) {
        super(ImmutableMap.of());
        this.searchRange = searchRange;
        this.verticalSearchRange = verticalSearchRange;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        // 自愈：{@code owner.fishing} 非空就代表「她手上已经有一根钩子」，这条行为会一直不启动。
        // 而钩子被区块卸载、被 /kill、或在客户端侧先行移除时，不一定走得到
        // {@code MaidFishingHook#remove}（例如实体根本没进过 remove 流程就被回收），
        // 于是这个引用会永久残留 —— 表现就是「坐进船里以后再也不抛钩」，
        // 而且换任务、下车、重上船都救不回来（只有重进存档才行，因为该字段不落盘）。
        // 这里每次尝试启动时兜底清一次，代价是一次 isAlive() 调用。
        if (owner.fishing != null && !owner.fishing.isAlive()) {
            owner.fishing = null;
        }
        return super.checkExtraStartConditions(worldIn, owner) && owner.fishing == null;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        ItemStack rod = com.github.tartaricacid.touhoulittlemaid.util.FishingRodUtil.getRod(maid);
        this.fishingType = FishingTypeManager.getFishingType(rod);

        if (!this.fishingType.isFishingRod(rod)) {
            this.report(maid, STATE_NO_ROD, "neither hand has a fishing rod: main="
                                            + maid.getMainHandItem() + " off=" + maid.getOffhandItem());
            return;
        }
        if (this.waterPos == null) {
            this.searchForDestination(worldIn, maid, rod);
            if (this.waterPos == null) {
                this.report(maid, STATE_NO_WATER, "no water within " + this.searchRange + " blocks");
            }
            return;
        }
        if (!this.posCheck(maid)) {
            this.report(maid, STATE_WATER_LOST, "water at " + this.waterPos + " is out of range or restriction");
            this.waterPos = null;
            return;
        }
        if (this.fishingType.suitableFishingHook(maid, worldIn, rod, this.waterPos)) {
            Vec3 centerPos = Vec3.atCenterOf(this.waterPos);
            MaidFishingHook fishingHook = this.fishingType.getFishingHook(maid, worldIn, rod, centerPos);
            worldIn.addFreshEntity(fishingHook);
            worldIn.playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F,
                    0.4F / (worldIn.getRandom().nextFloat() * 0.4F + 0.8F));
            maid.swing(InteractionHand.MAIN_HAND);
            maid.getLookControl().setLookAt(centerPos);
            this.report(maid, STATE_CAST, "cast at " + this.waterPos);
        } else {
            this.report(maid, STATE_WATER_LOST, "water at " + this.waterPos + " is no longer fishable");
            this.waterPos = null;
        }
    }

    protected final void searchForDestination(ServerLevel worldIn, EntityMaid maid, ItemStack rod) {
        BlockPos centrePos = maid.getBrainSearchPos();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int y = this.verticalSearchStart; y <= this.verticalSearchRange; y = y > 0 ? -y : 1 - y) {
            for (int i = 0; i < this.searchRange; ++i) {
                for (int x = 0; x <= i; x = x > 0 ? -x : 1 - x) {
                    for (int z = x < i && x > -i ? i : 0; z <= i; z = z > 0 ? -z : 1 - z) {
                        mutableBlockPos.setWithOffset(centrePos, x, y - 1, z);
                        if (maid.isWithinRestriction(mutableBlockPos)
                            && this.fishingType.suitableFishingHook(maid, worldIn, rod, mutableBlockPos)) {
                            maid.getLookControl().setLookAt(mutableBlockPos.getX(), mutableBlockPos.getY(),
                                    mutableBlockPos.getZ());
                            this.waterPos = mutableBlockPos.immutable();
                            this.setNextCheckTickCount(5);
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean posCheck(EntityMaid maid) {
        // 注意这里**不能**直接用 searchRange 的平方当阈值。
        //
        // searchForDestination 是按「整数方块偏移」搜的：x/z 覆盖 [-(searchRange-1), searchRange-1]，
        // y 覆盖 [-(1), verticalSearchRange-1]，折成平方距离最大可达
        // (searchRange-1)² + verticalSearchRange² + (searchRange-1)²，明显大于 searchRange²。
        // 再用 searchRange² 去判「水还在不在范围内」，就会出现
        // 「刚搜到的水立刻被判失效 → waterPos 清空 → 下一次又搜到同一片水」的死循环，
        // 表现为女仆站在水边一动不动、永远不抛钩（日志里是
        // "water at ... is out of range or restriction" 反复出现）。
        //
        // 改用「方块中心」+ 放宽 2 格：既覆盖搜索范围的全部可能性，又容忍
        // 女仆站在方块角落（浮点位置最多比方块坐标远 √3 格）带来的误差。
        double maxDistance = this.searchRange + 2.0;
        Vec3 waterVec = Vec3.atCenterOf(this.waterPos);
        return maid.distanceToSqr(waterVec) < maxDistance * maxDistance
                && maid.isWithinRestriction(this.waterPos);
    }

    /**
     * 只在「状态发生变化」时记一条日志。
     *
     * <p><b>为什么需要它：</b>这条链路一旦卡住是完全静默的 —— 女仆坐进船里、
     * 手里有鱼竿，却既没有鱼钩也没有任何提示。玩家能观察到的只有「她不动」，
     * 而可能的原因至少有四种（主手不是鱼竿 / 6 格内没水 / 水超出活动范围 /
     * 骑乘活动根本没激活）。这条日志把前三种区分开，剩下的就是活动表的问题。</p>
     *
     * <p>不做每 tick 打印：{@code start} 每 100~200 tick 才跑一次，但一条日志
     * 一直重复也没有意义，状态没变就不打。</p>
     */
    private void report(EntityMaid maid, int state, String detail) {
        if (this.lastState == state) {
            return;
        }
        this.lastState = state;
        TouhouLittleMaid.LOGGER.debug("[Futo] fishing maid={} at={} -> {}",
                maid.getUUID(), maid.blockPosition(), detail);
    }
}
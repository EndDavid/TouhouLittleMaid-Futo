package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ride.MaidRideFindWaterTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFindSitTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 钓鱼模式，移植自上游 {@code entity/task/TaskFishing}。
 *
 * <p>两段式，与上游一致：</p>
 * <ol>
 *     <li>WORK 活动里跑 {@link MaidFindSitTask} —— 找水边的<b>船</b>（或女仆坐垫）走过去坐下；</li>
 *     <li>坐下之后活动切到 RIDE_WORK，由 {@link MaidRideFindWaterTask} 螺旋扫描水面并抛出鱼钩，
 *         之后的一切（等待、咬钩、结算）都由 {@code MaidFishingHook} 自己推进。</li>
 * </ol>
 *
 * <p><b>曾经绕开过这条路：</b>早期版本因为 GeckoLib 管线的「骑乘 + 非人形模型上下翻转」
 * 没有修，改成在 WORK 活动里直接「走到岸边抛钩」（{@code MaidFishingCastTask}）。
 * 骑乘模型倒置修好之后已回归上游的坐船 / 坐垫路线，那个临时的岸钓行为类也一并删除 ——
 * 两者同时挂在 WORK 会让女仆优先在岸上抛钩，「坐船钓鱼」永远不会发生。</p>
 *
 * <p><b>与上游的差异：</b>上游用 {@code ToolActions.FISHING_ROD_CAST}（Forge 的工具动作补丁）
 * 判断鱼竿，本移植版没有那套动作表，因此退化为 {@code instanceof FishingRodItem}；
 * 上游的 {@code onFunctionCallSwitch}（「呼叫时自动从背包掏鱼竿」）依赖尚未移植的
 * {@code TaskEquipUtil}，这里同样没有。</p>
 */
public class TaskFishing implements IMaidTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "fishing");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.FISHING_ROD.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return InitSounds.MAID_MODE_IDLE;
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // 上游 1.20.1：WORK 只负责「找到船 / 坐垫坐下」，抛钩交给 RIDE_WORK。
        return Lists.newArrayList(Pair.of(5, new MaidFindSitTask(0.6F)));
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return Lists.newArrayList(Pair.of(5, new MaidRideFindWaterTask(6, 3)));
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        // 主手或副手都认（上游只看主手，见 FishingRodUtil 的说明）。
        return Collections.singletonList(
                Pair.of("has_fishing_rod", com.github.tartaricacid.touhoulittlemaid.util.FishingRodUtil::hasRod));
    }

    @Override
    public boolean workPointTask(EntityMaid maid) {
        return true;
    }
}
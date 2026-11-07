package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ride.MaidRideFindWaterTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFishingCastTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 钓鱼模式，移植自上游 {@code entity/task/TaskFishing}。
 *
 * <p><b>与上游的一处结构性差异：</b>上游在 WORK 活动里只做「找船 / 坐垫坐上去」，
 * 真正抛钩发生在 RIDE_WORK 活动。这套设计在本移植版行不通，原因有两个：</p>
 * <ol>
 *     <li>可坐对象是 {@code EntityChair}（独立方块实体，尚未移植）与 {@code Boat}；
 *         两者都<b>不是</b> {@code LivingEntity}，而上游是从
 *         {@code NEAREST_VISIBLE_LIVING_ENTITIES} 里筛它们的 —— 那个记忆永远不含 Boat，
 *         所以「女仆对水边的船毫无反应」。</li>
 *     <li>本移植版的 GeckoLib 渲染管线在「骑乘 + 非人形模型」时会把模型上下翻转。</li>
 * </ol>
 * <p>因此这里改为：WORK 活动直接「走到岸边抛钩」（{@link MaidFishingCastTask}），
 * RIDE_WORK 保留上游的骑乘抛钩（{@link MaidRideFindWaterTask}），两条路都能用。</p>
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
        return Lists.newArrayList(Pair.of(5, new MaidFishingCastTask(8, 3)));
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return Lists.newArrayList(Pair.of(5, new MaidRideFindWaterTask(6, 3)));
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Collections.singletonList(
                Pair.of("has_fishing_rod", e -> e.getMainHandItem().getItem() instanceof FishingRodItem));
    }

    @Override
    public boolean workPointTask(EntityMaid maid) {
        return true;
    }
}


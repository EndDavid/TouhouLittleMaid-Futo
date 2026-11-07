package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidExtinguishingTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 灭火模式，移植自上游 {@code entity/task/TaskExtinguishing}。
 *
 * <p>唯一需要「主手持有特定物品」才能工作的模式：主手必须是灭火器。</p>
 *
 * <p>{@link #enablePanic} 返回 {@code false} 是刻意为之 —— 若不禁用慌乱 AI，
 * 女仆自己着火后会先乱跑，永远轮不到灭火行为执行。</p>
 */
public class TaskExtinguishing implements IMaidTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "extinguishing");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return InitItems.EXTINGUISHER.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_EXTINGUISHING, 0.5f);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return Lists.newArrayList(Pair.of(5, new MaidExtinguishingTask(0.6f)));
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Collections.singletonList(Pair.of("has_extinguisher", this::hasExtinguisher));
    }

    @Override
    public boolean enablePanic(EntityMaid maid) {
        return false;
    }

    private boolean hasExtinguisher(EntityMaid maid) {
        return maid.getMainHandItem().getItem() == InitItems.EXTINGUISHER;
    }
}


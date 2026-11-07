package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidTorchMoveTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidTorchPlaceTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
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
 * 插火把模式，移植自上游 {@code entity/task/TaskTorch}。
 *
 * <p>两个行为的优先级不同：找位置是 5、真正插下是 6 —— 也就是「移动」先跑，
 * 「放置」后跑，这样女仆走到位的那一 tick 就能立刻插上。</p>
 */
public class TaskTorch implements IMaidTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "torch");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.TORCH.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_TORCH, 0.5f);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        MaidTorchMoveTask maidTorchMoveTask = new MaidTorchMoveTask(0.6f);
        MaidTorchPlaceTask maidTorchPlaceTask = new MaidTorchPlaceTask(2);
        return Lists.newArrayList(Pair.of(5, maidTorchMoveTask), Pair.of(6, maidTorchPlaceTask));
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Collections.singletonList(Pair.of("has_torch", this::hasTorch));
    }

    private boolean hasTorch(EntityMaid maid) {
        return ItemsUtil.isStackIn(maid.getAvailableInv(false), stack -> stack.getItem() == Items.TORCH);
    }
}


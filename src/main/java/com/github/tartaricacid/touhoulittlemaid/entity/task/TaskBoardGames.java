package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBoardGameTask;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
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

import java.util.List;

/**
 * 「下棋」工作模式，移植自上游 {@code entity/task/TaskBoardGames}。
 *
 * <p>它只挂一个行为 {@link MaidBoardGameTask}，再加一句 {@link #canSitInJoy}。
 * 后者是这一整套娱乐方块能成立的关键：{@code EntitySit#tickMaid} 每 20 tick 会检查
 * 「工作模式下这个任务允许你坐在这张桌子旁吗」，默认答案是<b>不允许</b> ——
 * 于是所有其它工作模式（种田、攻击、灭火……）的女仆都会立刻站起来。
 * 只有这里返回 true，下棋的女仆才能一直坐着。</p>
 *
 * <p><b>与上游的差异：</b>上游的图标是五子棋棋子（{@code InitItems.GOMOKU}），
 * 而五子棋棋盘还没移植，所以暂用中国象棋棋盘当图标；等五子棋进来时改一行即可。</p>
 */
public class TaskBoardGames implements IMaidTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "board_games");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(InitItems.CCHESS);
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_MODE_IDLE, 0.5f);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // 优先级 5、移动速度 0.6、两格内算「走到」
        Pair<Integer, Behavior<? super EntityMaid>> boardGame = Pair.of(5, new MaidBoardGameTask(0.6f, 2));
        return Lists.newArrayList(boardGame);
    }

    @Override
    public boolean canSitInJoy(EntityMaid maid, String joyType) {
        return Type.GOMOKU.getTypeName().equals(joyType);
    }
}


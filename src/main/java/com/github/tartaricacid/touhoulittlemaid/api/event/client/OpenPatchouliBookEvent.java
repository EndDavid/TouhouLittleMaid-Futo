package com.github.tartaricacid.touhoulittlemaid.api.event.client;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.eventbus.api.Event;

/**
 * 用于侧边栏帕秋莉按钮打开「记忆中的幻想乡」手册。
 *
 * <p>默认行为（见 {@code compat/patchouli/OpenDefaultBook}）是打开手册上一次停留的页面；
 * 其它模组可以监听本事件，按 {@link #getTask()} 打开自己手册的任意页面。</p>
 *
 * <p>本移植版没有 Forge 的模组事件总线，但兼容层里保留了一个极简的
 * {@code MinecraftForge.EVENT_BUS}，语义与上游一致：只在本模组任务
 * （命名空间为 {@code touhou_little_maid}）时由默认监听器处理。</p>
 */
public class OpenPatchouliBookEvent extends Event {
    private final EntityMaid maid;
    private final IMaidTask task;

    public OpenPatchouliBookEvent(EntityMaid maid, IMaidTask task) {
        this.maid = maid;
        this.task = task;
    }

    public EntityMaid getMaid() {
        return maid;
    }

    public IMaidTask getTask() {
        return task;
    }
}


package com.github.tartaricacid.touhoulittlemaid.api.event.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraftforge.eventbus.api.Event;

/**
 * 「全局配置界面正在构建，想加条目的插件可以往里塞」的事件，移植自上游
 * {@code api/event/client/AddClothConfigEvent}。
 *
 * <p>上游在 {@code MenuIntegration#getConfigBuilder} 的末尾把它 post 到
 * {@code MinecraftForge.EVENT_BUS}，让附属模组（或本模组自己的其它模块）
 * 不用改 {@code MenuIntegration} 就能往界面上加东西。</p>
 *
 * <p>本移植版的 {@code net.minecraftforge.eventbus.api.Event} 是<b>自建的兼容层</b>
 * （见 {@code src/main/java/net/minecraftforge/eventbus/}）：只实现
 * {@code post / register / 监听器调用} 这几个本仓库真正用到的语义，
 * 没有 Forge 的 {@code @Cancelable} 传播、优先级排序之外的花样。
 * 对这个事件来说够用 —— 它只需要「有人 post、有人收」。</p>
 */
public class AddClothConfigEvent extends Event {
    private final ConfigBuilder root;
    private final ConfigEntryBuilder entryBuilder;

    public AddClothConfigEvent(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        this.root = root;
        this.entryBuilder = entryBuilder;
    }

    public ConfigBuilder getRoot() {
        return root;
    }

    public ConfigEntryBuilder getEntryBuilder() {
        return entryBuilder;
    }
}


package com.github.tartaricacid.touhoulittlemaid.util.version;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * 组件（Component）的短写工具，移植自上游 {@code util/version/TComponent}。
 *
 * <p>它只是把 {@code new TranslatableComponent(key)} / {@code new TextComponent(s)}
 * 缩短成 {@code TComponent.translatable(key)} / {@code TComponent.literal(s)}。
 * 存在的意义是<b>让上游那些移植文件可以逐字保留</b> ——
 * {@code compat/cloth/*} 里 100 多处都写成这个形式，改名反而是纯粹的噪声。</p>
 */
public final class TComponent {
    public static final Component EMPTY = TextComponent.EMPTY;

    private TComponent() {
    }

    public static Component empty() {
        return EMPTY;
    }

    public static MutableComponent translatable(String key) {
        return new TranslatableComponent(key);
    }

    public static MutableComponent translatable(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public static MutableComponent literal(String string) {
        return new TextComponent(string);
    }
}


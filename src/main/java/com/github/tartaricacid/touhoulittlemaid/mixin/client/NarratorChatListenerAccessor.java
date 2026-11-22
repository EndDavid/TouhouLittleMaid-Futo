package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.mojang.text2speech.Narrator;
import net.minecraft.client.gui.chat.NarratorChatListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 把 {@link NarratorChatListener} 的 {@code narrator} 字段暴露出来。
 *
 * <p>上游 Forge 版靠 AccessTransformer 把它变成 public；1.18.2 Fabric 没有 AT，
 * 所以用一个只读的 {@code @Accessor} 达到同样效果。用途见
 * {@code client/sound/SystemNarrator}（系统 TTS 站点要直接朗读）。</p>
 */
@Mixin(NarratorChatListener.class)
public interface NarratorChatListenerAccessor {
    @Accessor("narrator")
    Narrator touhouLittleMaid$getNarrator();
}


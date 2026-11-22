package com.github.tartaricacid.touhoulittlemaid.client.sound;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.mixin.client.NarratorChatListenerAccessor;
import com.mojang.text2speech.Narrator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.chat.NarratorChatListener;

/**
 * 用「系统旁白」（{@code com.mojang.text2speech}）朗读一段文本，
 * 对应上游 {@code TTSSystemAudioToClientMessage} 里的
 * {@code NarratorChatListener.INSTANCE.narrator.say(text, true)}。
 *
 * <p><b>为什么不直接调 {@code NarratorChatListener#sayNow(String)}：</b>
 * 那个方法会先检查「旁白」游戏设置，关闭时静默不读；而这里的调用来自玩家
 * 自己在站点里选 {@code api_type: system}，属于显式请求，上游也是绕过开关直接朗读的。
 * 但 {@code narrator} 字段在 1.18.2 里是 {@code private}（Forge 侧靠 AT 打开），
 * 本仓库没有 AT，所以用一个 {@code @Accessor} mixin 取出来。</p>
 *
 * <p>旁白库缺失时（Linux 无 speech-dispatcher 等），{@code Narrator} 会是空实现，
 * {@code say} 什么也不做，因此不需要额外判空。</p>
 */
@Environment(EnvType.CLIENT)
public final class SystemNarrator {
    public static void say(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            Narrator narrator = ((NarratorChatListenerAccessor) NarratorChatListener.INSTANCE).touhouLittleMaid$getNarrator();
            narrator.say(text, true);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("[Futo] system narrator is unavailable", e);
        }
    }

    private SystemNarrator() {
    }
}


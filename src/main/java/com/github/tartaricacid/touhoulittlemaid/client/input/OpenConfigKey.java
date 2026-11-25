package com.github.tartaricacid.touhoulittlemaid.client.input;

import com.github.tartaricacid.touhoulittlemaid.compat.cloth.ClothConfigCompat;
import com.github.tartaricacid.touhoulittlemaid.util.version.TComponent;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * 「打开模组配置界面」的按键，默认 <b>未绑定</b>（{@link GLFW#GLFW_KEY_UNKNOWN}）。
 *
 * <h2>为什么需要这个键</h2>
 * <p>上游在 Forge 上靠 {@code ConfigGuiHandler.ConfigGuiFactory} 暴露配置界面 ——
 * 那个入口在 Fabric 上由 <b>ModMenu</b> 提供。但 ModMenu 是可选模组：
 * 只装 Fabric API + ForgeConfigAPIPort + Cloth Config 的玩家，
 * 就<b>完全没有</b>办法打开这张配置表。</p>
 *
 * <p>所以这里补一个按键。默认不绑定（{@code -1}），不会和任何已有按键冲突，
 * 玩家想用自己去「控制」里设一个。这样既补上了缺口，又不打扰不关心配置的人。</p>
 *
 * <h2>没装 Cloth 时会怎样</h2>
 * <p>打开 {@code client/gui/mod/ClothConfigScreen} 那一页，告诉你「需要安装 Cloth Config」
 * 并给一个下载链接 —— 而不是什么都不发生。</p>
 *
 * <h2>为什么只在「没开任何界面」时才响应</h2>
 * <p>在背包 / 聊天里误触这个键会直接把当前界面顶掉，非常烦。所以要求
 * {@code client.screen == null}（即正在游戏里、没有打开任何 GUI）。</p>
 */
@Environment(EnvType.CLIENT)
public final class OpenConfigKey {
    /** 与上游一致的按键分类：设置在「杂项」页里找得到。 */
    private static final String CATEGORY = "key.touhou_little_maid.category";
    private static final String NAME = "key.touhou_little_maid.open_config.desc";

    private static KeyMapping openConfigKey;

    private OpenConfigKey() {
    }

    public static void init() {
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                NAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // consumeClick() 会清掉点击计数，所以必须先取再判（与 DismountBroomKey 同一个理由）
            if (!openConfigKey.consumeClick()) {
                return;
            }
            if (client.player == null || client.level == null) {
                return;
            }
            // 已经开着界面时不响应：在背包 / 聊天里误触会把当前界面顶掉
            Screen current = client.screen;
            if (current != null) {
                return;
            }
            if (ClothConfigCompat.isAvailable()) {
                ClothConfigCompat.openConfigScreen();
            } else {
                com.github.tartaricacid.touhoulittlemaid.client.gui.mod.ClothConfigScreen.open();
            }
        });
    }

    /** 供「按键提示」类文本引用（例如聊天栏里告诉玩家按哪个键打开配置）。 */
    public static KeyMapping getOpenConfigKey() {
        return openConfigKey;
    }

    /** 未绑定时给玩家一句可读的说明，避免提示里出现空白。 */
    public static net.minecraft.network.chat.Component displayName() {
        if (openConfigKey == null) {
            return TComponent.translatable(NAME);
        }
        return openConfigKey.getTranslatedKeyMessage();
    }
}


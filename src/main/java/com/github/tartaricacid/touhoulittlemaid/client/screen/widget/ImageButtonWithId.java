package com.github.tartaricacid.touhoulittlemaid.client.screen.widget;

import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 带下标的 {@link ImageButton}，移植自上游 {@code client/gui/widget/button/ImageButtonWithId}。
 *
 * <p>模型切换器的模式列表是<b>循环创建的</b>，而 {@code OnPress} 拿到的是按钮自己，
 * 光凭它分不出「我点的是第几行」。上游的做法就是给按钮挂一个下标字段，
 * 回调里再 {@code ((ImageButtonWithId) b).getIndex()} 取回来。</p>
 *
 * <p>1.18.2 的 {@code ImageButton} 没有 {@code Button.builder}，只有长构造器；
 * 这里只保留上游最常用的那个短重载（纹理尺寸默认 256×256，与 {@code model_switcher.png} 一致）。</p>
 */
public class ImageButtonWithId extends ImageButton {
    private final int index;

    public ImageButtonWithId(int index, int x, int y, int width, int height,
                             int u, int v, int hoverVOffset, ResourceLocation texture,
                             OnPress onPress) {
        super(x, y, width, height, u, v, hoverVOffset, texture, 256, 256, onPress, TextComponent.EMPTY);
        this.index = index;
    }

    public int getIndex() {
        return this.index;
    }
}


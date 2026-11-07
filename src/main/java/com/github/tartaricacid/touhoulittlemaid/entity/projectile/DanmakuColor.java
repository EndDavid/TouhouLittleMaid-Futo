package com.github.tartaricacid.touhoulittlemaid.entity.projectile;

import java.util.Random;

/**
 * 弹幕颜色，移植自上游 {@code entity/projectile/DanmakuColor}。
 *
 * <p>枚举<b>顺序</b>即贴图列索引（{@code textures/entity/danmaku.png} 里每行
 * 13 个 32x32 方块，一行对应一种 {@link DanmakuType}），因此顺序不能改。</p>
 */
public enum DanmakuColor {
    RED(0xff0000),
    ORANGE(0xff7f00),
    YELLOW(0xfeff00),
    LIME(0x7fff00),
    LIGHT_GREEN(0x00ff00),
    GREEN(0x00ff7f),
    CYAN(0x1dfeff),
    LIGHT_BLUE(0x007fff),
    BLUE(0x0000ff),
    PURPLE(0x7f00ff),
    MAGENTA(0xff00fe),
    PINK(0xff007f),
    GRAY(0x8c8c8c);

    private final int rgb;
    private final int red;
    private final int green;
    private final int blue;

    DanmakuColor(int color) {
        this.rgb = color;
        this.red = color >> 16 & 255;
        this.green = color >> 8 & 255;
        this.blue = color & 255;
    }

    public static DanmakuColor getColor(int index) {
        if (index < 0 || index >= values().length) {
            return RED;
        }
        return values()[index];
    }

    public static DanmakuColor random(Random random) {
        return getColor(random.nextInt(getLength()));
    }

    public static int getLength() {
        return values().length;
    }

    public int getRed() {
        return this.red;
    }

    public int getGreen() {
        return this.green;
    }

    public int getBlue() {
        return this.blue;
    }

    public int getRgb() {
        return this.rgb;
    }
}


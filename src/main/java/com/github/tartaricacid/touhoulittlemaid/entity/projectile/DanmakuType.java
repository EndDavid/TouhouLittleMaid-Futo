package com.github.tartaricacid.touhoulittlemaid.entity.projectile;

import java.util.Random;

/**
 * 弹幕形状，移植自上游 {@code entity/projectile/DanmakuType}。
 *
 * <p>枚举<b>顺序</b>即贴图行索引（{@code textures/entity/danmaku.png} 共 4 行，
 * 每行 13 个 32x32 方块），因此顺序不能改。</p>
 *
 * <p>{@code size} 是渲染时的半边长（方块单位），与碰撞箱无关。</p>
 */
public enum DanmakuType {
    /** 点弹 */
    PELLET(0.6D),
    /** 小玉 */
    BALL(0.5D),
    /** 环玉 */
    ORBS(0.3D),
    /** 中玉 */
    BIG_BALL(0.5D);

    private final double size;

    DanmakuType(double size) {
        this.size = size;
    }

    public static DanmakuType getType(int index) {
        if (index < 0 || index >= values().length) {
            return PELLET;
        }
        return values()[index];
    }

    public static DanmakuType random(Random random) {
        return getType(random.nextInt(getLength()));
    }

    public static int getLength() {
        return values().length;
    }

    public double getSize() {
        return size;
    }
}


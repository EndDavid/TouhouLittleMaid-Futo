package com.github.tartaricacid.touhoulittlemaid.block.properties;

import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

/**
 * 三乘三棋盘的九宫格部件，移植自上游 {@code block/properties/GomokuPart}。
 *
 * <p>象棋（3×3）、五子棋（3×3）、国际象棋（3×3）都是把一个 3×3 的方块阵当成一张桌子：
 * 只有 {@link #CENTER} 那一格带方块实体、画模型，其余八格只是占位 ——
 * 否则拆掉任意一格，剩下的部分就会变成「没人管的方块残骸」。</p>
 *
 * <p>坐标用 {@code -1 / 0 / 1} 表示相对中心的方向，{@code getPosX()} 对应 X 轴、
 * {@code getPosY()} 对应 Z 轴。由中心反推中心位置的减法就是靠这两个数。</p>
 */
public enum GomokuPart implements StringRepresentable {
    LEFT_UP("left_up", -1, -1),
    UP("up", 0, -1),
    RIGHT_UP("right_up", 1, -1),
    LEFT_CENTER("left_center", -1, 0),
    CENTER("center", 0, 0),
    RIGHT_CENTER("right_center", 1, 0),
    LEFT_DOWN("left_down", -1, 1),
    DOWN("down", 0, 1),
    RIGHT_DOWN("right_down", 1, 1);

    private final String name;
    private final int posX;
    private final int posY;

    GomokuPart(String name, int posX, int posY) {
        this.name = name;
        this.posX = posX;
        this.posY = posY;
    }

    /**
     * 由相对偏移反查部件；偏移超出 3×3 范围时返回 {@code null}。
     */
    @Nullable
    public static GomokuPart getPartByPos(int x, int y) {
        for (GomokuPart part : GomokuPart.values()) {
            if (part.getPosX() == x && part.getPosY() == y) {
                return part;
            }
        }
        return null;
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    @Override
    public String toString() {
        return String.format("%s[%d, %d]", this.getSerializedName(), this.posX, this.posY);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean isCenter() {
        return this == CENTER;
    }
}


package com.github.tartaricacid.touhoulittlemaid.entity.passive;

/**
 * 女仆 GUI 顶部三个页签的下标，移植自上游 {@code entity/passive/TabIndex}。
 *
 * <p>上游就是三个 {@code int} 常量，没有接口、没有枚举。
 * 页签切换的做法也不是「在同一个界面里换内容」，而是
 * <b>服务端重新打开一个不同的容器菜单</b>（见 {@code EntityMaid#openMaidGui(Player, int)}）。
 * 因此下标只用于服务端选菜单，客户端不参与任何分派。</p>
 */
public final class TabIndex {
    public static final int MAIN = 0;
    public static final int TASK_CONFIG = 1;
    public static final int MAID_CONFIG = 2;

    private TabIndex() {
    }
}


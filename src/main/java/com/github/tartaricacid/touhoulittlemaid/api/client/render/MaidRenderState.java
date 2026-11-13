package com.github.tartaricacid.touhoulittlemaid.api.client.render;

/**
 * 女仆的渲染状态，逐字移植自上游 {@code api/client/render/MaidRenderState}。
 *
 * <p>用来区分「这是一只活的女仆」还是「一个正在被别人画出来的女仆快照」。
 * 雕像（{@code TileEntityStatueRenderer}）与手办（{@code TileEntityGarageKitRenderer}）
 * 都是把实体 NBT 读进一个<b>不参与 tick 的临时实体</b>再交给
 * {@code EntityRenderDispatcher} 画，它们会把这个字段置成对应值，
 * 好让渲染链路上需要「这只是快照」的地方有据可依（也是给附属模组留的公开 API）。</p>
 */
public enum MaidRenderState {
    /**
     * 普通实体渲染状态，默认情况。
     */
    ENTITY,
    /**
     * 雕像，方块形态的。
     */
    STATUE,
    /**
     * 手办，可以是方块形态，也可以是物品形态。
     */
    GARAGE_KIT
}


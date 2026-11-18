package com.github.tartaricacid.touhoulittlemaid.client.render;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 手持隙间时，把<b>绑定的那个箱子</b>用红色线框框出来，
 * 移植自上游 {@code client/event/WirelessIORenderEvent}。
 *
 * <p>存在的理由很实际：隙间绑定的是坐标，而玩家完全可能在几十格以外绑定它，
 * 回头就忘了绑的是哪个箱子。没有这个线框，唯一的确认手段是去翻物品说明里的
 * 那行「绑定坐标：x, y, z」，再在世界里数格子。</p>
 *
 * <p><b>渲染时机：</b>上游用 Forge 的 {@code RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS}，
 * 这里用 Fabric 的 {@link WorldRenderEvents#LAST}（注入在
 * {@code WorldRenderer#renderChunkDebugInfo}，是同一段「世界的最后一遍」）。
 * 坐标处理与 {@link MaidAreaRenderer} 完全相同：进入
 * {@link RenderHelper#beginCameraSpace()} 之后一律用世界绝对坐标、画完立刻 flush。</p>
 *
 * <p><b>与上游的两处差异：</b></p>
 * <ol>
 *     <li>线框外扩 0.002 格。上游用 {@code new AABB(pos)} 直接画，
 *         线框与方块表面共面 —— 在多数角度会与方块本身的描边 z-fighting，
 *         表现为「线框一半闪没了」。</li>
 *     <li>线宽用 2.0（与女仆区域线一致），上游是默认的 1.0。
 *         1.0 宽的线在 1080p 下只有 1 像素，远看几乎看不见。</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class WirelessIORenderer {
    private static final float LINE_WIDTH = 2.0F;
    private static final double INFLATE = 0.002D;

    private WirelessIORenderer() {
    }

    public static void init() {
        WorldRenderEvents.LAST.register(WirelessIORenderer::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ItemStack mainStack = minecraft.player.getMainHandItem();
        if (mainStack.getItem() != InitItems.WIRELESS_IO) {
            return;
        }
        BlockPos pos = ItemWirelessIO.getBindingPos(mainStack);
        if (pos == null) {
            return;
        }
        if (!minecraft.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }
        // 箱子在别的维度时不画：坐标只在自己的维度里有意义
        if (minecraft.level.getBlockEntity(pos) == null) {
            return;
        }

        AABB box = new AABB(pos).inflate(INFLATE);
        PoseStack cameraSpace = RenderHelper.beginCameraSpace();
        try {
            BufferBuilder lines = RenderHelper.beginLines(LINE_WIDTH);
            renderBox(lines, box);
            RenderHelper.endLines();
        } finally {
            RenderHelper.endCameraSpace(cameraSpace);
        }
    }

    /**
     * 立方体的 12 条棱。
     *
     * <p>{@code RenderHelper} 只提供 {@code renderCylinder}（水平圆环），
     * 没有现成的线框函数 —— 女仆区域那边画的是填充框。
     * 这里直接按「下底 4 条 + 上底 4 条 + 4 根立柱」写出来，
     * 12 条棱每条都要写（不能用三角带）。</p>
     */
    private static void renderBox(BufferBuilder builder, AABB box) {
        // 下底
        edge(builder, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ);
        edge(builder, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
        edge(builder, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
        edge(builder, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ);
        // 上底
        edge(builder, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ);
        edge(builder, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
        edge(builder, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);
        edge(builder, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ);
        // 立柱
        edge(builder, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ);
        edge(builder, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
        edge(builder, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);
        edge(builder, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ);
    }

    /**
     * 红色棱（上游的颜色是 {@code (1, 0, 0, 1)}）。
     */
    private static void edge(BufferBuilder builder, double x1, double y1, double z1,
                             double x2, double y2, double z2) {
        RenderHelper.renderLine(builder, new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), 1.0F, 0.0F, 0.0F);
    }
}


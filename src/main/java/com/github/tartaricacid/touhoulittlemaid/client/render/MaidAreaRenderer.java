package com.github.tartaricacid.touhoulittlemaid.client.render;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 手持指南针右击女仆后，在世界里显示她的工作 / 休息 / 睡觉区域。
 *
 * <p>结构照抄上游 {@code client/event/MaidAreaRenderEvent}（Forge 1.18.2），
 * 只把「Forge 的 {@code RenderLevelLastEvent}」换成 Fabric 的
 * {@link WorldRenderEvents#LAST}（它注入在 {@code WorldRenderer#renderChunkDebugInfo}，
 * 与上游是同一渲染时刻）。</p>
 *
 * <h2>坐标约定</h2>
 *
 * <p>整块绘制跑在 {@link RenderHelper#beginCameraSpace()} 里：modelview 被重置成
 * 「相机旋转 × 平移(-相机位置)」，于是<b>顶点一律用世界绝对坐标、不乘任何矩阵</b>，
 * 并且<b>当场 flush</b>。这与 vanilla 自己的区块调试框、方块描边是同一套写法
 * （反编译 {@code WorldRenderer#renderChunkDebugInfo} 核对过）。</p>
 *
 * <p>之前几版之所以「圈随视角转 / 文字糊脸 / 黄框漂移」，是因为既依赖了
 * {@code WorldRenderEvents.LAST} 时 modelview 的残留状态，又把顶点交给了延迟刷出的
 * {@code RenderBuffers} —— 刷出时 modelview 早已被还原。</p>
 */
@Environment(EnvType.CLIENT)
public final class MaidAreaRenderer {
    private static final long SHOW_DURATION_TICKS = 600L;
    /**
     * 圆环的多边形边数，与上游一致。
     */
    private static final int CIRCLE_PRECISION = 16;
    private static final float TEXT_SCALE = 0.15F;
    private static final float LINE_WIDTH = 2.0F;

    /**
     * 实体 id → 到期时刻（{@code level.getGameTime()}）。
     */
    private static final Map<Integer, Long> SHOWN = new HashMap<>();

    private MaidAreaRenderer() {
    }

    public static void init() {
        WorldRenderEvents.LAST.register(MaidAreaRenderer::render);
    }

    /**
     * 客户端调用：让这只女仆的区域显示 30 秒（重复右击会顺延）。
     */
    public static void show(EntityMaid maid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        SHOWN.put(maid.getId(), minecraft.level.getGameTime() + SHOW_DURATION_TICKS);
    }

    private static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || SHOWN.isEmpty()) {
            return;
        }
        if (!minecraft.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }

        long now = minecraft.level.getGameTime();
        Iterator<Map.Entry<Integer, Long>> iterator = SHOWN.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
            }
        }

        List<EntityMaid> maids = new ArrayList<>();
        for (Integer id : SHOWN.keySet().toArray(new Integer[0])) {
            if (minecraft.level.getEntity(id) instanceof EntityMaid maid && maid.isAlive()) {
                maids.add(maid);
            }
        }
        if (maids.isEmpty()) {
            return;
        }

        PoseStack cameraSpace = RenderHelper.beginCameraSpace();
        try {
            // 线段：红线 + 三条同心环，写完立刻刷出
            BufferBuilder lines = RenderHelper.beginLines(LINE_WIDTH);
            for (EntityMaid maid : maids) {
                renderAreaLines(lines, minecraft, maid);
            }
            RenderHelper.endLines();

            // 实心高亮框：单独一批（不同的着色器与顶点格式）
            for (EntityMaid maid : maids) {
                AABB box = maid.getBoundingBox();
                RenderHelper.addChainedFilledBoxVertices(box, 0.8F, 0.8F, 0.2F, 0.75F);
            }

            // 文字：immediate 源，drawInBatch 之后 endBatch 立刻绘制
            for (EntityMaid maid : maids) {
                renderAreaTexts(minecraft, maid);
            }
        } finally {
            RenderHelper.endCameraSpace(cameraSpace);
        }
    }

    /**
     * 区域中心（世界坐标）—— <b>女仆的「居家位置」，与玩家无关</b>。
     *
     * <p>上游 {@code MaidAreaRenderEvent#renderPos} 收到的是
     * {@code pos.getWorkPos()} / {@code pos.getIdlePos()} / {@code pos.getSleepPos()}，
     * 也就是 {@code SchedulePos} 里记下的「开启居家模式时女仆所在的位置」。
     * 只在第一次开启时写入，之后女仆走动、玩家走开都不会变。</p>
     *
     * <p>早期版本在「居家模式关闭」时退化成以玩家为中心画圈，于是整组环跟着玩家跑 ——
     * 那既不是上游行为，也和这个指南针的用途（看女仆在哪儿干活）完全相反。
     * 现在中心只认 {@link EntityMaid#getHomePos()}；没设定过（{@code BlockPos.ZERO}）
     * 就什么都不画，与上游 {@code if (!schedulePos.getWorkPos().equals(BlockPos.ZERO))} 一致。</p>
     */
    private static Vec3 areaCenter(EntityMaid maid) {
        BlockPos center = maid.getHomePos();
        // +0.5 是方块中心；再 +1.0 对应上游 MaidAreaRenderEvent#renderPos 开头那一句
        // poseStack.translate(0, 1, 0)。也就是说圆环画在「锚点方块**上表面之上 1 格**」，
        // 相对女仆脚底约 1.5 格高 —— 太低会整个嵌进草方块 / 台阶里看不见。
        return new Vec3(center.getX() + 0.5D, center.getY() + 1.5D, center.getZ() + 0.5D);
    }

    /**
     * 这只女仆的区域要不要画：必须先有「居家位置」。
     */
    private static boolean hasArea(EntityMaid maid) {
        return maid.hasHomePos();
    }

    private static void renderAreaLines(BufferBuilder lines, Minecraft minecraft, EntityMaid maid) {
        if (!hasArea(maid)) {
            return;
        }
        Vec3 center = areaCenter(maid);
        // 与上游一致：整块绘制都抬高了 1 格，所以连线终点也要抬 1 格，
        // 否则红线会从高处斜插到女仆脚底。
        Vec3 maidPos = maid.position().add(0.0D, 1.0D, 0.0D);

        // 红线：把「当初设定的位置」和「女仆现在的位置」连起来。
        // 居家模式下起点就是区域中心（上游同款）；关掉居家模式时上游把起点换成玩家自己，
        // 语义是「她相对你跑到哪儿去了」—— 圆心不受影响，仍然钉在原地。
        Vec3 lineStart = maid.isHomeModeEnable() ? center
                : minecraft.player.position().add(0.0D, 1.0D, 0.0D);
        RenderHelper.renderLine(lines, lineStart, maidPos, 1.0F, 0.2F, 0.2F);

        // 三条同心圆环：工作红 / 休息绿 / 睡觉蓝（半径与上游逐条对应）
        RenderHelper.renderCylinder(lines, center,
                MaidConfig.workRange() + 0.1D, CIRCLE_PRECISION, 1.0F, 0.0F, 0.0F);
        RenderHelper.renderCylinder(lines, center,
                MaidConfig.idleRange(), CIRCLE_PRECISION, 0.0F, 1.0F, 0.0F);
        RenderHelper.renderCylinder(lines, center,
                MaidConfig.sleepRange() - 0.1D, CIRCLE_PRECISION, 0.0F, 0.0F, 1.0F);
    }

    /**
     * 三行标签。
     *
     * <p>上游在「三个点重合」时逐级把后一行抬高 1 格（{@code idle == work → +1}，
     * {@code sleep == idle → +2}），本移植版三个点共用同一中心，因此直接按 0 / 1 / 2 抬。
     * 颜色与 {@code ▼} 的 {@code yOffset}（±5）都照抄上游。</p>
     */
    private static void renderAreaTexts(Minecraft minecraft, EntityMaid maid) {
        if (!hasArea(maid)) {
            return;
        }
        Vec3 center = areaCenter(maid);
        double baseY = center.y + 2.0D;
        drawLabel(center.x, baseY, center.z,
                "message.touhou_little_maid.kappa_compass.work_area", 0xFF1111, 0.0D);
        drawLabel(center.x, baseY, center.z,
                "message.touhou_little_maid.kappa_compass.idle_area", 0x11FF11, 1.0D);
        drawLabel(center.x, baseY, center.z,
                "message.touhou_little_maid.kappa_compass.sleep_area", 0x1111FF, 2.0D);
    }

    private static void drawLabel(double x, double y, double z, String langKey, int color, double lift) {
        double textY = y + lift;
        RenderHelper.renderFloatingText(I18n.get(langKey), x, textY, z,
                color, TEXT_SCALE, true, -5.0F, false);
        RenderHelper.renderFloatingText("\u25BC", x, textY, z,
                color, TEXT_SCALE, true, 5.0F, false);
    }
}


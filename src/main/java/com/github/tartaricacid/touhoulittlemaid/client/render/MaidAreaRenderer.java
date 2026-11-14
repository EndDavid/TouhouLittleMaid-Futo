package com.github.tartaricacid.touhoulittlemaid.client.render;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.github.tartaricacid.touhoulittlemaid.network.MaidAreaPacket;
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

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 手持原版指南针右击女仆后，在世界里显示她的工作 / 娱乐 / 睡觉三个区域。
 * 移植自上游 {@code client/event/MaidAreaRenderEvent}。
 *
 * <p>结构照抄上游（Forge 1.18.2），只把「Forge 的 {@code RenderLevelLastEvent}」换成 Fabric
 * 的 {@link WorldRenderEvents#LAST}（它注入在 {@code WorldRenderer#renderChunkDebugInfo}，
 * 与上游是同一渲染时刻）。</p>
 *
 * <h2>数据来自包里，不是实体同步数据</h2>
 * <p>三个坐标由服务端在 {@code EntityMaid#compassOrNameTag} 里通过
 * {@link MaidAreaPacket} 发过来，本类只缓存 600 tick（30 秒，与上游
 * {@code expireAfterWrite(30, SECONDS)} 一致）。这样每只女仆不必常驻同步三个
 * {@code BlockPos}，而这份数据一辈子也改不了几次。</p>
 *
 * <h2>三个圈各有各的圆心</h2>
 * <p>这是本文件最容易被写错的地方：早期版本用同一个「居家位置」画三个同心圆，
 * 于是罗盘设下的三个点里只有一个能看出来。上游的做法是三个圆心各自独立，
 * <b>两点不重合时还会画一条白色连线</b>，让玩家一眼看出「工作区和睡觉区隔着半条街」。</p>
 *
 * <h2>坐标约定</h2>
 * <p>整块绘制跑在 {@link RenderHelper#beginCameraSpace()} 里：modelview 被重置成
 * 「相机旋转 × 平移(-相机位置)」，于是<b>顶点一律用世界绝对坐标、不乘任何矩阵</b>，
 * 并且<b>当场 flush</b>。这与 vanilla 自己的区块调试框、方块描边是同一套写法
 * （反编译 {@code WorldRenderer#renderChunkDebugInfo} 核对过）。</p>
 *
 * <p>上游在每个点上都先 {@code poseStack.translate(0, 1, 0)}：也就是说区域整体画在
 * 「锚点方块上表面之上 1 格」。本移植版没有用矩阵，而是把 <b>+1 直接加在世界 Y 上</b>
 * （见 {@link #topOf(BlockPos)}），效果相同且不依赖进入本方法时 modelview 的残留状态。</p>
 */
@Environment(EnvType.CLIENT)
public final class MaidAreaRenderer {
    /**
     * 显示时长：600 tick = 30 秒 @ 20 tps，与上游 {@code expireAfterWrite(30, SECONDS)} 一致。
     */
    private static final long SHOW_DURATION_TICKS = 600L;

    /**
     * 圆环的多边形边数，与上游一致。16 边在方块尺度上已经看不出棱角。
     */
    private static final int CIRCLE_PRECISION = 16;

    private static final float TEXT_SCALE = 0.15F;
    private static final float LINE_WIDTH = 2.0F;

    /**
     * 实体 id → 一份带到期时刻的作息坐标。
     */
    private static final Map<Integer, Shown> SHOWN = new HashMap<>();

    private MaidAreaRenderer() {
    }

    public static void init() {
        WorldRenderEvents.LAST.register(MaidAreaRenderer::render);
    }

    /**
     * 客户端收包后调用：让这只女仆的区域显示 30 秒（重复右击会顺延并刷新坐标）。
     */
    public static void show(MaidAreaPacket.Area area) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        SHOWN.put(area.getMaidId(),
                new Shown(area.getSchedulePos(), minecraft.level.getGameTime() + SHOW_DURATION_TICKS));
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
        Iterator<Map.Entry<Integer, Shown>> iterator = SHOWN.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt <= now) {
                iterator.remove();
            }
        }

        // 先按 id 找到还活着的实体，再决定要不要画：缓存里可能留着已经走远的 id
        List<Map.Entry<EntityMaid, SchedulePos>> targets = new ArrayList<>();
        String playerDimension = minecraft.player.level.dimension().location().toString();
        for (Map.Entry<Integer, Shown> entry : SHOWN.entrySet()) {
            if (!(minecraft.level.getEntity(entry.getKey()) instanceof EntityMaid maid) || !maid.isAlive()) {
                continue;
            }
            SchedulePos pos = entry.getValue().pos;
            // 跨维度时不画：坐标只在它自己的维度里有意义，硬画会在错误的位置立一堆圆圈
            if (!pos.getDimension().toString().equals(playerDimension)) {
                continue;
            }
            targets.add(new AbstractMap.SimpleImmutableEntry<>(maid, pos));
        }
        if (targets.isEmpty()) {
            return;
        }

        PoseStack cameraSpace = RenderHelper.beginCameraSpace();
        try {
            // 线段（红线 + 三色圆环 + 白连线）：同一批，写完立刻刷出
            BufferBuilder lines = RenderHelper.beginLines(LINE_WIDTH);
            for (Map.Entry<EntityMaid, SchedulePos> target : targets) {
                renderAreaLines(lines, minecraft, target.getKey(), target.getValue());
            }
            RenderHelper.endLines();

            // 实心高亮框：单独一批（不同的着色器与顶点格式）
            for (Map.Entry<EntityMaid, SchedulePos> target : targets) {
                AABB box = target.getKey().getBoundingBox();
                RenderHelper.addChainedFilledBoxVertices(box, 0.8F, 0.8F, 0.2F, 0.75F);
            }

            // 文字：immediate 源，drawInBatch 之后 endBatch 立刻绘制
            for (Map.Entry<EntityMaid, SchedulePos> target : targets) {
                renderAreaTexts(target.getValue());
            }
        } finally {
            RenderHelper.endCameraSpace(cameraSpace);
        }
    }

    /**
     * 方块「上表面之上 1 格」的中心点 —— 对应上游每个点开头那句
     * {@code poseStack.translate(0, 1, 0)}。
     */
    @Nullable
    private static Vec3 topOf(@Nullable BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D);
    }

    private static void renderAreaLines(BufferBuilder lines, Minecraft minecraft,
                                        EntityMaid maid, SchedulePos pos) {
        // 红线：把「活动范围中心」和「女仆现在的位置」连起来。
        // 居家模式下起点是 restrictCenter（也就是当前活动对应的那个点）；
        // 关掉居家模式时上游把起点换成玩家自己，语义是「她相对你跑到哪儿去了」。
        Vec3 lineStart = minecraft.player.position().add(0.0D, 1.0D, 0.0D);
        if (maid.isHomeModeEnable()) {
            BlockPos center = maid.getRestrictCenter();
            lineStart = new Vec3(center.getX() + 0.5D, center.getY() + 1.5D, center.getZ() + 0.5D);
        }
        Vec3 maidPos = maid.position().add(0.0D, 1.0D, 0.0D);
        RenderHelper.renderLine(lines, lineStart, maidPos, 1.0F, 0.2F, 0.2F);

        Vec3 work = topOf(pos.getWorkPos());
        Vec3 idle = topOf(pos.getIdlePos());
        Vec3 sleep = topOf(pos.getSleepPos());

        // 工作红 / 娱乐绿 / 睡觉蓝，半径与上游逐条对应（+0.1 / 原值 / -0.1）
        if (work != null) {
            RenderHelper.renderCylinder(lines, work,
                    MaidConfig.workRange() + 0.1D, CIRCLE_PRECISION, 1.0F, 0.0F, 0.0F);
        }
        if (idle != null) {
            RenderHelper.renderCylinder(lines, idle,
                    MaidConfig.idleRange(), CIRCLE_PRECISION, 0.0F, 1.0F, 0.0F);
            // 两点不重合时用白线连起来；重合就不画（否则是一条零长度的线，白白多两个顶点）
            if (work != null && !pos.getIdlePos().equals(pos.getWorkPos())) {
                RenderHelper.renderLine(lines, idle, work, 1.0F, 1.0F, 1.0F);
            }
        }
        if (sleep != null) {
            RenderHelper.renderCylinder(lines, sleep,
                    MaidConfig.sleepRange() - 0.1D, CIRCLE_PRECISION, 0.0F, 0.0F, 1.0F);
            if (idle != null && work != null && !pos.getSleepPos().equals(pos.getIdlePos())) {
                RenderHelper.renderLine(lines, sleep, idle, 1.0F, 1.0F, 1.0F);
                RenderHelper.renderLine(lines, sleep, work, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    /**
     * 三行标签。
     *
     * <p>与上游一致：三个点重合时把后一行抬高（{@code idle == work → +1}、
     * {@code sleep == idle → +2}），否则三行字会精确重叠成一行。
     * 颜色与 {@code ▼} 的 {@code yOffset}（∓5）都照抄上游。</p>
     */
    private static void renderAreaTexts(SchedulePos pos) {
        BlockPos work = pos.getWorkPos();
        BlockPos idle = pos.getIdlePos();
        BlockPos sleep = pos.getSleepPos();

        if (work != null) {
            // 上游：textPos = workPos + (0.5, 2, 0.5)，再加上那句 translate(0,1,0)
            drawLabel(work.getX() + 0.5D, work.getY() + 3.0D, work.getZ() + 0.5D,
                    "message.touhou_little_maid.kappa_compass.work_area", 0xFF1111);
        }
        if (idle != null) {
            double lift = idle.equals(work) ? 1.0D : 0.0D;
            drawLabel(idle.getX() + 0.5D, idle.getY() + 3.0D + lift, idle.getZ() + 0.5D,
                    "message.touhou_little_maid.kappa_compass.idle_area", 0x11FF11);
        }
        if (sleep != null) {
            double lift = sleep.equals(idle) ? 2.0D : 0.0D;
            drawLabel(sleep.getX() + 0.5D, sleep.getY() + 3.0D + lift, sleep.getZ() + 0.5D,
                    "message.touhou_little_maid.kappa_compass.sleep_area", 0x1111FF);
        }
    }

    private static void drawLabel(double x, double y, double z, String langKey, int color) {
        RenderHelper.renderFloatingText(I18n.get(langKey), x, y, z,
                color, TEXT_SCALE, true, -5.0F, false);
        RenderHelper.renderFloatingText("\u25BC", x, y, z,
                color, TEXT_SCALE, true, 5.0F, false);
    }

    /**
     * 一条缓存：作息坐标 + 到期时刻。
     */
    private static final class Shown {
        private final SchedulePos pos;
        private final long expiresAt;

        private Shown(SchedulePos pos, long expiresAt) {
            this.pos = pos;
            this.expiresAt = expiresAt;
        }
    }
}


package com.github.tartaricacid.touhoulittlemaid.client.render;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemKappaCompass;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

/**
 * <b>手里拿着河童的罗盘时，预览它自己记下的三个区域。</b>
 * 移植自上游 1.20.1 的 {@code client/event/CompassRenderEvent}。
 *
 * <h2>为什么需要第二个渲染器</h2>
 * <p>{@link MaidAreaRenderer} 画的是<b>某只女仆身上</b>的作息坐标，入口是「拿原版指南针
 * 右击她」；而拿罗盘<b>设点的时候</b>，坐标还在罗盘自己的 NBT 里，没有任何女仆牵涉其中，
 * 于是什么都看不到 —— 玩家记下了工作点，却完全不知道那个圈有多大、会不会把床圈进去，
 * 必须先跑过去右击一次女仆才知道。</p>
 *
 * <p>上游 1.18.2 其实没有这个功能（它的 {@code CompassRenderEvent} 是 1.20.1 才加的），
 * 所以这里按 1.20.1 的行为补齐，半径、颜色、{@code ▼} 的偏移、白线规则都逐条对齐。</p>
 *
 * <h2>为什么不需要网络包</h2>
 * <p>罗盘的数据在<b>手里那件物品的 NBT</b> 里，而手持物品栈本来就是同步给客户端的，
 * 客户端直接读即可。这与 {@link MaidAreaRenderer} 完全不同 ——
 * 三个作息坐标在服务端的女仆实体上，客户端拿不到，所以那边必须走 {@code MaidAreaPacket}。</p>
 *
 * <p><b>但「写进 NBT」不等于「客户端马上看见」：</b>服务端改完物品 NBT 之后要等一次
 * 背包同步，客户端手里那份才更新。所以 {@code ItemKappaCompass#useOn} 在写完坐标后
 * 主动调了一次 {@code inventoryMenu.broadcastChanges()}（与仆从铃同一处理），
 * 否则点完方块要愣几秒才冒出圈来。</p>
 *
 * <h2>{@code getPoint} 的回退语义在这里会「放大」</h2>
 * <p>{@code ItemKappaCompass#getPoint} 在目标活动没有记录时会依次回退到娱乐点、工作点，
 * 所以<b>只记了一个点时三个圈完全重合</b>。这不是 bug：上游就是这样，
 * 此时三行标签靠 {@link MaidAreaRenderer#renderTripletTexts} 的抬升规则错开显示。</p>
 */
@Environment(EnvType.CLIENT)
public final class CompassAreaRenderer {
    private static final float LINE_WIDTH = 2.0F;

    private CompassAreaRenderer() {
    }

    public static void init() {
        WorldRenderEvents.LAST.register(CompassAreaRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!minecraft.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }

        // 主手优先、副手其次：两处都是「手持即预览」，与上游一致。
        // 副手也要查，是因为很多人习惯把工具挂在副手而主手拿食物 / 方块。
        ItemStack compass = minecraft.player.getMainHandItem();
        if (!compass.is(InitItems.KAPPA_COMPASS)) {
            compass = minecraft.player.getOffhandItem();
            if (!compass.is(InitItems.KAPPA_COMPASS)) {
                return;
            }
        }
        // 一个点都没记过的罗盘没有任何东西可预览
        if (!ItemKappaCompass.hasKappaCompassData(compass)) {
            return;
        }
        // 跨维度不画：坐标只在它自己的维度里有意义，硬画会在完全无关的位置立一圈
        ResourceLocation dimension = ItemKappaCompass.getDimension(compass);
        if (dimension != null && !minecraft.player.level.dimension().location().equals(dimension)) {
            return;
        }

        BlockPos workPos = ItemKappaCompass.getPoint(Activity.WORK, compass);
        BlockPos idlePos = ItemKappaCompass.getPoint(Activity.IDLE, compass);
        BlockPos sleepPos = ItemKappaCompass.getPoint(Activity.REST, compass);

        PoseStack cameraSpace = RenderHelper.beginCameraSpace();
        try {
            // 与 MaidAreaRenderer 共用同一套画法：半径 +0.1 / 原值 / -0.1，
            // 白线只在两点不重合时画
            BufferBuilder lines = RenderHelper.beginLines(LINE_WIDTH);
            MaidAreaRenderer.renderTripletLines(lines, workPos, idlePos, sleepPos);
            RenderHelper.endLines();

            MaidAreaRenderer.renderTripletTexts(workPos, idlePos, sleepPos);
        } finally {
            RenderHelper.endCameraSpace(cameraSpace);
        }
    }
}


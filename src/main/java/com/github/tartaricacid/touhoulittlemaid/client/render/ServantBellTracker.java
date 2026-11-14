package com.github.tartaricacid.touhoulittlemaid.client.render;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemServantBell;
import com.github.tartaricacid.touhoulittlemaid.item.ItemFoxScroll;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 主手拿着「指过路的物品」时，在世界上画出「距离 + 红色倒三角」的浮标
 * （上游 {@code client/event/ScrollRenderEvent}）。
 *
 * <p>三种物品共用这一套浮标：</p>
 * <ul>
 *     <li>已绑定的<b>仆从铃</b> —— 她所在的区块没加载时，NBT 里存着区块坐标；</li>
 *     <li><b>红 / 白狐狸卷轴</b> —— 玩家在卷轴界面里点了「追踪」的那条记录。</li>
 * </ul>
 *
 * <p>没有这个浮标，「她在未加载区块」这件事就只剩一句「请跟着位置提示去找」——
 * 而<b>根本没有提示可跟</b>。所以它不是一个可有可无的装饰，
 * 是这些物品那一半唯一有用的输出。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>上游挂 Forge 的 {@code RenderLevelStageEvent.Stage#AFTER_PARTICLES}；
 *         这里用 Fabric 的 {@link WorldRenderEvents#LAST}，与
 *         {@link MaidAreaRenderer} 同一个渲染时刻（两者共用
 *         {@link RenderHelper#beginCameraSpace()} 的坐标系约定：
 *         <b>顶点一律用世界绝对坐标、不乘任何矩阵</b>）。</li>
 *     <li>上游 {@code renderFloatingText} 有一个接收 {@code PoseStack} 的重载；
 *         本移植版的 {@code renderFloatingText} 自己建 PoseStack，
 *         前提是<b>已处在 {@code beginCameraSpace()} 之内</b>，
 *         所以整段要包在 try/finally 里。</li>
 *     <li>{@code RenderHelper.renderFloatingText} 的 {@code scale} 语义与上游一致
 *         （越大字越大），{@code yOffset} 同理。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class ServantBellTracker {
    /**
     * 距离小于这个值就不画。上游是 5 格：你已经站在她旁边了，浮标只会挡住视线。
     */
    private static final double MIN_DISTANCE = 5.0D;

    /**
     * 超出视距时把浮标压到视距边缘，让玩家知道「朝那边走」。
     * 这个值来自上游 {@code 0.02f * ((viewDistance + 4.0f) / 3.0f)} 的比例基准。
     */
    private static final float SCALE_BASE = 0.02F;

    private static final int TEXT_COLOR = 0xFF8800;
    private static final int ARROW_COLOR = 0xFF0000;

    private ServantBellTracker() {
    }

    public static void init() {
        WorldRenderEvents.LAST.register(ServantBellTracker::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!minecraft.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }
        Pair<String, BlockPos> info = getInfo(player, player.getMainHandItem());
        if (info == null) {
            return;
        }
        String dimension = info.getFirst();
        BlockPos pos = info.getSecond();
        // 跨维度时她不在这张地图上，画出来只会在错误的位置立一个箭头
        if (!dimension.equals(player.level.dimension().location().toString())) {
            return;
        }

        Vec3 trackVec = new Vec3(pos.getX(), pos.getY(), pos.getZ());
        Vec3 playerVec = player.position();
        double actualDistance = playerVec.distanceTo(trackVec);
        if (actualDistance < MIN_DISTANCE) {
            return;
        }

        double viewDistance = actualDistance;
        double maxRenderDistance = minecraft.options.renderDistance * 16.0D;
        if (actualDistance > maxRenderDistance) {
            Vec3 delta = trackVec.subtract(playerVec).normalize();
            trackVec = playerVec.add(delta.x * maxRenderDistance,
                    delta.y * maxRenderDistance, delta.z * maxRenderDistance);
            viewDistance = maxRenderDistance;
        }

        float scale = SCALE_BASE * (((float) viewDistance + 4.0F) / 3.0F);
        var cameraSpace = RenderHelper.beginCameraSpace();
        try {
            // 数字显示的是**真实**距离（actualDistance），不是压到视距后的距离
            RenderHelper.renderFloatingText(Math.round(actualDistance) + " m",
                    trackVec.x, trackVec.y, trackVec.z, TEXT_COLOR, scale, true, -17.0F, true);
            RenderHelper.renderFloatingText("\u25BC",
                    trackVec.x, trackVec.y, trackVec.z, ARROW_COLOR, scale * 1.2F, true, -5.0F, true);
        } finally {
            RenderHelper.endCameraSpace(cameraSpace);
        }
    }

    /**
     * 主手物品 → 追踪信息。三种物品共用这一套浮标（上游也是把它们并排写在一个方法里）：
     * 已绑定的仆从铃、红狐狸卷轴、白狐狸卷轴。
     */
    private static Pair<String, BlockPos> getInfo(Player player, ItemStack stack) {
        if (stack.is(InitItems.SERVANT_BELL)) {
            return ItemServantBell.getMaidShow(stack);
        }
        if (stack.getItem() instanceof ItemFoxScroll) {
            return ItemFoxScroll.getTrackInfo(stack);
        }
        return null;
    }
}

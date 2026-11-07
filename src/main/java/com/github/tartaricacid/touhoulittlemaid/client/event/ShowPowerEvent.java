package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.capability.IPower;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 手持御币时在屏幕左上角显示当前灵力，上游 {@code client/event/ShowPowerEvent} 的移植。
 *
 * <p>上游挂的是 Forge 的 {@code RenderGameOverlayEvent}（{@code ElementType.LAYER} 阶段），
 * Fabric 侧等价物是 {@link HudRenderCallback} —— 两者都在原版 HUD 之上、
 * 且已经进入 GUI 正交投影，所以坐标可以直接写屏幕像素。</p>
 *
 * <p><b>为什么只在手持御币时显示：</b>上游就是这个行为（wiki 也这么写）。
 * 灵力是个长期资源，常驻显示会挡视野；做成「拿御币才看得到」既是上游的手感，
 * 也顺带给了玩家一个自查手段。</p>
 *
 * <p>数值来自 {@link IPower}：客户端那份由 {@code PowerSyncPacket} 写，
 * 与服务端同源，不是自己算的。</p>
 */
public final class ShowPowerEvent {
    /**
     * 主手没有御币时什么都不画，这里缓存默认实例避免每帧新建 ItemStack。
     */
    private static ItemStack icon;

    private ShowPowerEvent() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register(ShowPowerEvent::onRenderOverlay);
    }

    private static void onRenderOverlay(PoseStack poseStack, float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        if (!ItemHakureiGohei.isGohei(player.getMainHandItem())) {
            return;
        }
        if (icon == null) {
            icon = new ItemStack(InitItems.POWER_POINT);
        }
        // 与上游一致的摆放：图标 (5,5)，文字 (20,10)
        minecraft.getItemRenderer().renderGuiItem(icon, 5, 5);
        String text = String.format("%s×%.2f", ChatFormatting.BOLD, IPower.getPower(player).get());
        minecraft.font.drawShadow(poseStack, text, 20, 10, 0xFFFFFF);
    }
}


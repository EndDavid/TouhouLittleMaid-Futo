package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIDataSerializable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 「打开这只女仆的 AI 设置界面」的 S2C 包，移植自上游
 * {@code network/message/OpenMaidAIDataScreenMessage}。
 *
 * <h2>与上游的差异</h2>
 * <p>上游这里打开的是 <b>Cloth Config</b> 的配置界面
 * （{@code ClothConfigCompat.openPartAiSettingScreen}，装着 {@code PartAIIntegration}），
 * 只有在没装 Cloth 时才退回 {@code ClothConfigScreen.open()}。</p>
 * <p>本移植版<b>也走同一条路</b>：装了 Cloth 就开 {@code PartAIIntegration}。
 * 额外的兜底是「没装 Cloth 时开手写的 {@code MaidAISettingScreen}」——
 * 上游那一版会直接弹出「请安装 Cloth」，等于功能缺失；这里保证不装 Cloth 也能改设置。</p>
 *
 * <p>只在客户端<b>当前开着聊天界面</b>时才切换到设置界面（与上游一致）：
 * 玩家可能已经按 ESC 关掉界面了，这时再弹出设置界面会很突兀。</p>
 */
public final class OpenMaidAIDataScreenPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "open_maid_ai_data_screen");

    private OpenMaidAIDataScreenPacket() {
    }

    public static void send(ServerPlayer player, int entityId, MaidAIDataSerializable data) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        data.encode(buf);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    int entityId = buf.readInt();
                    MaidAIDataSerializable data = new MaidAIDataSerializable();
                    data.decode(buf);
                    client.execute(() -> handle(entityId, data));
                });
    }

    @Environment(EnvType.CLIENT)
    private static void handle(int entityId, MaidAIDataSerializable data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        // 这份数据有两个用途：界面底部摘要要用它，设置界面也要用它。
        // 先无条件缓存，再看意图位决定要不要切界面。
        com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen.CLIENT_DATA = data;
        boolean wantSetting =
                com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen.PENDING_SETTING_SCREEN;
        com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen.PENDING_SETTING_SCREEN = false;
        if (!wantSetting) {
            if (minecraft.screen instanceof
                    com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen chat) {
                chat.refreshData();
            }
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
            // 装了 Cloth Config 就走上游那套 PartAIIntegration —— 它的下拉框能直接列出
            // available_sites.yml 里的站点与模型，而手写的 MaidAISettingScreen 只有固定的
            // 几个扁平按钮。没装 Cloth 才退回手写界面（保证功能不缺失）。
            if (com.github.tartaricacid.touhoulittlemaid.compat.cloth.ClothConfigCompat.isAvailable()) {
                com.github.tartaricacid.touhoulittlemaid.compat.cloth.ClothConfigCompat
                        .openPartAiSettingScreen(maid);
            } else {
                minecraft.setScreen(new com.github.tartaricacid.touhoulittlemaid.client.screen.ai
                        .MaidAISettingScreen(maid, data));
            }
        } else {
            TouhouLittleMaid.LOGGER.debug("[Futo] AI setting target {} is not a loaded maid", entityId);
        }
    }
}

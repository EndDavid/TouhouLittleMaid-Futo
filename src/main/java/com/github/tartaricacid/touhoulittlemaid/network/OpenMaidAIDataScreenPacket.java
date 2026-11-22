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
 * <h2>与上游的差异（框架差异，不是设计选择）</h2>
 * <p>上游这里打开的是 <b>Cloth Config</b> 的配置界面
 * （{@code ClothConfigCompat.openPartAiSettingScreen}，装着 {@code PartAIIntegration}），
 * 只有在没装 Cloth 时才退回 {@code ClothConfigScreen.open()}。</p>
 * <p>本移植版没有移植那套 Cloth 集成（它要用 {@code ConfigBuilder} 的 Forge 风格条目
 * 逐项绑定 setter，且会额外引入一整套「全局配置界面」），改成手写的
 * {@code MaidAISettingScreen}：同样的 6 个字段、同样的保存包，
 * 只是界面长得朴素一些。这样不依赖 Cloth 是否加载，行为在专用服务器上也一致。</p>
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
        // 与上游一致：只有「正开着 AI 聊天界面」时才切过去
        if (!(minecraft.screen instanceof com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen)) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
            minecraft.setScreen(
                    new com.github.tartaricacid.touhoulittlemaid.client.screen.ai.MaidAISettingScreen(maid, data));
        } else {
            TouhouLittleMaid.LOGGER.debug("[Futo] AI setting target {} is not a loaded maid", entityId);
        }
    }
}


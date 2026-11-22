package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
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
 * 「打开坐垫换肤界面」的 S2C 包，移植自上游 {@code OpenChairGuiMessage}。
 *
 * <p>为什么不用菜单（{@code MenuType}）：这个界面<b>不是容器</b> —— 里面没有物品槽，
 * 只有一份模型网格。上游同样是纯 {@code Screen}，用自定义包把实体 id 发下去，
 * 客户端自己找到坐垫再开界面。</p>
 *
 * <p>上游是在服务端 {@code EntityChair#interact} 里发这个包的（而不是客户端直接开），
 * 这样「是不是真的蹲着右键了这个坐垫」只有服务端说了算，客户端伪造不了。</p>
 */
public final class OpenChairGuiPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "open_chair_gui");

    private OpenChairGuiPacket() {
    }

    /**
     * 服务端调用：让这名玩家打开指定坐垫的换肤界面。
     */
    public static void send(ServerPlayer player, int entityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    /**
     * 客户端注册接收器（在客户端入口调用）。
     *
     * <p>整段逻辑都在客户端，所以这里直接引用 {@code Minecraft} 并标 {@link Environment}；
     * 只要不在主入口（服务端也会加载的那个类）里碰它就行。</p>
     */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，实体 id 必须在切回主线程之前读完
                    int entityId = buf.readInt();
                    client.execute(() -> handleOpenGui(entityId));
                });
    }

    @Environment(EnvType.CLIENT)
    private static void handleOpenGui(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof EntityChair chair) {
            minecraft.setScreen(
                    new com.github.tartaricacid.touhoulittlemaid.client.screen.ChairModelGui(chair));
        } else {
            // 实体已被拆除 / 不在客户端可见范围：界面没得开，但也没必要刷 ERROR
            TouhouLittleMaid.LOGGER.debug("[Futo] Chair GUI target {} is not a loaded chair", entityId);
        }
    }
}

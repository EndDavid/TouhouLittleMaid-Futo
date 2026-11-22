package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.ChairConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 坐垫换肤的 C2S 包，移植自上游 {@code network/message/ChairModelMessage}。
 *
 * <p>载荷四项，与上游逐字对应：{@code entityId} + {@code modelId} +
 * {@code mountedYOffset} + {@code tameableCanRide} + {@code noGravity}。
 * 服务端收到后一次性写进坐垫的四个同步字段。</p>
 *
 * <h2>为什么必须走网络包</h2>
 * <p>这四项都是 {@code SynchedEntityData}，客户端改了自己那份既不会被同步、也到不了服务端；
 * 而且「能不能换模型」取决于服务端配置 {@code ChairConfig.CHAIR_CHANGE_MODEL}，
 * 纯客户端判定可以被伪造。</p>
 *
 * <h2>服务端复核（对应上游字节码的逐行行为）</h2>
 * <ol>
 *     <li>{@code allowed = CHAIR_CHANGE_MODEL || player.isCreative()}；</li>
 *     <li>不允许时给玩家发一条 {@code message.touhou_little_maid.change_model.disabled}，其它什么都不做；</li>
 *     <li>允许时写入四项，并在 {@code tameableCanRide} 变成 {@code false} 时把乘客请下车
 *         （否则「不可骑」的坐垫上还坐着人，语义矛盾）；</li>
 *     <li>打一次女仆进度判据 {@code change_chair_model}（对应上游「我刚买的家具啊」）。</li>
 * </ol>
 */
public final class ChairModelPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "chair_model");

    private ChairModelPacket() {
    }

    /**
     * 客户端调用：请求把这名玩家正在看的坐垫换成某个模型。
     */
    @Environment(EnvType.CLIENT)
    public static void send(int entityId, ResourceLocation modelId, float mountedYOffset,
                            boolean tameableCanRide, boolean noGravity) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeResourceLocation(modelId);
        buf.writeFloat(mountedYOffset);
        buf.writeBoolean(tameableCanRide);
        buf.writeBoolean(noGravity);
        ClientPlayNetworking.send(CHANNEL, buf);
    }

    /**
     * 服务端注册接收器（在主入口调用）。
     */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，四个字段必须在 execute 之前读完
                    int entityId = buf.readInt();
                    ResourceLocation modelId = buf.readResourceLocation();
                    float mountedYOffset = buf.readFloat();
                    boolean tameableCanRide = buf.readBoolean();
                    boolean noGravity = buf.readBoolean();
                    server.execute(() -> handle(player, entityId, modelId,
                            mountedYOffset, tameableCanRide, noGravity));
                });
    }

    private static void handle(ServerPlayer player, int entityId, ResourceLocation modelId,
                              float mountedYOffset, boolean tameableCanRide, boolean noGravity) {
        Entity entity = player.level.getEntity(entityId);
        if (!(entity instanceof EntityChair chair)) {
            // 实体不在（区块卸载 / 已被拆除）或 id 根本不对：静默丢弃，不刷屏
            return;
        }
        // 配置项理论上一定已初始化；这里按「读不到就当作关闭」处理，宁可少改一次也不 NPE。
        boolean configured = ChairConfig.CHAIR_CHANGE_MODEL != null
                && Boolean.TRUE.equals(ChairConfig.CHAIR_CHANGE_MODEL.get());
        if (!configured && !player.isCreative()) {
            if (player.isAlive()) {
                player.displayClientMessage(
                        new TranslatableComponent("message.touhou_little_maid.change_model.disabled"), false);
            }
            return;
        }
        chair.setModelId(modelId.toString());
        // setMountedHeight 内部会夹到 [-0.5, 2.5]，所以这里不必再校验
        chair.setMountedHeight(mountedYOffset);
        chair.setTameableCanRide(tameableCanRide);
        chair.setNoGravity(noGravity);
        // 变成「不可骑」时，把已经坐在上面的人请下去
        if (!tameableCanRide && !chair.getPassengers().isEmpty()) {
            chair.ejectPassengers();
        }
        if (InitTrigger.MAID_EVENT != null) {
            InitTrigger.MAID_EVENT.trigger(player, TriggerType.CHANGE_CHAIR_MODEL);
        }
    }
}


package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「从扫帚上下来」的 C2S 包，移植自上游 1.20.1 的 {@code DismountMessage}。
 *
 * <h2>为什么下扫帚不自己调 {@code stopRiding()}</h2>
 * <p>玩家能不能下车是<b>服务端</b>说了算的东西（乘客表、位置修正、区块归属都在那边），
 * 客户端单方面 {@code stopRiding} 只会让本地模型掉下去、下一 tick 被服务端拉回来。
 * 所以和上游一样：客户端按键 → 发包 → 服务端 {@code stopRiding()}。</p>
 *
 * <h2>为什么不像原版那样「按 Shift 下车」</h2>
 * <p>因为在扫帚上 Shift 是<b>下降</b>（{@code PlayerBroomControl#travel} 里
 * {@code keySneak → vertical = -0.2f}），这是上游 1.20.1 的设计，
 * 所以下扫帚必须另给一个键。上游给它的是默认 C、可在「控制」里改绑定。</p>
 *
 * <p><b>光有 C 键还不够：</b>原版 {@code Player#wantsToStopRiding()} 就是
 * {@code isShiftKeyDown()}，{@code Player#rideTick()} 拿着它每 tick 做「潜行即下车」的判定
 * —— 于是按住 Shift 的人会先被原版从扫帚上踢下来，下降一次都用不上。
 * 那条判定由 {@code mixin/PlayerBroomMixin} 在骑扫帚时屏蔽掉，
 * 两个改动是一组，缺哪个都不成立。</p>
 *
 * <p>载荷只有一个 byte：上游用 int 是因为它的 {@code DismountMessage} 还想复用来做别的事，
 * 结果到最后只用到 {@link #DISMOUNT_BROOM} 一个动作。</p>
 */
public final class DismountBroomPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "dismount_broom");

    /**
     * 动作 id，与上游 {@code DismountMessage.DISMOUNT_BROOM} 同值。
     */
    public static final byte DISMOUNT_BROOM = 1;

    private DismountBroomPacket() {
    }

    /**
     * 客户端调用：请求下车，并带上自己骑的是什么。
     *
     * <p>带实体 id 而不是「无参请求」是有意的：服务端可以直接复核
     * 「你上报的这个实体是不是就是你此刻骑的那个、而且它真的是扫帚」，
     * 不用再靠 {@code getVehicle()} 猜。伪造这个包最多只能让自己下车，无害。</p>
     */
    public static void send(int broomEntityId) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeByte(DISMOUNT_BROOM);
        buf.writeVarInt(broomEntityId);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(CHANNEL, buf);
    }

    /**
     * 服务端注册接收器（在主入口调用）。
     */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，字段必须在 execute 之前读完
                    byte action = buf.readByte();
                    int broomEntityId = buf.readVarInt();
                    if (action != DISMOUNT_BROOM) {
                        return;
                    }
                    server.execute(() -> handle(player, broomEntityId));
                });
    }

    private static void handle(ServerPlayer player, int broomEntityId) {
        // 正在骑的必须就是上报的那个实体，而且必须是扫帚。
        // 上游只查 `sender.getVehicle() instanceof EntityBroom`；
        // 这里多查一个 id 相等，防止「骑 A 报 B」让服务端对别的实体做处理。
        if (!(player.getVehicle() instanceof EntityBroom broom) || broom.getId() != broomEntityId) {
            return;
        }
        player.stopRiding();
    }
}

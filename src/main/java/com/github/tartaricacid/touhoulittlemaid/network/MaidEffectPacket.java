package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidEffectData;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Collection;

/**
 * 女仆界面左侧「状态效果」列表的同步包，合并自上游的
 * {@code RequestEffectMessage}（C2S）与 {@code SendEffectMessage}（S2C）。
 *
 * <p><b>为什么这个功能必须走网络包：</b>原版只把「玩家自己」的状态效果同步进
 * {@code ClientboundUpdateMobEffectPacket}，而且接收方固定是 {@code LocalPlayer}
 * （{@code ClientPacketListener#handleUpdateMobEffect} 写死 {@code this.minecraft.player}）。
 * 别的实体的效果<b>从来不会</b>下发到客户端 —— 客户端上 {@code maid.getActiveEffects()}
 * 永远是空的（除非她恰好在你身边、且效果是她自己刷上去的，那种也只在服务端）。
 * 所以想让 GUI 显示，只能自己问服务端要。</p>
 *
 * <p><b>为什么是「拉」而不是「推」：</b>效果会自然到期、会被药水 / 庭灯随时改写，
 * 如果只在变化时推送，就得在服务端每个 tick 比对一次全体女仆的效果集合 ——
 * 太贵。上游的做法是：界面打开期间<b>每 20 tick</b> 请求一次，由服务端现查现回。
 * 代价是界面关掉后列表不再更新，但那时也没人看。</p>
 *
 * <p><b>服务端复核：</b>这个请求是玩家可控的，必须确认「实体 id 确实是一只女仆」
 * 且<b>归请求者所有</b>（{@code isOwnedByPlayer}）—— 否则任何人都能拉取别人的女仆
 * 身上挂了什么效果。上游同样只做这两项检查。</p>
 */
public final class MaidEffectPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "maid_effect");

    /**
     * C2S：请求某只女仆的当前效果，载荷只有一个实体 id。
     */
    public static final byte REQUEST = 0;
    /**
     * S2C：回传效果列表，载荷是实体 id + 条数 + 每条的四元组。
     */
    public static final byte SYNC = 1;

    /**
     * 单次最多接受的效果条数。
     *
     * <p>女仆身上的效果数量上限由她自己 {@code addEffect} 的写入方决定（原版没有硬上限），
     * 但真实玩法里不可能超过十几个。这里放一个上界，是为了让
     * <b>客户端</b>在读到被伪造的「条数 = 20 亿」时不会直接 {@code OOM}。</p>
     */
    public static final int MAX_EFFECTS = 64;

    private MaidEffectPacket() {
    }

    /**
     * 服务端注册接收器（在主入口调用）。
     */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，所以字段必须在 execute 之前读完
                    byte action = buf.readByte();
                    int entityId = buf.readInt();
                    if (action != REQUEST) {
                        return;
                    }
                    server.execute(() -> handleRequest(player, entityId));
                });
    }

    private static void handleRequest(ServerPlayer player, int entityId) {
        if (!(player.level.getEntity(entityId) instanceof EntityMaid maid)) {
            return;
        }
        if (!maid.isOwnedByPlayer(player)) {
            return;
        }
        sendSync(player, maid);
    }

    /**
     * 服务端调用：把这只女仆的当前效果回传给指定玩家。
     *
     * <p>直接遍历 {@link EntityMaid#getActiveEffects()} —— 它返回的是服务端
     * {@code LivingEntity} 内部那份活动的 map，所以拿到的一定是最新值。</p>
     */
    public static void sendSync(ServerPlayer player, EntityMaid maid) {
        Collection<MobEffectInstance> active = maid.getActiveEffects();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(SYNC);
        buf.writeInt(maid.getId());
        int size = Math.min(active.size(), MAX_EFFECTS);
        buf.writeInt(size);
        int written = 0;
        for (MobEffectInstance effect : active) {
            if (written++ >= size) {
                break;
            }
            MaidEffectData.of(effect).writeToBuf(buf);
        }
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
}


package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * 「让附近客户端在某只女仆身上生成一撮粒子」的 S2C 包，
 * 移植自上游 {@code network/message/SpawnParticleMessage}。
 *
 * <p><b>为什么不能直接服务端发粒子：</b>上游这批粒子确实可以走
 * {@code ServerLevel#sendParticles}，但其中 {@link Type#HEART} / {@link Type#BUBBLE}
 * 用的是 {@code Level#addParticle} 的「客户端本地」语义 —— 那是为了与女仆自身的
 * 动画节奏严格同步（粒子随模型的呼吸抖动一起出现）。用包分发可以保证
 * 「饰品触发的那一刻」和「粒子出现的那一刻」是同一 tick。</p>
 *
 * <p><b>与上游的差异：</b></p>
 * <ol>
 *     <li>上游有 {@code delayTicks} 字段，实现在 <b>网络线程里 {@code Thread.sleep}</b> 再回主线程。
 *         本移植版不移植这条路径：那是上游为「升级特效延后播放」临时加的，
 *         本次移植的饰品全部是即时反馈，用不到；顺带避免了一个真实风险 ——
 *         在网络处理线程里睡眠会拖住整条连接。</li>
 *     <li>上游按 {@code Type} 直接调用女仆自己的 {@code spawnXxxParticle()}；
 *         本移植版同样如此，粒子实现留在 {@link EntityMaid} 上，与上游一一对应。</li>
 * </ol>
 */
public final class MaidParticlePacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "maid_particle");

    /**
     * 上游 {@code NetworkHandler#sendToNearby} 用的半径是 16 格。
     */
    private static final double NEARBY_RADIUS = 16.0D;

    private MaidParticlePacket() {
    }

    /**
     * 服务端调用：把「在 {@code maid} 身上生成某种粒子」发给附近所有玩家。
     *
     * <p>距离用 {@code distanceToSqr} 判定，与上游 {@code NetworkHandler#sendToNearby} 一致。</p>
     */
    public static void sendNearby(EntityMaid maid, Type type) {
        if (!(maid.level instanceof ServerLevel serverLevel)) {
            return;
        }
        double radiusSqr = NEARBY_RADIUS * NEARBY_RADIUS;
        FriendlyByteBuf base = new FriendlyByteBuf(Unpooled.buffer());
        base.writeInt(maid.getId());
        base.writeByte(type.ordinal());
        byte[] payload = new byte[base.readableBytes()];
        base.readBytes(payload);
        base.release();
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(maid) > radiusSqr) {
                continue;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBytes(payload);
            ServerPlayNetworking.send(player, CHANNEL, buf);
        }
    }

    /**
     * 客户端接收后按类型驱动女仆自己的粒子方法。
     */
    public static void handle(EntityMaid maid, int typeOrdinal) {
        Type type = byIndex(typeOrdinal);
        switch (type) {
            case EXPLOSION -> maid.spawnExplosionParticle();
            case BUBBLE -> maid.spawnBubbleParticle();
            case HEART -> maid.spawnHeartParticle();
            case RANK_UP -> maid.spawnRankUpParticle();
            case HEAL -> maid.spawnRestoreHealthParticle(maid.getRandom().nextInt(3) + 7);
        }
    }

    private static Type byIndex(int index) {
        Type[] values = Type.values();
        return values[Mth.clamp(index, 0, values.length - 1)];
    }

    /**
     * 粒子类型，顺序与上游 {@code SpawnParticleMessage.Type} 完全一致
     * （网络里传 ordinal，顺序不能改）。
     */
    public enum Type {
        EXPLOSION, BUBBLE, HEART, RANK_UP, HEAL
    }
}


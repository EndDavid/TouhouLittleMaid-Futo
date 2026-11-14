package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「把这名女仆的作息坐标发给我看」—— S2C，移植自上游
 * {@code network/message/SyncMaidAreaMessage}。
 *
 * <h2>为什么不是实体同步数据</h2>
 * <p>原版指南针右击女仆只是<b>临时看一眼</b>三个区域。把三个 {@link BlockPos}
 * 加维度塞进 {@code SynchedEntityData} 意味着每只女仆、每时每刻、每 20 秒一轮
 * 都在传这份没人看的坐标 —— 而作息坐标几乎一辈子不变。</p>
 * <p>所以沿用上游的做法：<b>按需</b>发一次，客户端缓存 30 秒后自己丢弃
 * （见 {@code client/render/MaidAreaRenderer}）。</p>
 *
 * <h2>为什么只带 id 不带实体引用</h2>
 * <p>方块坐标是纯值，传输时不需要实体存在；客户端拿 id 去 {@code level.getEntity(id)}
 * 自己找。找不到（她已经走出视距 / 区块卸载）就跳过这一帧，而不是让服务端去序列化一只实体。</p>
 */
public final class MaidAreaPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "maid_area");

    private MaidAreaPacket() {
    }

    /**
     * 服务端调用：把这只女仆的作息坐标发给指定玩家。
     */
    public static void send(ServerPlayer player, EntityMaid maid) {
        SchedulePos pos = maid.getSchedulePos();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(maid.getId());
        buf.writeBlockPos(pos.getWorkPos());
        buf.writeBlockPos(pos.getIdlePos());
        buf.writeBlockPos(pos.getSleepPos());
        buf.writeResourceLocation(pos.getDimension());
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    /**
     * 客户端调用：把缓冲区读成一份 {@link SchedulePos}。
     *
     * <p>刻意构造真正的 {@link SchedulePos} 而不是另建一个 DTO：区域渲染要读的是
     * 「三个点各是什么」，用同一个类型可以保证服务端算出来的东西和客户端画出来的
     * 东西一定同构。{@code configured} 在这里没有意义（能发过来就说明配置过），
     * 传 {@code true} 只是为了让这个对象不自相矛盾。</p>
     */
    public static Area read(FriendlyByteBuf buf) {
        int maidId = buf.readVarInt();
        BlockPos workPos = buf.readBlockPos();
        BlockPos idlePos = buf.readBlockPos();
        BlockPos sleepPos = buf.readBlockPos();
        ResourceLocation dimension = buf.readResourceLocation();
        return new Area(maidId, new SchedulePos(workPos, idlePos, sleepPos, dimension));
    }

    /**
     * 一次通知的内容：谁的、哪三个点。
     */
    public static final class Area {
        private final int maidId;
        private final SchedulePos schedulePos;

        public Area(int maidId, SchedulePos schedulePos) {
            this.maidId = maidId;
            this.schedulePos = schedulePos;
            this.schedulePos.setConfigured(true);
        }

        public int getMaidId() {
            return this.maidId;
        }

        public SchedulePos getSchedulePos() {
            return this.schedulePos;
        }
    }
}


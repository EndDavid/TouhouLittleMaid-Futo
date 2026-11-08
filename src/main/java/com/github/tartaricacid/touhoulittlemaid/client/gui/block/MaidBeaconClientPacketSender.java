package com.github.tartaricacid.touhoulittlemaid.client.gui.block;

import com.github.tartaricacid.touhoulittlemaid.network.MaidBeaconPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 神社庭灯 GUI 的客户端发包封装。
 *
 * <p>把 {@code ClientPlayNetworking} 的引用限制在这个类里，这样
 * {@link MaidBeaconPacket} 可以留在 common 包中，专用服务端不会去解析客户端类。</p>
 *
 * <p>三个动作都<b>只发意图</b>：真正的写入在服务端完成（见 {@code MaidBeaconPacket}）。
 * 本地直接改方块实体会在下一帧被服务端的同步数据覆盖回去。</p>
 */
public final class MaidBeaconClientPacketSender {
    private MaidBeaconClientPacketSender() {
    }

    /**
     * 选择 / 取消效果。{@code index = -1} 表示关闭。
     */
    public static void sendPotion(BlockPos pos, int index) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(MaidBeaconPacket.SET_POTION);
        buf.writeBlockPos(pos);
        buf.writeInt(index);
        ClientPlayNetworking.send(MaidBeaconPacket.CHANNEL, buf);
    }

    /**
     * 切换「删除溢出 P 点」。
     */
    public static void sendOverflow(BlockPos pos, boolean overflowDelete) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(MaidBeaconPacket.SET_OVERFLOW);
        buf.writeBlockPos(pos);
        buf.writeBoolean(overflowDelete);
        ClientPlayNetworking.send(MaidBeaconPacket.CHANNEL, buf);
    }

    /**
     * 存入（{@code isStorage = true}）或取出指定数量的灵力。
     */
    public static void sendStoreOrTake(BlockPos pos, float powerNum, boolean isStorage) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(MaidBeaconPacket.STORE_OR_TAKE);
        buf.writeBlockPos(pos);
        buf.writeFloat(powerNum);
        buf.writeBoolean(isStorage);
        ClientPlayNetworking.send(MaidBeaconPacket.CHANNEL, buf);
    }
}


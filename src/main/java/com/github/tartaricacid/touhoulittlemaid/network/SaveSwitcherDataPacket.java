package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityModelSwitcher;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 「保存模型切换器的模式列表」的 C2S 包，移植自上游 {@code SaveSwitcherDataMessage}。
 *
 * <p>客户端那侧改的是<b>方块实体里那份列表的一份引用</b>（{@code ModelSwitcherGui}
 * 直接从 {@code switcher.getInfoList()} 拿的），但客户端手里的是同步过来的拷贝，
 * 改它不会传回服务端 —— 所以关界面与点「保存」时都必须把这个列表发包送回去。</p>
 *
 * <h2>服务端要复核什么</h2>
 * <ol>
 *     <li>坐标所在区块已加载（{@code ServerLevel#isLoaded}）—— 否则 {@code getBlockEntity} 会强制加载区块；</li>
 *     <li>该坐标确实是模型切换器 —— 不校验的话，任何客户端都能往任意方块实体坐标塞数据。</li>
 * </ol>
 */
public final class SaveSwitcherDataPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "save_switcher_data");

    private SaveSwitcherDataPacket() {
    }

    /**
     * 客户端调用：把整个列表发回服务端。
     */
    public static void send(BlockPos pos, List<TileEntityModelSwitcher.ModeInfo> infoList) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(infoList.size());
        for (TileEntityModelSwitcher.ModeInfo info : infoList) {
            info.toBuf(buf);
        }
        ClientPlayNetworking.send(CHANNEL, buf);
    }

    /**
     * 服务端注册接收器（在主入口调用）。
     */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，整个列表必须在 execute 之前读完
                    BlockPos pos = buf.readBlockPos();
                    int size = buf.readVarInt();
                    List<TileEntityModelSwitcher.ModeInfo> infoList = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        infoList.add(TileEntityModelSwitcher.ModeInfo.fromBuf(buf));
                    }
                    server.execute(() -> handle(player, pos, infoList));
                });
    }

    private static void handle(net.minecraft.server.level.ServerPlayer player,
                               BlockPos pos, List<TileEntityModelSwitcher.ModeInfo> infoList) {
        if (!(player.level instanceof ServerLevel serverLevel) || !serverLevel.isLoaded(pos)) {
            return;
        }
        BlockEntity te = serverLevel.getBlockEntity(pos);
        if (te instanceof TileEntityModelSwitcher switcher) {
            switcher.setInfoList(infoList);
        }
    }
}


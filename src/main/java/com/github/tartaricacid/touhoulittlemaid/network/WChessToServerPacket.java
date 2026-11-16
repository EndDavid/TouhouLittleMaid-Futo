package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockWChess;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 客户端 → 服务端：把算好的一步棋交回去执行。
 *
 * <p>对应上游 {@code WChessToServerMessage}。安全检查与中国象棋那个包一致
 * （{@link CChessToServerPacket}）：先查区块是否加载、再查距离，最后才把棋交出去；
 * 两个「谁输了」的布尔值由 {@link BlockWChess#maidMove} 重新验证。</p>
 */
public final class WChessToServerPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "wchess_to_server");

    private WChessToServerPacket() {
    }

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    int move = buf.readVarInt();
                    boolean maidLost = buf.readBoolean();
                    boolean playerLost = buf.readBoolean();
                    server.execute(() -> handle(player, pos, move, maidLost, playerLost));
                });
    }

    private static void handle(ServerPlayer sender, BlockPos pos, int move, boolean maidLost, boolean playerLost) {
        Level level = sender.level;
        // 方块可能已经被拆了（或者玩家跑得太远，区块已卸载）
        if (!level.isLoaded(pos)) {
            return;
        }
        // 距离检查：客户端只在自己看得见的棋盘上下棋，超过这个距离的包一律当伪造
        if (sender.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) {
            return;
        }
        BlockWChess.maidMove(sender, level, pos, move, maidLost, playerLost);
    }
}


package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockCChess;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 客户端 → 服务端：把算好的一步棋交回去执行。
 *
 * <p>对应上游 {@code CChessToServerMessage}。</p>
 *
 * <p><b>两个 boolean 不可信。</b>{@code maidLost} / {@code playerLost} 由客户端计算并提交，
 * 一个改过的客户端可以直接宣布「女仆输了」。上游的对策是在服务端把最关键的那一支
 * （{@code maidLost}）重新用棋局验证一遍 —— 见 {@link BlockCChess#maidMove}。
 * 这里额外先检查一次方块仍在、且确实轮到女仆，避免迟到的包把棋局走乱。</p>
 */
public final class CChessToServerPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation(TouhouLittleMaid.MOD_ID, "cchess_to_server");

    private CChessToServerPacket() {
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
        BlockCChess.maidMove(sender, level, pos, move, maidLost, playerLost);
    }
}

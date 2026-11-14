package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 服务端 → 客户端：把当前局面交给玩家，让他的客户端去算女仆的下一步。
 *
 * <p>对应上游 {@code CChessToClientMessage}。{@code fen} 是整盘棋，不传移动列表 ——
 * 引擎本来就是从 FEN 恢复局面的，传整盘还能让客户端自检（收到什么就是什么）。</p>
 *
 * <p><b>为什么要设长度上限：</b>{@code readUtf} 不带上限时会按对方声明的长度分配内存。
 * 象棋 FEN 最长也就百来字节，这里给 256 足够，同时挡掉畸形包。</p>
 */
public final class CChessToClientPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation(TouhouLittleMaid.MOD_ID, "cchess_to_client");
    private static final int MAX_FEN_LENGTH = 256;

    private CChessToClientPacket() {
    }

    public static void sendToPlayer(@Nullable ServerPlayer player, BlockPos pos, String fen) {
        if (player == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeUtf(fen, MAX_FEN_LENGTH);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    /**
     * 客户端收包时调用：读出一个 {@code [坐标, FEN]} 对。
     *
     * <p>不引用任何客户端类型，所以可以留在 common 侧。</p>
     */
    public static Payload read(FriendlyByteBuf buf) {
        return new Payload(buf.readBlockPos(), buf.readUtf(MAX_FEN_LENGTH));
    }

    public static final class Payload {
        private final BlockPos pos;
        private final String fen;

        private Payload(BlockPos pos, String fen) {
            this.pos = pos;
            this.fen = fen;
        }

        public BlockPos getPos() {
            return pos;
        }

        public String getFen() {
            return fen;
        }
    }
}


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
 * <p>对应上游 {@code WChessToClientMessage}，与中国象棋那个（{@link CChessToClientPacket}）
 * 结构完全一致 —— 因为两套引擎都有 {@code toFen} / {@code fromFen}，
 * 整盘棋一个字符串就能传完。</p>
 */
public final class WChessToClientPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "wchess_to_client");
    /**
     * 国际象棋 FEN 比中国象棋长一些（含易位权 / 吃过路兵 / 半回合计数），给 256 足够。
     */
    private static final int MAX_FEN_LENGTH = 256;

    private WChessToClientPacket() {
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


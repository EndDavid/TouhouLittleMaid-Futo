package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.util.GomokuUtil;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 服务端 → 客户端：把整个 15×15 棋盘 + 玩家刚落的子 + 女仆胜场数交给客户端，
 * 让它在后台线程里跑五子棋 AI。
 *
 * <p>对应上游 {@code GomokuToClientMessage}。五子棋没有 FEN 可传
 * （引擎只吃 {@code int[][]}），所以直接把 15 条 {@code int[]} 写过去 ——
 * 一条 15 个数、每个数 0/1/2，用 {@code writeVarIntArray} 之后每个数只占 1 字节，
 * 整包不到 300 字节，比传增量列表还简单可靠。</p>
 *
 * <p><b>为什么要传 {@code count}（胜场数）：</b>女仆的棋力是按胜场分档的
 * （{@code MaidGomokuAI#getService}）。胜场数是同步数据，客户端其实也能读，
 * 但这里显式传一份，保证「算这步棋时用的档位」和「服务端当时看到的档位」是同一个值 ——
 * 否则一次同步延迟就会让对局两边对不上。</p>
 */
public final class GomokuToClientPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "gomoku_to_client");

    private GomokuToClientPacket() {
    }

    public static void sendToPlayer(@Nullable ServerPlayer player, BlockPos pos, int[][] chessData,
                                    Point point, int winCount) {
        if (player == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(chessData.length);
        for (int[] row : chessData) {
            buf.writeVarIntArray(row);
        }
        buf.writeVarInt(point.x);
        buf.writeVarInt(point.y);
        buf.writeVarInt(point.type);
        buf.writeVarInt(winCount);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    /**
     * 客户端收包时调用。不引用任何客户端类型，所以可以留在 common 侧。
     *
     * <p>读的时候按 {@link GomokuUtil#GRID_SIZE} 补齐 / 截断：这段代码面对的是网络来的数据，
     * 行数、行长得由自己说了算，不能直接相信对方写的长度。</p>
     */
    public static Payload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int rows = Math.min(buf.readVarInt(), GomokuUtil.GRID_SIZE);
        int[][] chessData = new int[GomokuUtil.GRID_SIZE][GomokuUtil.GRID_SIZE];
        for (int i = 0; i < rows; i++) {
            int[] row = buf.readVarIntArray();
            System.arraycopy(row, 0, chessData[i], 0, Math.min(row.length, GomokuUtil.GRID_SIZE));
        }
        Point point = new Point(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        int winCount = buf.readVarInt();
        return new Payload(pos, chessData, point, winCount);
    }

    public static final class Payload {
        private final BlockPos pos;
        private final int[][] chessData;
        private final Point point;
        private final int winCount;

        private Payload(BlockPos pos, int[][] chessData, Point point, int winCount) {
            this.pos = pos;
            this.chessData = chessData;
            this.point = point;
            this.winCount = winCount;
        }

        public BlockPos getPos() {
            return pos;
        }

        public int[][] getChessData() {
            return chessData;
        }

        public Point getPoint() {
            return point;
        }

        public int getWinCount() {
            return winCount;
        }
    }
}


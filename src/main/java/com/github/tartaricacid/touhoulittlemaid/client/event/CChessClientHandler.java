package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position;
import com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Search;
import com.github.tartaricacid.touhoulittlemaid.network.CChessToServerPacket;
import com.github.tartaricacid.touhoulittlemaid.util.CChessUtil;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.concurrent.CompletableFuture;

/**
 * 客户端替女仆思考一步棋。
 *
 * <p>对应上游 {@code CChessToClientMessage} 的客户端分支。三个要点：</p>
 * <ol>
 *     <li><b>必须在后台线程算。</b>{@code Search#searchMain(1000)} 是同步的、会跑满 1 秒 CPU
 *         （象棋引擎的迭代加深搜索）。放在渲染线程上就是 1 秒的卡死，每秒一次。</li>
 *     <li><b>算完必须回到客户端线程发包。</b>{@code ClientPlayNetworking.send} 不是线程安全的。</li>
 *     <li><b>补足 1 秒。</b>引擎有可能提前收敛（例如必杀局几十毫秒就算完），
 *         不补的话女仆会「瞬间回应」，看着不像在思考。这是上游的行为，照抄。</li>
 * </ol>
 */
public final class CChessClientHandler {
    /**
     * 每步的思考时间上限（毫秒），同时用作「看起来像在想」的最短时间。
     */
    private static final int LEVEL_TIME = 1000;
    private static final int SEARCH_DEPTH = 12;

    private CChessClientHandler() {
    }

    /**
     * 由客户端收包处调用（已经切到客户端线程）。
     */
    public static void handle(Minecraft client, BlockPos pos, String fen) {
        CompletableFuture.runAsync(() -> search(client, pos, fen), Util.backgroundExecutor());
    }

    private static void search(Minecraft client, BlockPos pos, String fen) {
        long timeStart = System.currentTimeMillis();
        int move = 0;
        boolean maidLost;
        boolean playerLost = false;

        Position position = new Position();
        try {
            position.fromFen(fen);
        } catch (Exception e) {
            // 局面解析失败就什么都不做：宁可这一局停住，也不要发一个非法走法给服务端
            TouhouLittleMaid.LOGGER.error("Failed to parse chess FEN: {}", fen, e);
            return;
        }

        // 先判断玩家是否赢了。放在客户端算，是为了减轻服务端压力；
        // 代价是这两个布尔值可以被作弊客户端伪造，服务端会重新验一遍（见 BlockCChess#maidMove）
        maidLost = CChessUtil.isMaid(position) && position.isMate();
        if (!maidLost) {
            // TODO: 暂时不做女仆的棋技系统（上游同款）
            move = new Search(position, SEARCH_DEPTH).searchMain(LEVEL_TIME);
            // 玩家是否输了
            playerLost = position.makeMove(move) && CChessUtil.isPlayer(position) && position.isMate();
        }

        long timeRemain = Math.max(0, LEVEL_TIME - (System.currentTimeMillis() - timeStart));
        if (timeRemain > 0) {
            try {
                Thread.sleep(timeRemain);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        final int moveFinal = move;
        final boolean maidLostFinal = maidLost;
        final boolean playerLostFinal = playerLost;
        client.execute(() -> send(pos, moveFinal, maidLostFinal, playerLostFinal));
    }

    private static void send(BlockPos pos, int move, boolean maidLost, boolean playerLost) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(move);
        buf.writeBoolean(maidLost);
        buf.writeBoolean(playerLost);
        ClientPlayNetworking.send(CChessToServerPacket.CHANNEL, buf);
    }
}

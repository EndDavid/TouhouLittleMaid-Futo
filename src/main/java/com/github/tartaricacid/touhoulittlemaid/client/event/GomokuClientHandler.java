package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.AIService;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidGomokuAI;
import com.github.tartaricacid.touhoulittlemaid.network.GomokuToServerPacket;
import com.github.tartaricacid.touhoulittlemaid.util.GomokuUtil;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.concurrent.CompletableFuture;

/**
 * 客户端替女仆思考一步五子棋。
 *
 * <p>对应上游 {@code GomokuToClientMessage} 的客户端分支。与中国象棋那两条不同的是：
 * 五子棋的 AI <b>不保证返回合法点位</b> —— {@code ZhiZhangAIService} 在「棋盘满了」之类
 * 的边界上会返回 {@link Point#NULL}（坐标 -1）。上游直接把结果发回服务端，
 * 服务端就会 {@code chessData[-1][-1]} 抛异常。这里在发送前先挡一次，
 * 万一 AI 返回空点就干脆不发，这一局停在「等女仆」的状态，玩家点两下棋盘就能重开。</p>
 *
 * <p>另外还有一条上游没写但很明显的东西：<b>AI 可能算很久</b>（HELL 档深度 8 + 算杀深度 10），
 * 而这段跑在 Fabric 的后台线程池里。发给服务端的包必须切回客户端线程。</p>
 */
public final class GomokuClientHandler {
    /**
     * 「看起来在想」的最短 / 最长等待（毫秒）。上游是 250 起、随机 1250，照抄。
     */
    private static final int MIN_THINK_TIME = 250;
    private static final int RANDOM_THINK_TIME = 1250;

    private GomokuClientHandler() {
    }

    /**
     * 由客户端收包处调用（已经切到客户端线程）。
     */
    public static void handle(Minecraft client, BlockPos pos, int[][] chessData, Point point, int winCount) {
        CompletableFuture.runAsync(() -> think(client, pos, chessData, point, winCount), Util.backgroundExecutor());
    }

    private static void think(Minecraft client, BlockPos pos, int[][] chessData, Point point, int winCount) {
        Point aiPoint;
        try {
            AIService service = MaidGomokuAI.getService(winCount);
            aiPoint = service.getPoint(chessData, point);
        } catch (Throwable e) {
            // AI 是第三方算法（ZhiZhangAIService），防御性兜底：算崩了也不能让客户端炸掉
            TouhouLittleMaid.LOGGER.error("Gomoku AI failed", e);
            return;
        }
        if (aiPoint == null || aiPoint.x < 0 || aiPoint.x >= GomokuUtil.GRID_SIZE
                || aiPoint.y < 0 || aiPoint.y >= GomokuUtil.GRID_SIZE) {
            return;
        }

        long wait = MIN_THINK_TIME + (long) (Math.random() * RANDOM_THINK_TIME);
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        final Point pointFinal = aiPoint;
        client.execute(() -> send(pos, pointFinal));
    }

    private static void send(BlockPos pos, Point point) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(point.x);
        buf.writeVarInt(point.y);
        buf.writeVarInt(point.type);
        ClientPlayNetworking.send(GomokuToServerPacket.CHANNEL, buf);
    }
}

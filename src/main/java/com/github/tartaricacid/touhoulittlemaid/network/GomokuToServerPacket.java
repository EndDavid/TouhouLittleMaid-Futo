package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidGomokuAI;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import com.github.tartaricacid.touhoulittlemaid.util.GomokuUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

/**
 * 客户端 → 服务端：女仆算好的落子点。
 *
 * <p>对应上游 {@code GomokuToServerMessage}。</p>
 *
 * <h2>为什么这个包比象棋那两个更需要校验</h2>
 * <p>象棋回传的是一整个「引擎走法编码 + 两个布尔值」，服务端至少还能用
 * {@code Position#legalMove} 复核合法性。五子棋回传的是<b>一个裸坐标</b>，
 * 上游直接 {@code setChessData(aiPoint.x, aiPoint.y, aiPoint.type)} ——
 * 一个改过的客户端可以：</p>
 * <ul>
 *     <li>把坐标写成 999，让服务端 {@code ArrayIndexOutOfBounds}（踢掉自己的连接，
 *         但方块实体已经被写脏了，存档里留下了垃圾）；</li>
 *     <li>往已经有子的交叉点再放一颗，把棋局搞坏；</li>
 *     <li>用 {@code type = BLACK} 冒充玩家，等于替玩家落子。</li>
 * </ul>
 * <p>所以这里逐条验：坐标在 15×15 内、该点为空、颜色<b>强制</b>为白
 * （女仆永远执白，这是规则而不是请求）。</p>
 */
public final class GomokuToServerPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "gomoku_to_server");

    private GomokuToServerPacket() {
    }

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    int x = buf.readVarInt();
                    int y = buf.readVarInt();
                    int type = buf.readVarInt();
                    server.execute(() -> handle(player, pos, x, y, type));
                });
    }

    private static void handle(ServerPlayer sender, BlockPos pos, int x, int y, int type) {
        Level level = sender.level;
        if (!level.isLoaded(pos)) {
            return;
        }
        if (sender.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof TileEntityGomoku gomoku)) {
            return;
        }
        // 不是女仆的回合 / 这一局已经结束了 / 一步都还没走 —— 都是迟到或伪造的包
        if (gomoku.getStatue() != Statue.IN_PROGRESS || gomoku.isPlayerTurn() || gomoku.getChessCounter() <= 0) {
            return;
        }
        if (x < 0 || x >= GomokuUtil.GRID_SIZE || y < 0 || y >= GomokuUtil.GRID_SIZE) {
            return;
        }
        int[][] chessData = gomoku.getChessData();
        if (chessData[x][y] != Point.EMPTY) {
            return;
        }

        // 颜色不听客户端的：女仆执白
        Point aiPoint = new Point(x, y, Point.WHITE);
        gomoku.setChessData(aiPoint.x, aiPoint.y, aiPoint.type);
        Statue statue = MaidGomokuAI.getStatue(chessData, aiPoint);
        gomoku.setStatue(statue);
        if (level instanceof ServerLevel serverLevel && serverLevel.getEntity(gomoku.getSitId()) instanceof EntitySit sit
                && sit.getFirstPassenger() instanceof EntityMaid maid) {
            maid.swing(InteractionHand.MAIN_HAND);
            if (statue == Statue.WIN) {
                maid.getGameRecordManager().markStatue(true);
            }
        }
        level.playSound(null, pos, InitSounds.GOMOKU, SoundSource.BLOCKS,
                1.0f, 0.8F + level.random.nextFloat() * 0.4F);
        if (statue == Statue.IN_PROGRESS) {
            gomoku.setPlayerTurn(true);
        }
        gomoku.refresh();
    }
}

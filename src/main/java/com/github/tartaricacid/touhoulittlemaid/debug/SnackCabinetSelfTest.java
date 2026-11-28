package com.github.tartaricacid.touhoulittlemaid.debug;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
/**
 * 零食柜「右键打开 27 格存储」的<b>服务端自检</b>，只在
 * {@code -Dfuto.snack.selftest=true} 时装配（见 {@code build.gradle} 的 {@code -PsnackSelftest}）。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>「右键点不开」这类报告既没法靠看代码定论，也没法用 RCON 复现 ——
 * {@code item replace block ... container.N} 只能证明方块实体是可读写的容器，
 * 证明不了玩家那一下右键真的走到了 {@code openMenu}。而这里缺的正是「点」这一步：
 * 之前 {@code use} 里还留着上游那句「手持方块物品点顶面 → {@code PASS}」，
 * 于是只要手上拿着方块，顶面就永远开不了界面。</p>
 *
 * <p>这个自检真正调用一遍
 * {@code BlockState#use(Level, Player, InteractionHand, BlockHitResult)}
 * —— 就是服务端收到右键包之后走的那条路 —— 然后断言
 * {@code player.containerMenu} 是 27 格 / 三行的 {@link ChestMenu}。四种拿法 / 三个面各测一次。</p>
 *
 * <h2>为什么手搓 ServerPlayer 而不是 Fabric 的 FakePlayer</h2>
 *
 * <p>1.18.2 这条线的 fabric-api（0.77）里<b>没有</b>
 * {@code net.fabricmc.fabric.api.entity.FakePlayer} —— 那个类要到 1.19 的 0.6.x 模块才有
 * （已核对缓存里 0.4.19 / 0.4.20 两个 jar，都没有）。所以这里按 Fabric 内部
 * {@code FakePlayerNetworkHandler} 的做法自己来：一个 {@link ServerPlayer}，
 * 外加一个把 {@code send} 全部吞掉的 {@link ServerGamePacketListenerImpl}
 * （底层 {@link Connection} 是本地构造的，不会连任何地方）。
 * 这样 {@code openMenu → initMenu → slotChanged} 里那些发包调用就落在空实现上，
 * 不会因为 {@code connection == null} 抛 NPE。</p>
 *
 * <p>用法：{@code gradlew runServer -PsnackSelftest}。跑完会自己 {@code halt}，
 * 日志里出现 {@code [Futo] snack selftest: PASS} 即通过（每一小步也单独打一行）。</p>
 */
public final class SnackCabinetSelfTest {
    /** 等世界完全就绪再动手：SERVER_STARTED 时出生点区块刚被加载。 */
    private static int delayTicks = 20;
    private static boolean done = false;
    private SnackCabinetSelfTest() {
    }
    public static void init() {
        TouhouLittleMaid.LOGGER.info("[Futo] snack selftest: armed");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> delayTicks = 20);
        ServerTickEvents.END_SERVER_TICK.register(SnackCabinetSelfTest::tick);
    }
    private static void tick(MinecraftServer server) {
        if (done || --delayTicks > 0) {
            return;
        }
        done = true;
        boolean ok;
        try {
            ok = runChecks(server);
        } catch (Throwable t) {
            // 自检本身崩了也算失败，但要留下堆栈：这里是唯一能看到它的地方
            TouhouLittleMaid.LOGGER.error("[Futo] snack selftest: threw", t);
            ok = false;
        }
        if (ok) {
            TouhouLittleMaid.LOGGER.info("[Futo] snack selftest: PASS");
        } else {
            TouhouLittleMaid.LOGGER.error("[Futo] snack selftest: FAIL");
        }
        server.halt(false);
    }
    private static boolean runChecks(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
        ServerPlayer player = silentPlayer(server, level);
        BlockState state = InitBlocks.SNACK_CABINET.defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        TouhouLittleMaid.LOGGER.info("[Futo] snack selftest: cabinet placed at {}", pos);
        boolean ok = true;
        try {
            // 用 &= 而不是 &&：四步都要跑完，日志里才能一次看全
            ok &= check(level, pos, player, ItemStack.EMPTY, Direction.NORTH, "empty hand / side face");
            // 这一条就是旧版 return PASS 的那一种（手持方块 + 顶面）
            ok &= check(level, pos, player, new ItemStack(Items.CAKE), Direction.UP, "cake in hand / top face");
            ok &= check(level, pos, player, ItemStack.EMPTY, Direction.UP, "empty hand / top face");
            ok &= check(level, pos, player, new ItemStack(Items.OAK_PLANKS), Direction.EAST,
                    "planks in hand / side face");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.closeContainer();
            level.removeBlock(pos, false);
        }
        return ok;
    }
    private static boolean check(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack held,
                                 Direction face, String name) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        InteractionResult result = level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, hit);
        boolean consumed = result.consumesAction();
        // 27 格 = 三行（threeRows）；39 格以上的双箱才是六行
        boolean menuOk = player.containerMenu instanceof ChestMenu chest && chest.getRowCount() == 3;
        TouhouLittleMaid.LOGGER.info("[Futo] snack selftest [{}]: use={} menu={} rows={} -> {}",
                name, result, player.containerMenu.getClass().getSimpleName(),
                player.containerMenu instanceof ChestMenu chest ? chest.getRowCount() : -1,
                consumed && menuOk ? "ok" : "NOT ok");
        player.closeContainer();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return consumed && menuOk;
    }
    /** 造一个「发包但没人收」的玩家：界面相关的包全部落在空实现里。 */
    private static ServerPlayer silentPlayer(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("futo-snack-selftest".getBytes()),
                "[Futo Snack Selftest]");
        ServerPlayer player = new ServerPlayer(server, level, profile);
        player.connection = new ServerGamePacketListenerImpl(server,
                new Connection(PacketFlow.CLIENTBOUND), player) {
            @Override
            public void send(Packet<?> packet) {
                // 这个假玩家没有真正的网络对端，打开 / 关闭 / 内容同步都不需要发出去
            }
        };
        return player;
    }
}

package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityModelSwitcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 「打开模型切换器界面」的 S2C 包，移植自上游 {@code OpenSwitcherGuiMessage}。
 *
 * <p>为什么不用菜单（{@code MenuType}）：这个界面<b>不是容器</b> ——
 * 里面没有物品槽，只有一份模式列表 + 一个女仆预览。上游同样是纯
 * {@code Screen}，用自定义包把 {@code BlockPos} 发下去，客户端拿坐标找方块实体。</p>
 *
 * <p>所以它也不需要 {@code InitMenu} 里那种「服务端开菜单、客户端按类型选界面」的机制，
 * 比容器界面少一整层。</p>
 */
public final class OpenSwitcherGuiPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "open_switcher_gui");

    private OpenSwitcherGuiPacket() {
    }

    /**
     * 服务端调用：让这名玩家打开指定坐标的切换器界面。
     */
    public static void send(ServerPlayer player, BlockPos pos) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    /**
     * 客户端注册接收器（在客户端入口调用）。
     *
     * <p>整段逻辑都在客户端，所以这里直接引用 {@code Minecraft} 并标 {@link Environment}；
     * 只要不在主入口（服务端也会加载的那个类）里碰它就行。</p>
     */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，坐标必须在切回主线程之前读完
                    BlockPos pos = buf.readBlockPos();
                    client.execute(() -> handleOpenGui(pos));
                });
    }

    @Environment(EnvType.CLIENT)
    private static void handleOpenGui(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()) {
            return;
        }
        BlockEntity te = minecraft.level.getBlockEntity(pos);
        if (te instanceof TileEntityModelSwitcher switcher) {
            minecraft.setScreen(new com.github.tartaricacid.touhoulittlemaid.client.screen.ModelSwitcherGui(switcher));
        }
    }
}


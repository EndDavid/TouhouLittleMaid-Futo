package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.capability.IPower;
import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapability;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 神社庭灯的网络包（单一通道 + 动作码），合并自上游的四个包：
 * {@code OpenBeaconGuiMessage}（S2C）、{@code SetBeaconPotionMessage}、
 * {@code SetBeaconOverflowMessage}、{@code StorageAndTakePowerMessage}（C2S）。
 *
 * <p><b>载荷格式：</b>第一个字节是动作码，紧接着永远是方块坐标
 * （{@link #OPEN_GUI} 只有这两项），之后按动作码决定后续字段。</p>
 *
 * <p><b>读取顺序：</b>所有字段必须在网络线程里读完再 {@code server.execute}，
 * 出回调后缓冲区就会被释放。</p>
 *
 * <p><b>服务端复核：</b>这个包是玩家可控的，所以每个动作都要重新验证
 * 「坐标处确实有庭灯方块实体」「玩家没有离得太远」「效果序号在合法区间内」。
 * 上游只检查了前两项中的第一项，做法更宽松。</p>
 *
 * <p>客户端发送端见 {@code client/gui/block/MaidBeaconGui} 与
 * {@code client/gui/widget/button/BeaconEffectButton}（那里才会引用
 * {@code ClientPlayNetworking}，避免专用服务端加载客户端类）。</p>
 */
public final class MaidBeaconPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation(TouhouLittleMaid.MOD_ID, "maid_beacon");

    /**
     * C2S：选择效果。{@code int} 序号，{@code -1} 表示取消。
     */
    public static final byte SET_POTION = 0;
    /**
     * C2S：溢出 P 点是否删除。
     */
    public static final byte SET_OVERFLOW = 1;
    /**
     * C2S：存入 / 取出（{@code float} 数量 + {@code boolean} 是否存入）。
     */
    public static final byte STORE_OR_TAKE = 2;
    /**
     * S2C：打开 GUI。
     */
    public static final byte OPEN_GUI = 3;

    /**
     * 允许操作的最大距离平方（8 格）。原版交互距离约 4.5 格，留一点余量给高延迟玩家。
     */
    private static final double MAX_DISTANCE_SQR = 64.0;

    private MaidBeaconPacket() {
    }

    /**
     * 服务端注册接收器（在主入口调用）。
     */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    byte action = buf.readByte();
                    BlockPos pos = buf.readBlockPos();
                    switch (action) {
                        case SET_POTION -> {
                            int index = buf.readInt();
                            server.execute(() -> handleSetPotion(player, pos, index));
                        }
                        case SET_OVERFLOW -> {
                            boolean value = buf.readBoolean();
                            server.execute(() -> handleSetOverflow(player, pos, value));
                        }
                        case STORE_OR_TAKE -> {
                            float amount = buf.readFloat();
                            boolean store = buf.readBoolean();
                            server.execute(() -> handleStoreOrTake(player, pos, amount, store));
                        }
                        default -> {
                            // 未知动作码：直接忽略（可能是版本不一致的客户端）
                        }
                    }
                });
    }

    /**
     * 服务端调用：让客户端打开坐标处的庭灯界面。
     */
    public static void sendOpenGui(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(OPEN_GUI);
        buf.writeBlockPos(pos);
        ServerPlayNetworking.send(serverPlayer, CHANNEL, buf);
    }

    // ------------------------------------------------------------------ 服务端处理

    /**
     * 找到「玩家够得着的」庭灯方块实体，否则返回 {@code null}。
     */
    private static TileEntityMaidBeacon findBeacon(ServerPlayer player, BlockPos pos) {
        if (!player.level.isLoaded(pos)) {
            return null;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            return null;
        }
        BlockEntity te = player.level.getBlockEntity(pos);
        return te instanceof TileEntityMaidBeacon beacon ? beacon : null;
    }

    private static void handleSetPotion(ServerPlayer player, BlockPos pos, int index) {
        TileEntityMaidBeacon beacon = findBeacon(player, pos);
        if (beacon == null) {
            return;
        }
        // 越界序号会被 BeaconEffect.getEffectByIndex 夹住，但存进方块实体之前就挡掉更干净
        if (index < -1 || index >= TileEntityMaidBeacon.BeaconEffect.values().length) {
            return;
        }
        beacon.setPotionIndex(index);
    }

    private static void handleSetOverflow(ServerPlayer player, BlockPos pos, boolean overflowDelete) {
        TileEntityMaidBeacon beacon = findBeacon(player, pos);
        if (beacon != null) {
            beacon.setOverflowDelete(overflowDelete);
        }
    }

    private static void handleStoreOrTake(ServerPlayer player, BlockPos pos, float powerNum, boolean isStorage) {
        TileEntityMaidBeacon beacon = findBeacon(player, pos);
        if (beacon == null || !(powerNum > 0) || Float.isNaN(powerNum)) {
            return;
        }
        PowerCapability playerPower = IPower.getPower(player);
        if (isStorage) {
            storageLogic(powerNum, playerPower, beacon);
        } else {
            takeLogic(powerNum, playerPower, beacon);
        }
    }

    /**
     * 玩家 → 庭灯。上游的规则：玩家灵力够就存，庭灯满了则只存得下多少存多少。
     */
    private static void storageLogic(float powerNum, PowerCapability playerPower, TileEntityMaidBeacon beacon) {
        boolean playerPowerIsEnough = powerNum <= playerPower.get();
        boolean beaconNotFull = powerNum + beacon.getStoragePower() <= beacon.getMaxStorage();
        if (playerPowerIsEnough) {
            if (beaconNotFull) {
                playerPower.min(powerNum);
                beacon.setStoragePower(beacon.getStoragePower() + powerNum);
            } else {
                playerPower.min(beacon.getMaxStorage() - beacon.getStoragePower());
                beacon.setStoragePower(beacon.getMaxStorage());
            }
        }
    }

    /**
     * 庭灯 → 玩家。庭灯够就取，玩家灵力快满则只取到上限。
     */
    private static void takeLogic(float powerNum, PowerCapability playerPower, TileEntityMaidBeacon beacon) {
        boolean beaconIsEnough = powerNum <= beacon.getStoragePower();
        boolean playerNotFull = powerNum + playerPower.get() < PowerCapability.MAX_POWER;
        if (beaconIsEnough) {
            if (playerNotFull) {
                beacon.setStoragePower(beacon.getStoragePower() - powerNum);
                playerPower.add(powerNum);
            } else {
                beacon.setStoragePower(beacon.getStoragePower() - PowerCapability.MAX_POWER + playerPower.get());
                playerPower.set(PowerCapability.MAX_POWER);
            }
        }
    }
}


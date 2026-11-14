package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemFoxScroll;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 狐狸卷轴的网络包（单一通道 + 动作码），合并自上游的
 * {@code FoxScrollMessage}（S2C）与 {@code SetScrollData}（C2S）。
 *
 * <p><b>载荷格式：</b>第一个字节永远是动作码，之后按动作码决定内容。</p>
 * <ul>
 *     <li>{@link #SHOW_DATA}（S2C）：{@code varInt 维度数}，每个维度
 *         {@code utf 维度名 + varInt 条数 + 每条(坐标, 名字, 时间戳)}；</li>
 *     <li>{@link #SET_TRACK}（C2S）：{@code utf 维度名 + 方块坐标}。</li>
 * </ul>
 *
 * <p><b>为什么 S2C 也带动作码：</b>同一个通道两个方向都有包时，如果 S2C 不带动作码，
 * 客户端只能靠「这条包是谁发的」来区分，而 Fabric 的接收器不提供这个信息。
 * 带上动作码后两端都能明确分发，将来再加动作也不会互相踩。</p>
 *
 * <p><b>读取顺序：</b>所有字段必须在网络线程里读完再 {@code execute}，
 * 缓冲区出回调即失效。</p>
 *
 * <p><b>为什么要上限：</b>{@link #readData} 里每一条都要按长度分配集合。
 * 服务端是我们自己的代码，但把「维度数 / 条数」当成可信值直接 {@code new ArrayList<>(n)}
 * 是个坏习惯 —— 上限只是几行常量，顺手加上。</p>
 */
public final class FoxScrollPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation(TouhouLittleMaid.MOD_ID, "fox_scroll");

    /**
     * S2C：把卷轴记录发给客户端开界面。
     */
    public static final byte SHOW_DATA = 0;

    /**
     * C2S：把某条记录钉到主手卷轴上。
     */
    public static final byte SET_TRACK = 1;

    /**
     * 维度名的长度上限。原版维度名（{@code minecraft:overworld}）只有二十来个字符。
     */
    public static final int MAX_DIMENSION_LENGTH = 128;

    /**
     * 最多接受多少个维度。原版存档里同时存在的维度也就是个位数。
     */
    public static final int MAX_DIMENSIONS = 16;

    /**
     * 每个维度最多接受多少条记录。界面每页 5 条，这个上限已经是 200 页。
     */
    public static final int MAX_ENTRIES = 1024;

    private FoxScrollPacket() {
    }

    /**
     * 服务端调用：把「维度 → 记录列表」发给指定玩家。空表也会发 ——
     * 客户端要靠它打开界面并显示「没有找到任何坐标」。
     */
    public static void sendData(ServerPlayer player, Map<String, List<FoxScrollData>> data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(SHOW_DATA);
        buf.writeVarInt(data.size());
        data.forEach((dimension, scrollData) -> {
            buf.writeUtf(dimension, MAX_DIMENSION_LENGTH);
            buf.writeVarInt(scrollData.size());
            scrollData.forEach(scroll -> scroll.encode(buf));
        });
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    /**
     * 客户端收包后调用：把缓冲区里的数据读成「维度 → 记录列表」。
     *
     * <p>这个方法不引用任何客户端类型，所以可以放在 common 侧被客户端初始化器调用。</p>
     */
    public static Map<String, List<FoxScrollData>> readData(FriendlyByteBuf buf) {
        int dimensionCount = buf.readVarInt();
        if (dimensionCount < 0 || dimensionCount > MAX_DIMENSIONS) {
            throw new io.netty.handler.codec.DecoderException(
                    "fox_scroll: dimension count out of range: " + dimensionCount);
        }
        Map<String, List<FoxScrollData>> data = Maps.newLinkedHashMap();
        for (int i = 0; i < dimensionCount; i++) {
            String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_ENTRIES) {
                throw new io.netty.handler.codec.DecoderException(
                        "fox_scroll: entry count out of range: " + size);
            }
            List<FoxScrollData> scrollData = Lists.newArrayListWithCapacity(size);
            for (int j = 0; j < size; j++) {
                scrollData.add(FoxScrollData.decode(buf));
            }
            data.put(dimension, scrollData);
        }
        return data;
    }

    /**
     * 服务端注册接收器（在主入口调用）。
     */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    byte action = buf.readByte();
                    if (action != SET_TRACK) {
                        return;
                    }
                    String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
                    BlockPos pos = buf.readBlockPos();
                    server.execute(() -> handleSetTrack(player, dimension, pos));
                });
    }

    private static void handleSetTrack(ServerPlayer player, String dimension, BlockPos pos) {
        ItemStack mainHand = player.getMainHandItem();
        // 上游同样检查物品类型：否则这个包能往任何物品的 NBT 里塞 TrackInfo，
        // 而那件物品的 tooltip / 追踪逻辑并不认识它（无害，但没有意义）
        if (!(mainHand.getItem() instanceof ItemFoxScroll)) {
            return;
        }
        ItemFoxScroll.setTrackInfo(mainHand, dimension, pos);
        // 物品 NBT 变了要主动同步：否则客户端手里那枚卷轴要等下一次背包全量同步
        // 才更新，表现是「点了追踪，但 tooltip 里没有坐标」
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * 界面里的一条记录：坐标 + 女仆（或墓碑）的名字 + 记录时间戳。
     *
     * <p>时间戳目前只传输、不显示（上游同款，界面里没用到）。保留它是为了让
     * 载荷与上游一致，将来要按时间排序 / 显示「多久以前」时不必改协议。</p>
     */
    public static final class FoxScrollData {
        private final BlockPos pos;
        private final Component name;
        private final long timestamp;

        public FoxScrollData(BlockPos pos, Component name, long timestamp) {
            this.pos = pos;
            this.name = name;
            this.timestamp = timestamp;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(this.pos);
            buf.writeComponent(this.name);
            buf.writeLong(this.timestamp);
        }

        public static FoxScrollData decode(FriendlyByteBuf buf) {
            return new FoxScrollData(buf.readBlockPos(), buf.readComponent(), buf.readLong());
        }

        public BlockPos getPos() {
            return pos;
        }

        public Component getName() {
            return name;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}


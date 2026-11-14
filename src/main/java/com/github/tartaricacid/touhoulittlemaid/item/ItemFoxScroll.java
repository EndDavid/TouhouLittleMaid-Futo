package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.network.FoxScrollPacket;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 狐狸卷轴（红 / 白两枚同款物品），逐字移植自上游 {@code item/ItemFoxScroll}。
 *
 * <h2>它解决的问题</h2>
 * <p>女仆所在的区块没加载时，实体根本不在内存里：小号召不回来，仆从铃也只有一个
 * 「她大概在那边的某个坐标」。卷轴把 {@code MaidWorldData} 里记下的<b>记录</b>
 * 摊开给玩家看 —— 维度、坐标、直线距离，并允许把其中一条「钉」在手上，
 * 之后世界里会一直画着指向它的浮标（见 {@code client/render/ServantBellTracker}）。</p>
 *
 * <h2>红卷轴与白卷轴</h2>
 * <ul>
 *     <li>红：{@code MaidWorldData#getPlayerMaidInfos} —— 还活着的女仆；</li>
 *     <li>白：{@code MaidWorldData#getPlayerMaidTombstones} —— 墓碑（女仆死后的遗物盒）。</li>
 * </ul>
 * <p>两者只有数据来源与进度判据不同，界面、追踪、NBT 都是同一套，所以是同一个类。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>上游用 {@code NetworkHandler.sendToClientPlayer(msg, player)}；这里换成
 *         {@link FoxScrollPacket#sendData}，直接 {@code ServerPlayNetworking.send}。</li>
 *     <li>上游的「钉坐标」走独立包 {@code SetScrollData}；本移植版把它并进
 *         {@link FoxScrollPacket} 的同一个通道，用动作码区分（与神社庭灯、仆从铃一致）。</li>
 * </ul>
 */
public class ItemFoxScroll extends Item {
    private static final String TRACK_INFO = "TrackInfo";
    private static final String TRACK_DIMENSION = "Dimension";
    private static final String TRACK_POSITION = "Position";

    public ItemFoxScroll() {
        super(new FabricItemSettings().tab(InitItems.MAIN_TAB).stacksTo(1));
    }

    /**
     * 这枚卷轴是否被「钉」过坐标。
     *
     * <p>判空要连 {@code TrackInfo} 一起查：{@code getCompound} 对不存在的键
     * 返回<b>空 CompoundTag</b> 而不是 {@code null}，所以只判断 {@code hasTag()}
     * 会把「只写了别的 NBT」的卷轴也当成已追踪。</p>
     */
    public static boolean hasTrackInfo(ItemStack scroll) {
        CompoundTag tag = scroll.getTag();
        return tag != null && !tag.getCompound(TRACK_INFO).isEmpty();
    }

    /**
     * 把一条追踪坐标写进卷轴。服务端在收到 {@code SET_TRACK} 时调用。
     */
    public static void setTrackInfo(ItemStack scroll, String dimension, BlockPos pos) {
        CompoundTag tag = scroll.getOrCreateTagElement(TRACK_INFO);
        tag.putString(TRACK_DIMENSION, dimension);
        tag.put(TRACK_POSITION, NbtUtils.writeBlockPos(pos));
    }

    /**
     * 读出钉住的坐标（维度 + 方块坐标）。
     *
     * <p>返回 {@code null} 表示这枚卷轴没钉过任何东西 —— 追踪渲染器据此决定不画。</p>
     */
    @Nullable
    public static Pair<String, BlockPos> getTrackInfo(ItemStack scroll) {
        if (!hasTrackInfo(scroll)) {
            return null;
        }
        CompoundTag tag = scroll.getTag().getCompound(TRACK_INFO);
        if (!tag.contains(TRACK_DIMENSION, Tag.TAG_STRING)
                || !tag.contains(TRACK_POSITION, Tag.TAG_COMPOUND)) {
            // 有 TrackInfo 但字段不全：可能是别的版本 / 手改的存档，当成没钉过
            return null;
        }
        return Pair.of(tag.getString(TRACK_DIMENSION),
                NbtUtils.readBlockPos(tag.getCompound(TRACK_POSITION)));
    }

    /**
     * 右键使用：把名下的记录整理成「维度 → 坐标列表」发给客户端开界面。
     *
     * <p><b>只在服务端执行。</b>记录本身存在 {@code MaidWorldData}（仅服务端可得），
     * 客户端拿不到；而「打开界面」这个动作必须由客户端做，所以这段逻辑是
     * 「服务端收集 + 发包，客户端收包开屏」的一次单向流程。</p>
     *
     * <p>副手使用直接忽略：界面会盖住屏幕，同一时刻也只能看一份数据，
     * 没有理由让副手触发第二次。</p>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide || hand != InteractionHand.MAIN_HAND) {
            return super.use(level, player, hand);
        }
        ItemStack item = player.getMainHandItem();
        MaidWorldData maidWorldData = MaidWorldData.get(level);
        if (maidWorldData == null) {
            return super.use(level, player, hand);
        }

        List<MaidInfo> maidInfos = Collections.emptyList();
        if (item.getItem() == InitItems.RED_FOX_SCROLL) {
            List<MaidInfo> infos = maidWorldData.getPlayerMaidInfos(player);
            if (infos != null) {
                maidInfos = infos;
            }
        } else if (item.getItem() == InitItems.WHITE_FOX_SCROLL) {
            List<MaidInfo> infos = maidWorldData.getPlayerMaidTombstones(player);
            if (infos != null) {
                maidInfos = infos;
            }
        }

        // LinkedHashMap：维度按钮按服务端记录顺序排列。上游用 HashMap，
        // 每次打开的按钮顺序都可能不同，找同一个维度要重新扫一遍。
        Map<String, List<FoxScrollPacket.FoxScrollData>> data = Maps.newLinkedHashMap();
        for (MaidInfo info : maidInfos) {
            List<FoxScrollPacket.FoxScrollData> scrollData =
                    data.computeIfAbsent(info.getDimension(), dim -> Lists.newArrayList());
            scrollData.add(new FoxScrollPacket.FoxScrollData(
                    info.getChunkPos(), info.getName(), info.getTimestamp()));
        }
        if (player instanceof ServerPlayer serverPlayer) {
            FoxScrollPacket.sendData(serverPlayer, data);
            if (item.getItem() == InitItems.RED_FOX_SCROLL) {
                InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_RED_FOX_SCROLL);
            } else if (item.getItem() == InitItems.WHITE_FOX_SCROLL) {
                InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_WHITE_FOX_SCROLL);
            }
        }
        return InteractionResultHolder.success(item);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components,
                                TooltipFlag isAdvanced) {
        Pair<String, BlockPos> info = getTrackInfo(stack);
        if (info != null) {
            components.add(new TranslatableComponent(
                    "tooltips.touhou_little_maid.fox_scroll.dimension",
                    info.getFirst()).withStyle(ChatFormatting.GRAY));
            components.add(new TranslatableComponent(
                    "tooltips.touhou_little_maid.fox_scroll.position",
                    info.getSecond().toShortString()).withStyle(ChatFormatting.GRAY));
        }
        if (stack.getItem() == InitItems.RED_FOX_SCROLL) {
            components.add(new TranslatableComponent(
                    "tooltips.touhou_little_maid.fox_scroll.red").withStyle(ChatFormatting.GRAY));
        } else if (stack.getItem() == InitItems.WHITE_FOX_SCROLL) {
            components.add(new TranslatableComponent(
                    "tooltips.touhou_little_maid.fox_scroll.white").withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, level, components, isAdvanced);
    }
}


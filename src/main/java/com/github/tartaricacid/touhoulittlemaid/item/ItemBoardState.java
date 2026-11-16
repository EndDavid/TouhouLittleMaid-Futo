package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.inventory.tooltip.BoardStateTooltip;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 残局道具，移植自上游 1.20.1 的 {@code item/ItemBoardState}。
 *
 * <p>三个物品（{@code gomoku_board_state} / {@code cchess_board_state} /
 * {@code wchess_board_state}）共用这个类，差异只在「棋局数据怎么解释」——
 * 那一步在客户端 tooltip 里按物品判断。</p>
 *
 * <h2>它没有创造模式物品栏</h2>
 * <p>与上游一致：残局道具<b>只能从战利品里拿到</b>（要塞图书馆与制图师小屋的箱子，
 * 见 {@code event/BoardStateLootEvent}）。没写 {@code tab(...)} 的后果是
 * 「搜索」页里能搜到、但物品栏里看不到 —— 上游就是这样。</p>
 *
 * <h2>为什么 shift 判断要绕一圈</h2>
 * <p>{@code Screen.hasShiftDown()} 是<b>客户端类</b>。这个物品类在服务端也会被加载，
 * 直接在方法体里引用它会让专用服务器在启动时报
 * {@code Cannot load class net.minecraft.client.gui.screens.Screen in environment type SERVER}。
 * 沿用 {@code ItemChair} 里已经验证过的做法：把调用塞进一个只在客户端才初始化的嵌套类。</p>
 */
public class ItemBoardState extends Item {
    public static final String DATA_TAG = "BoardStateData";
    public static final String DESC_TAG = "BoardStateDesc";
    public static final String AUTHOR_TAG = "BoardStateAuthor";

    public ItemBoardState() {
        super(new Item.Properties());
    }

    public static void setState(ItemStack stack, String data, String desc, String author) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(DATA_TAG, data);
        tag.putString(DESC_TAG, desc);
        tag.putString(AUTHOR_TAG, author);
    }

    /**
     * 读出 {@code [data, descKey, author]}；没有数据时返回 {@code null}。
     */
    @Nullable
    public static String[] getState(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return null;
        }
        if (!tag.contains(DATA_TAG, Tag.TAG_STRING)) {
            return null;
        }
        return new String[]{
                tag.getString(DATA_TAG),
                tag.getString(DESC_TAG),
                tag.getString(AUTHOR_TAG)
        };
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        if (!ShiftKey.isShiftDown()) {
            return Optional.empty();
        }
        String[] state = getState(stack);
        if (state == null || StringUtils.isBlank(state[0])) {
            return Optional.empty();
        }
        if (stack.is(InitItems.GOMOKU_BOARD_STATE)) {
            return Optional.of(BoardStateTooltip.ofGomoku(state[0]));
        }
        if (stack.is(InitItems.CCHESS_BOARD_STATE)) {
            return Optional.of(BoardStateTooltip.ofXiangqi(state[0]));
        }
        if (stack.is(InitItems.WCHESS_BOARD_STATE)) {
            return Optional.of(BoardStateTooltip.ofChess(state[0]));
        }
        return Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String[] state = getState(stack);
        if (state == null) {
            tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.board_state.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        String descKey = state[1];
        if (StringUtils.isNotBlank(descKey)) {
            tooltip.add(new TranslatableComponent(descKey).withStyle(ChatFormatting.GRAY));
        }

        String author = state[2];
        if (StringUtils.isNotBlank(author)) {
            tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.board_state.author", author)
                    .withStyle(ChatFormatting.GRAY));
        }

        if (!ShiftKey.isShiftDown()) {
            tooltip.add(new TranslatableComponent("board_state.touhou_little_maid.show_picture")
                    .withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
        }
    }

    /**
     * {@code Screen.hasShiftDown()} 是客户端类，所以包一层：
     * 服务端环境下这个嵌套类永远不会被初始化，也就碰不到那个类引用。
     */
    private static final class ShiftKey {
        private static final ShiftKey INSTANCE = new ShiftKey();

        private static boolean isShiftDown() {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT && INSTANCE.shift();
        }

        private boolean shift() {
            return net.minecraft.client.gui.screens.Screen.hasShiftDown();
        }
    }
}


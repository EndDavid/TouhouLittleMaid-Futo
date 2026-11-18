package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.other.WirelessIOContainer;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * 隙间（无线 I/O 饰品），移植自上游 {@code item/ItemWirelessIO}。
 *
 * <p>这件物品同时是<b>可配置的饰品</b>和<b>手持工具</b>：</p>
 * <ul>
 *     <li>Shift 右击箱子 → 把坐标写进 NBT（{@code BindingPos}）；</li>
 *     <li>右击空气 → 打开配置界面（I/O 方向、黑白名单、9 格过滤清单）；</li>
 *     <li>放进女仆饰品栏后，女仆每 100 tick 与那个箱子之间搬一次物品
 *         （逻辑在 {@code WirelessIOBauble}）。</li>
 * </ul>
 *
 * <h2>数据布局（与上游逐字一致，存档可互换）</h2>
 * <ul>
 *     <li>{@code ItemIOMode}（boolean）—— {@code true} = 女仆 → 箱子；缺省 {@code false} = 箱子 → 女仆；</li>
 *     <li>{@code ItemFilterMode}（boolean）—— {@code true} = 黑名单；缺省 {@code false} = 白名单；</li>
 *     <li>{@code ItemFilterList}（CompoundTag）—— 9 格过滤清单，{@code ItemStackHandler} 的序列化形式；</li>
 *     <li>{@code BindingPos}（CompoundTag）—— 绑定的箱子坐标；</li>
 *     <li>{@code SlotConfigData}（byte[]）—— 38 个槽位的「冻结」标记，见 {@code WirelessIOBauble}。</li>
 * </ul>
 *
 * <h2>1.18.2 适配</h2>
 * <ul>
 *     <li>{@code MenuProvider} + {@code NetworkHooks.openScreen}（Forge）→ Fabric 的
 *         {@link ExtendedScreenHandlerFactory} + {@code ServerPlayer#openMenu}。
 *         界面除了菜单 id 之外还要带「手上这件物品」，扩展类型正好用来塞这段数据。</li>
 *     <li>{@code I18n.get}（客户端类）→ {@link TranslatableComponent}。
 *         上游把这个调用放在公共类里是安全的（服务端不会走到 {@code appendHoverText}），
 *         但本移植版一直避免在公共类里静态引用客户端类型，改成组件后两边都干净。</li>
 * </ul>
 */
public class ItemWirelessIO extends Item implements ExtendedScreenHandlerFactory {
    /**
     * 过滤清单格数，与界面上的 3×3 一致。
     */
    private static final int FILTER_LIST_SIZE = 9;
    private static final String FILTER_LIST_TAG = "ItemFilterList";
    private static final String FILTER_MODE_TAG = "ItemFilterMode";
    private static final String IO_MODE_TAG = "ItemIOMode";
    private static final String BINDING_POS = "BindingPos";
    private static final String SLOT_CONFIG_TAG = "SlotConfigData";
    private static final String TOOLTIPS_PREFIX = "§a▍ §7";

    public ItemWirelessIO() {
        super(new Properties().tab(InitItems.MAIN_TAB).stacksTo(1));
    }

    // ------------------------------------------------------------------ I/O 方向

    public static void setMode(ItemStack stack, boolean maidToChest) {
        if (stack.getItem() == InitItems.WIRELESS_IO) {
            stack.getOrCreateTag().putBoolean(IO_MODE_TAG, maidToChest);
        }
    }

    public static boolean isMaidToChest(ItemStack stack) {
        if (stack.getItem() == InitItems.WIRELESS_IO && stack.hasTag()) {
            CompoundTag nbt = stack.getTag();
            return Objects.requireNonNull(nbt).getBoolean(IO_MODE_TAG);
        }
        return false;
    }

    // ------------------------------------------------------------------ 过滤模式

    public static void setFilterMode(ItemStack stack, boolean isBlacklist) {
        if (stack.getItem() == InitItems.WIRELESS_IO) {
            stack.getOrCreateTag().putBoolean(FILTER_MODE_TAG, isBlacklist);
        }
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (stack.getItem() == InitItems.WIRELESS_IO && stack.hasTag()) {
            CompoundTag nbt = stack.getTag();
            return Objects.requireNonNull(nbt).getBoolean(FILTER_MODE_TAG);
        }
        return false;
    }

    // ------------------------------------------------------------------ 过滤清单

    /**
     * 读出过滤清单。<b>每次都新建</b>一个 handler（与上游一致）——
     * 它是物品 NBT 的一份快照，改完必须用 {@link #setFilterList} 写回。
     */
    public static ItemStackHandler getFilterList(ItemStack stack) {
        WirelessIOHandler handler = new WirelessIOHandler(FILTER_LIST_SIZE);
        if (stack.getItem() == InitItems.WIRELESS_IO) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains(FILTER_LIST_TAG, Tag.TAG_COMPOUND)) {
                handler.deserializeNBT(tag.getCompound(FILTER_LIST_TAG));
            }
        }
        return handler;
    }

    public static void setFilterList(ItemStack stack, ItemStackHandler itemStackHandler) {
        if (stack.getItem() == InitItems.WIRELESS_IO) {
            stack.getOrCreateTag().put(FILTER_LIST_TAG, itemStackHandler.serializeNBT());
        }
    }

    // ------------------------------------------------------------------ 绑定坐标

    @Nullable
    public static BlockPos getBindingPos(ItemStack stack) {
        if (stack.getItem() == InitItems.WIRELESS_IO) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains(BINDING_POS, Tag.TAG_COMPOUND)) {
                return NbtUtils.readBlockPos(tag.getCompound(BINDING_POS));
            }
        }
        return null;
    }

    public static void setBindingPos(ItemStack stack, BlockPos pos) {
        if (stack.getItem() == InitItems.WIRELESS_IO) {
            stack.getOrCreateTag().put(BINDING_POS, NbtUtils.writeBlockPos(pos));
        }
    }

    // ------------------------------------------------------------------ 槽位配置

    public static void setSlotConfig(ItemStack stack, byte[] config) {
        if (stack.getItem() == InitItems.WIRELESS_IO) {
            stack.getOrCreateTag().put(SLOT_CONFIG_TAG, new ByteArrayTag(config));
        }
    }

    @Nullable
    public static byte[] getSlotConfig(ItemStack stack) {
        if (stack.getItem() == InitItems.WIRELESS_IO) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains(SLOT_CONFIG_TAG, Tag.TAG_BYTE_ARRAY)) {
                return tag.getByteArray(SLOT_CONFIG_TAG);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 交互

    /**
     * 右击方块：是箱子就记下坐标。
     *
     * <p>只认主手 —— 副手右击同时也会触发主手的 {@code useOn}，
     * 不挡掉的话两个手会各绑一次，绑定结果取决于遍历顺序。</p>
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level worldIn = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();
        BlockEntity te = worldIn.getBlockEntity(pos);

        if (hand != InteractionHand.MAIN_HAND) {
            return super.useOn(context);
        }
        if (player == null) {
            return super.useOn(context);
        }

        for (IChestType type : ChestManager.getAllChestTypes()) {
            if (!type.isChest(te)) {
                continue;
            }
            if (type.canOpenByPlayer(te, player)) {
                ItemStack stack = player.getMainHandItem();
                setBindingPos(stack, pos);
                return InteractionResult.sidedSuccess(worldIn.isClientSide);
            }
        }
        return super.useOn(context);
    }

    /**
     * 右击空气：打开配置界面。
     *
     * <p>只有服务端能开界面；客户端这边返回 {@code success} 只是为了让手臂摆一下。
     * 用 {@code instanceof ServerPlayer} 而不是 {@code !level.isClientSide}，
     * 是为了让编译器直接把 {@code openMenu} 的接收者收窄成服务端玩家类型。</p>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn) {
        if (handIn == InteractionHand.MAIN_HAND && playerIn instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this);
            return InteractionResultHolder.success(playerIn.getMainHandItem());
        }
        return super.use(worldIn, playerIn, handIn);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> tooltip, TooltipFlag flagIn) {
        boolean maidToChest = isMaidToChest(stack);
        boolean isBlacklist = isBlacklist(stack);
        BlockPos pos = getBindingPos(stack);

        String ioModeKey = maidToChest
                ? "tooltips.touhou_little_maid.wireless_io.io_mode.input"
                : "tooltips.touhou_little_maid.wireless_io.io_mode.output";
        String filterModeKey = isBlacklist
                ? "tooltips.touhou_little_maid.wireless_io.filter_mode.blacklist"
                : "tooltips.touhou_little_maid.wireless_io.filter_mode.whitelist";

        tooltip.add(withPrefix(new TranslatableComponent(ioModeKey)));
        tooltip.add(withPrefix(new TranslatableComponent(filterModeKey)));
        if (pos != null) {
            tooltip.add(withPrefix(new TranslatableComponent(
                    "tooltips.touhou_little_maid.wireless_io.binding_pos.has",
                    pos.getX(), pos.getY(), pos.getZ())));
        } else {
            tooltip.add(withPrefix(new TranslatableComponent(
                    "tooltips.touhou_little_maid.wireless_io.binding_pos.none")));
        }
        tooltip.add(new TextComponent(" "));
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.wireless_io.usage.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.wireless_io.usage.2")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * 上游用字符串拼接把「§a▍ §7」贴在译文前面。这里改成组件拼接，
     * 效果完全相同：前缀本身携带格式码，{@code §7} 会把后面的文字染成灰色。
     */
    private static Component withPrefix(Component body) {
        return new TextComponent(TOOLTIPS_PREFIX).append(body);
    }

    @Override
    public Component getDisplayName() {
        return new TranslatableComponent("item.touhou_little_maid.wireless_io");
    }

    // ------------------------------------------------------------------ 界面

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WirelessIOContainer(id, inventory, player.getMainHandItem());
    }

    /**
     * 客户端要靠这段数据重建「手上这件物品」的 ItemStack —— 界面上的开关状态、
     * 过滤清单内容都是从它读的。
     *
     * <p>注意这是<b>一份拷贝</b>：服务端之后改 NBT 不会自动同步过去，
     * 所以「槽位设置」界面确认后由服务端<b>重新打开一次菜单</b>，
     * 让客户端拿到新的拷贝（与上游相同）。</p>
     */
    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        buf.writeItem(player.getMainHandItem());
    }

    /**
     * 过滤清单的 9 个格子，每格只放一件。
     */
    private static class WirelessIOHandler extends ItemStackHandler {
        private WirelessIOHandler(int size) {
            super(size);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }
}


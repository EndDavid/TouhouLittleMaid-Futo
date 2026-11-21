package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.other.PicnicBasketContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.tooltip.ItemContainerTooltip;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * 野餐篮，移植自上游 {@code item/ItemPicnicBasket}。
 *
 * <p>它同时是三种东西：</p>
 * <ol>
 *     <li><b>方块物品</b> —— 右击地面放下野餐垫，篮里那 9 格食物一起搬进中心方块；</li>
 *     <li><b>一个 9 格容器</b> —— 右击空气打开界面，随时塞/取食物；</li>
 *     <li><b>一个 tooltip 组件</b> —— 鼠标悬停时把里面的食物画成一排图标（{@link ItemContainerTooltip}）。</li>
 * </ol>
 *
 * <p>食物存在物品 NBT 的 {@value #PICNIC_BASKET_TAG} 子标签里，格式就是
 * {@code ItemStackHandler#serializeNBT()} 的输出 —— 与上游逐字相同，
 * 所以野餐篮和已经放下的野餐垫用的是同一份序列化格式，来回搬不会丢东西。</p>
 *
 * <h2>与上游的差异</h2>
 * <p>打开界面走 Fabric 的 {@link ExtendedScreenHandlerFactory} +
 * {@code ServerPlayer#openMenu}（上游是 Forge 的 {@code NetworkHooks.openGui}），
 * 两者语义一致：都是「带一段自定义数据打开菜单」，这里那段数据是<b>主手物品</b>。
 * 物品图标由 {@code PicnicBasketItemRenderer} 负责（上游用 Forge 的
 * {@code IItemRenderProperties}）。</p>
 */
public class ItemPicnicBasket extends BlockItem implements ExtendedScreenHandlerFactory {
    private static final int PICNIC_BASKET_SIZE = 9;
    private static final String PICNIC_BASKET_TAG = "PicnicBasketContainer";

    public ItemPicnicBasket(Block block) {
        // 与 InitItems 里其它方块物品一致，用 FabricItemSettings；
        // 堆叠数 1：篮子里那 9 格是写在自己 NBT 上的，堆叠会让同一叠里的物品互相覆盖。
        super(block, new net.fabricmc.fabric.api.item.v1.FabricItemSettings()
                .tab(InitItems.MAIN_TAB).stacksTo(1));
    }

    /**
     * 读出野餐篮里的 9 格物品（一份<b>拷贝</b>，改完要 {@link #setContainer} 写回）。
     */
    public static ItemStackHandler getContainer(ItemStack stack) {
        ItemStackHandler handler = new ItemStackHandler(PICNIC_BASKET_SIZE);
        if (stack.getItem() == InitItems.PICNIC_BASKET) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains(PICNIC_BASKET_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                handler.deserializeNBT(tag.getCompound(PICNIC_BASKET_TAG));
            }
        }
        return handler;
    }

    public static void setContainer(ItemStack stack, ItemStackHandler itemStackHandler) {
        if (stack.getItem() == InitItems.PICNIC_BASKET) {
            stack.getOrCreateTag().put(PICNIC_BASKET_TAG, itemStackHandler.serializeNBT());
        }
    }

    /**
     * 右击空气：打开容器界面。
     *
     * <p>{@code BlockItem} 的 {@code use} 只有在没命中方块时才会被调用，
     * 所以「右击地面 = 放置」「右击空气 = 开界面」天然分开，不需要额外判断朝向。</p>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn) {
        if (handIn == InteractionHand.MAIN_HAND && playerIn instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this);
            return InteractionResultHolder.success(playerIn.getMainHandItem());
        }
        return super.use(worldIn, playerIn, handIn);
    }

    /**
     * 悬停时画出篮子里的东西。
     *
     * <p>1.18.2 的 {@code getTooltipImage} 返回值类型与 1.20 一样是
     * {@code Optional<TooltipComponent>}，差别只在「组件 → 渲染器」那张表怎么注册
     * （Fabric 走 {@code TooltipComponentCallback}，见 {@code InitClientTooltip}）。</p>
     */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new ItemContainerTooltip(getContainer(stack)));
    }

    /**
     * 名字取自物品而不是方块。
     *
     * <p>{@code BlockItem} 默认返回 {@code block.touhou_little_maid.picnic_mat}，
     * 那样物品会叫「野餐垫」；上游在这里显式改回 {@code item.…picnic_basket}。</p>
     */
    @Override
    public String getDescriptionId() {
        return "item.touhou_little_maid.picnic_basket";
    }

    @Override
    public Component getDisplayName() {
        return new TranslatableComponent(this.getDescriptionId());
    }

    // ------------------------------------------------------------------ 界面

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PicnicBasketContainer(id, inventory, player.getMainHandItem());
    }

    /**
     * 客户端要靠这段数据重建「手上这个野餐篮」的 ItemStack —— 9 格里有什么是从它读的。
     *
     * <p>和无线 IO 一样，这是<b>一份拷贝</b>：服务端之后改 NBT 不会自动同步，
     * 所以界面里每次点击都由 {@code PicnicBasketContainer#clicked} 写回物品。
     * </p>
     */
    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        buf.writeItem(player.getMainHandItem());
    }
}

package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityModelSwitcher;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 模型切换器的方块物品，移植自上游 {@code item/ItemModelSwitcher}。
 *
 * <p>它比普通 {@code BlockItem} 多两件事：</p>
 * <ol>
 *     <li><b>右击女仆 = 绑定</b>（{@link #interactLivingEntity}）——
 *         把她的 UUID 写进物品 NBT，之后放下方块、来红石脉冲时就能认出是哪只女仆；</li>
 *     <li><b>与方块实体互搬数据</b> —— 放下时 {@link #itemStackToTileEntity}，
 *         拆掉时 {@link #tileEntityToItemStack}，这样绑定信息与模式列表跟着物品走。</li>
 * </ol>
 *
 * <h2>与上游的一处差异</h2>
 * <p>上游把数据存在物品 NBT 的 {@code StorageData/ForgeData/...} 三层下：
 * 因为 Forge 的 {@code BlockEntity#saveWithoutMetadata()} 会自动把
 * {@code getPersistentData()} 包进一个 {@code ForgeData} 子标签。1.18.2 没有这层包装，
 * 所以本移植版直接是 {@code StorageData/<方块实体自己的键>}（一层少）。</p>
 *
 * <p><b>存档兼容性说明</b>：本模组是新移植版，没有历史物品；层数少一层只是让
 * 「物品里的键」与「方块实体 NBT 里的键」完全同名，反而更好读。</p>
 */
public class ItemModelSwitcher extends BlockItem {
    private static final String STORAGE_DATA_TAG = "StorageData";

    public ItemModelSwitcher() {
        // 进主创造模式物品栏：本移植版的物品栏是靠 fillItemCategory 遍历注册表填的，
        // 只有设了 tab(MAIN_TAB) 的才会出现（上游用显式的 accept 列表，效果相同）。
        super(InitBlocks.MODEL_SWITCHER, new net.fabricmc.fabric.api.item.v1.FabricItemSettings()
                .tab(InitItems.MAIN_TAB)
                .stacksTo(1));
    }

    /**
     * 方块实体 → 物品：把整份 NBT 塞进 {@code StorageData}。
     */
    public static ItemStack tileEntityToItemStack(TileEntityModelSwitcher switcher) {
        ItemStack stack = InitItems.MODEL_SWITCHER.getDefaultInstance();
        stack.getOrCreateTag().put(STORAGE_DATA_TAG, switcher.saveWithoutMetadata());
        return stack;
    }

    /**
     * 物品 → 方块实体：把 {@code StorageData} 里那份 NBT 读回去。
     *
     * <p>只有真的存过东西才读 —— 创造模式拿到的空白方块物品里没有这个键，
     * 读它会得到一个全空的 tag，从而把 {@code load} 里的默认值覆盖掉。</p>
     */
    public static void itemStackToTileEntity(ItemStack stack, TileEntityModelSwitcher switcher) {
        CompoundTag tag = stack.getTagElement(STORAGE_DATA_TAG);
        if (tag != null && !tag.isEmpty()) {
            switcher.load(tag);
        }
    }

    /**
     * 右击女仆：把她的 UUID 写进物品。
     *
     * <p>只写 UUID，不写别的 —— 模式列表是放下方块之后在界面里配的。</p>
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (target instanceof EntityMaid maid) {
            CompoundTag tag = stack.getOrCreateTagElement(STORAGE_DATA_TAG);
            tag.put(TileEntityModelSwitcher.ENTITY_UUID, NbtUtils.createUUID(maid.getUUID()));
            return InteractionResult.SUCCESS;
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    private boolean hasMaidInfo(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(STORAGE_DATA_TAG);
        return tag != null && tag.contains(TileEntityModelSwitcher.ENTITY_UUID, Tag.TAG_INT_ARRAY);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (this.hasMaidInfo(stack)) {
            tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.model_switcher.bounded")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(new TranslatableComponent("gui.touhou_little_maid.model_switcher.uuid.empty")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }
}


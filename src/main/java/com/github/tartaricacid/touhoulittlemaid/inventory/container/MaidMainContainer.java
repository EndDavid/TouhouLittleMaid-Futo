package com.github.tartaricacid.touhoulittlemaid.inventory.container;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆主界面容器，移植自上游 {@code inventory/container/MaidMainContainer}。
 *
 * <p>所有槽位坐标都直接取自上游 <b>1.18.2</b> 的源码，配合
 * {@code textures/gui/maid_gui_main.png}（256x256）使用：</p>
 * <pre>
 * 护甲 2x2   (94,37) (114,37)
 *            (94,57) (114,57)      —— handler 下标 3,2 / 1,0（脚/腿/胸/头 反序）
 * 主手       (87,77)
 * 副手       (121,77)
 * 背包第 1 行 (143,37)  6 格
 * 背包后续行  (143, 59 / 82 / 100 / 123 / 141)  各 6 格
 * 玩家物品栏  (88,174) 3 行
 * 快捷栏     (88,232)
 * </pre>
 *
 * <p><b>与上游的差异：</b>上游为每种背包各写了一个 {@code *BackpackContainer} 子类，
 * 每个子类硬编码自己的行数与 Y 坐标。这里合并成一个类，按
 * {@code IMaidBackpack#getAvailableMaxContainerIndex()} 决定补几行，
 * Y 坐标沿用上游那张行距表（37 / 59 / 82 / 100 / 123 / 141），
 * 因为背包贴图本身就是这样分段绘制的。</p>
 */
public class MaidMainContainer extends AbstractMaidContainer {
    /**
     * 背包各行的 Y 坐标（取自上游各 {@code *BackpackContainer}）。
     */
    private static final int[] ROW_Y = {37, 59, 82, 100, 123, 141};
    /**
     * 背包每行 6 格，起始 X = 143，间距 18.
     */
    private static final int BACKPACK_X = 143;
    private static final int BACKPACK_COLUMNS = 6;

    /**
     * 玩家物品栏原点（上游 {@code AbstractMaidContainer#addPlayerInv}）。
     */

    /**
     * GUI 固定尺寸（对应 {@code maid_gui_main.png}）。
     */
    public static final int GUI_WIDTH = 256;
    public static final int GUI_HEIGHT = 256;

    /**
     * 与 {@code EntityArmorInvWrapper} 的 0=脚 / 1=腿 / 2=胸 / 3=头 对应的界面顺序。
     */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /**
     * 模组自带的「空槽提示」贴图：主手槽与背包第 6 格。
     *
     * <p><b>为什么这两张不能走 {@code Slot#getNoItemIcon()}：</b>
     * 原版 {@code AbstractContainerScreen#renderSlot} 画提示图标的写法是
     * {@code minecraft.getTextureAtlas(atlas).apply(sprite)} ——
     * 也就是贴图必须<b>已经并入某张图集</b>，图集里没有就只会画成紫黑格。
     * Forge 用 {@code TextureStitchEvent.Pre} 把模组贴图塞进 {@code blocks} 图集，
     * 而 1.18.2 的 Fabric 既没有等价事件，也还没有图集定义 JSON（那是 1.19.3 才有的），
     * 所以这两张图由 {@code MaidMainScreen#drawManualSlotHints} 按贴图路径直接绘制。</p>
     *
     * <p>路径写全 {@code textures/...png} —— 直接绘制的接口吃到的是完整贴图路径，
     * 不是图集里的 sprite 名。</p>
     */
    public static final ResourceLocation EMPTY_MAINHAND_SLOT =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/slot/empty_mainhand_slot.png");
    public static final ResourceLocation EMPTY_BACK_SHOW_SLOT =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/slot/empty_back_show_slot.png");

    private final int backpackRows;

    public MaidMainContainer(MenuType<MaidMainContainer> type, int id, Inventory inventory, int entityId) {
        super(type, id, inventory, entityId);

        EntityMaid foundMaid = this.maid;
        int capacity = foundMaid == null ? 6 : foundMaid.getMaidBackpackType().getAvailableMaxContainerIndex();
        this.backpackRows = Math.max(1, Math.min(ROW_Y.length, (capacity + BACKPACK_COLUMNS - 1) / BACKPACK_COLUMNS));

        // 玩家物品栏排在最前（36 格）—— quickMoveStack 依赖这个下标分界
        this.addPlayerInv(inventory, PLAYER_INV_X, PLAYER_INV_Y);

        if (foundMaid != null) {
            this.addMaidArmorInv(foundMaid);
            this.addMaidHandInv(foundMaid);
            this.addMaidBauble(foundMaid);
            this.addBackpackInv(foundMaid, capacity);
        }
    }

    private void addMaidArmorInv(EntityMaid maid) {
        IItemHandler armor = maid.getArmorInvWrapper();
        for (int i = 0; i < 4; i++) {
            EquipmentSlot equipmentSlot = ARMOR_SLOTS[i];
            int x = 94 + 20 * (i % 2);
            int y = 37 + 20 * (i / 2);
            // EntityArmorInvWrapper 的顺序是 脚/腿/胸/头，界面是 头/胸/腿/脚，因此取反
            int handlerIndex = 3 - i;
            this.addSlot(new SlotItemHandler(armor, handlerIndex, x, y) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    // 1.18.2 没有 ItemStack#canEquip，改用等价的静态反查
                    return !stack.isEmpty()
                           && net.minecraft.world.entity.LivingEntity.getEquipmentSlotForItem(stack) == equipmentSlot
                           && stack.getItem().canFitInsideContainerItems();
                }

                @Override
                public boolean mayPickup(Player playerIn) {
                    ItemStack itemstack = this.getItem();
                    boolean curse = !itemstack.isEmpty() && !playerIn.isCreative()
                                    && EnchantmentHelper.hasBindingCurse(itemstack);
                    return !curse && super.mayPickup(playerIn);
                }

                @Override
                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    // 空护甲槽的「头盔 / 胸甲 / 护腿 / 靴子」提示图：直接用原版图集里的 sprite
                    return Pair.of(InventoryMenu.BLOCK_ATLAS, emptyArmorIcon(equipmentSlot));
                }
            });
        }
    }

    /**
     * 原版 blocks 图集里的空护甲槽 sprite（对应上游 {@code TEXTURE_EMPTY_SLOTS}）。
     *
     * <p>上游是按 {@code EquipmentSlot#getIndex()} 去查表的，这里改成按枚举分支，
     * 少一个「下标含义」的隐含约定。</p>
     */
    private static ResourceLocation emptyArmorIcon(EquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case CHEST:
                return InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS:
                return InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            default:
                return InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
        }
    }

    private void addMaidHandInv(EntityMaid maid) {
        IItemHandler hands = maid.getHandsInvWrapper();
        // 主手：模组自带贴图，由屏幕补画
        this.addSlot(new HintSlot(hands, 0, 87, 77, EMPTY_MAINHAND_SLOT));
        // 副手：原版盾牌 sprite
        this.addSlot(new SlotItemHandler(hands, 1, 121, 77) {
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });
    }

    /**
     * 女仆饰品栏 3x3（上游 {@code MaidMainContainer#addMaidBauble}）。
     *
     * <p>坐标逐格取自上游：{@code (86 + 18*j, 99 + 18*i)}，即从 (86,99) 起 3 行 3 列。
     * 这 9 格画在 {@code maid_gui_main.png} 的左下区域，与护甲 / 双手并排。</p>
     */
    private void addMaidBauble(EntityMaid maid) {
        com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler bauble =
                maid.getMaidBauble();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new SlotItemHandler(bauble, i * 3 + j, 86 + 18 * j, 99 + 18 * i));
            }
        }
    }

    private void addBackpackInv(EntityMaid maid, int capacity) {
        IItemHandler backpack = maid.getAvailableBackpackInv();
        int added = 0;
        for (int row = 0; row < this.backpackRows && added < capacity; row++) {
            for (int col = 0; col < BACKPACK_COLUMNS && added < capacity; col++) {
                int x = BACKPACK_X + 18 * col;
                int y = ROW_Y[row];
                // 背包第 6 格（上游 addMainDefaultInv 里 i == 5 的那一格）带提示图标
                if (added == 5) {
                    this.addSlot(new HintSlot(backpack, added, x, y, EMPTY_BACK_SHOW_SLOT));
                } else {
                    this.addSlot(new SlotItemHandler(backpack, added, x, y));
                }
                added++;
            }
        }
    }

    /**
     * 带「模组自有空槽提示贴图」的槽位。
     *
     * <p>原版自动绘制提示的入口是 {@code Slot#getNoItemIcon()}，但它要求贴图已经并入
     * 原版 {@code blocks} 图集（见 {@link #EMPTY_MAINHAND_SLOT} 的说明）。
     * 所以模组自带的贴图改由 {@code MaidMainScreen#drawManualSlotHints} 在槽位为空时直接绘制，
     * 本类只负责把「哪一格用哪张贴图」带出去。</p>
     */
    public static class HintSlot extends SlotItemHandler {
        @Nullable
        private final ResourceLocation hintTexture;

        public HintSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition,
                        @Nullable ResourceLocation hintTexture) {
            super(itemHandler, index, xPosition, yPosition);
            this.hintTexture = hintTexture;
        }

        @Nullable
        public ResourceLocation getHintTexture() {
            return this.hintTexture;
        }
    }

    /**
     * 背包行数（渲染层用来决定要画几行分隔与锁图标）。
     */
    /**
     * 背包各行的纵坐标（渲染层画遮罩与锁时要用，避免在渲染层重复硬编码）。
     */
    public static int[] getRowY() {
        return ROW_Y;
    }

    public int getBackpackRows() {
        return this.backpackRows;
    }

    /**
     * 当前女仆（渲染层取血量 / 好感度等）。
     */
    @Nullable
    @Override
    public EntityMaid getMaid() {
        return this.maid;
    }
}

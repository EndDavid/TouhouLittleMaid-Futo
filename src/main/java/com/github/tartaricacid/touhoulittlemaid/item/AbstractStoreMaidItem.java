package com.github.tartaricacid.touhoulittlemaid.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * 「内部存着一只女仆」的物品基类（上游 {@code item/AbstractStoreMaidItem} 的移植）。
 */
public abstract class AbstractStoreMaidItem extends Item {
    /**
     * 女仆数据的子标签名（上游同名字段）。
     */
    static final String MAID_INFO = "MaidInfo";
    /**
     * 女仆自定义名的 NBT 键。
     *
     * <p>由原版 {@code Entity#saveWithoutId} 写入（{@code if (hasCustomName()) putString("CustomName", ...)}），
     * 直接读即可。</p>
     */
    static final String CUSTOM_NAME = "CustomName";
    /**
     * 女仆模型 id 的 NBT 键。
     *
     * <p><b>必须与 {@code EntityMaid} 里写的一致</b>：本移植版的 {@code EntityMaid}
     * 在 {@code addAdditionalSaveData} 里写的是字面量 {@code "ModelId"}
     * （上游对应常量 {@code EntityMaid.MODEL_ID_TAG}，值相同）。
     * 这里不便为了一个字符串去改动那个 2900 行的实体类，所以单独定义一份。</p>
     */
    static final String MODEL_ID_TAG = "ModelId";

    protected AbstractStoreMaidItem(Properties properties) {
        super(properties);
    }

    /**
     * 这个物品里是否真的存着女仆数据。
     */
    public static boolean hasMaidData(ItemStack stack) {
        return stack.hasTag() && !Objects.requireNonNull(stack.getTag()).getCompound(MAID_INFO).isEmpty();
    }

    /**
     * 取出女仆数据的<b>副本引用</b>（没有数据时返回空标签，方便直接读键）。
     */
    public static CompoundTag getMaidData(ItemStack stack) {
        if (hasMaidData(stack)) {
            return Objects.requireNonNull(stack.getTag()).getCompound(MAID_INFO);
        }
        return new CompoundTag();
    }

    /**
     * 让提示框里画出一个三维女仆预览。
     *
     * <p>这一半 1.18.2 是有的（{@code Item#getTooltipImage} 自 1.17 起存在），
     * 真正缺的是「{@code TooltipComponent} → 客户端实现」那张注册表 ——
     * 原版那里只有一个硬编码的 {@code if}，见
     * {@code mixin/client/ClientTooltipComponentMixin} 的注释。</p>
     *
     * <p>只有当数据里确实带模型 id 时才返回组件：没有模型 id 就没有东西可画，
     * 返回空组件会让提示框多出一大块空白。</p>
     */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        CompoundTag maidData = getMaidData(stack);
        if (!maidData.contains(MODEL_ID_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        String customName = maidData.contains(CUSTOM_NAME, Tag.TAG_STRING)
                ? maidData.getString(CUSTOM_NAME) : "";
        return Optional.of(new com.github.tartaricacid.touhoulittlemaid.inventory.tooltip.ItemMaidTooltip(
                maidData.getString(MODEL_ID_TAG), customName));
    }
}

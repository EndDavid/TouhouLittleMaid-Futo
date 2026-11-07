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
 *
 * <p>照片（{@link ItemPhoto}）与胶片（上游 {@code ItemFilm}）都继承它，
 * 共同点是：NBT 里的 {@code MaidInfo} 子标签保存一份 {@code EntityMaid#saveWithoutId} 的结果。</p>
 *
 * <h2>与上游的差异</h2>
 *
 * <ol>
 *     <li><b>{@code onEntityItemUpdate} 没有对应钩子。</b>
 *         上游用这个 Forge 方法让掉落在地上的照片「发光 + 无敌 + 不掉进虚空」：
 *         <pre>
 *         entity.setGlowingTag(true);
 *         entity.setInvulnerable(true);
 *         if (y &lt; getMinBuildHeight()) { 拉住不往下掉 }
 *         </pre>
 *         其中「不被火烧 / 岩浆烧」这一点用 {@code Item.Properties#fireResistant()} 等价实现
 *         （见两个子类的构造函数）—— 这也正是语言文件里承诺的行为。
 *         剩下「发光」与「虚空保护」在 Fabric 侧需要每 tick 扫实体，代价与收益不成比例，
 *         暂不实现。</li>
 *     <li><b>三维预览要自己接一层。</b>
 *         上游靠 Forge 的 {@code registerTooltipComponentFactory} 把
 *         {@code ItemMaidTooltip} 对应到 {@code ClientMaidTooltip}。
 *         原版 1.18.2 没有那张注册表（{@code ClientTooltipComponent#create} 只是一个
 *         硬编码 {@code if}），Fabric 也没提供等价 API —— 但方法本身是静态可注入的，
 *         所以本移植版用 {@code mixin/client/ClientTooltipComponentMixin} 在 HEAD 处接上去，
 *         效果与上游的注册表一致（见 {@link #getTooltipImage} 与 {@code ClientMaidTooltip}）。</li>
 *     <li><b>YSM 兼容略过。</b>{@code YsmMaidInfo} 属于 YSM 联动，本移植版没有。</li>
 * </ol>
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

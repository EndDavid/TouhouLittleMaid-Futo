package com.github.tartaricacid.touhoulittlemaid.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * 「内部存着一只女仆」的物品基类（上游 {@code item/AbstractStoreMaidItem} 的移植）。
 *
 * <p>照片（{@link ItemPhoto}）与胶片（上游 {@code ItemFilm}）都继承它，
 * 共同点是：NBT 里的 {@code MaidInfo} 子标签保存一份 {@code EntityMaid#saveWithoutId} 的结果。</p>
 *
 * <h2>与上游的三处差异（都是因为 1.18.2 / Fabric 缺对应 API）</h2>
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
 *     <li><b>{@code getTooltipImage} 不再返回三维预览。</b>
 *         上游返回一个 {@code ItemMaidTooltip} 记录，再由 Forge 的
 *         {@code registerTooltipComponentFactory} 注册的 {@code ClientMaidTooltip}
 *         在提示框里画出女仆模型。Fabric 1.18.2 没有对应的提示框组件注册 API，
 *         所以这里不覆写 {@code getTooltipImage}（否则会因为没有工厂而取不到组件）。
 *         女仆名字改用一行普通文本显示，见 {@link ItemPhoto#appendHoverText}。</li>
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
}


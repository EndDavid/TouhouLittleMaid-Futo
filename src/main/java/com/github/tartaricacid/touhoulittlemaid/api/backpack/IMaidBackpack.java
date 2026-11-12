package com.github.tartaricacid.touhoulittlemaid.api.backpack;

import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.BackpackLevel;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆背包类型，移植自上游 {@code api/backpack/IMaidBackpack}。
 *
 * <p>背包决定「女仆能使用物品栏的前多少格」，以及穿上 / 脱下时的副作用。</p>
 *
 * <p><b>与上游的差异：</b>上游这个抽象类还声明了
 * {@code getGuiProvider}（容器 GUI）、{@code getBackpackModel} / {@code getBackpackTexture} /
 * {@code offsetBackpackItem}（客户端渲染）、{@code onSpawnTombstone}（墓碑实体）。
 * 这些依赖尚未移植的子系统（GUI / 墓碑），因此在 GUI 里程碑之前暂不声明 ——
 * 逻辑层（容量、穿脱）已完整可用。</p>
 */
public abstract class IMaidBackpack {
    /**
     * 背包 id，同时是本地化 key 与模型键。
     */
    public abstract ResourceLocation getId();

    /**
     * 对应的背包物品；空背包返回 {@code Items.AIR}。
     */
    public abstract Item getItem();

    /**
     * 穿上背包时调用（例如把背包本身放进专用槽位）。
     */
    public abstract void onPutOn(ItemStack stack, Player player, EntityMaid maid);

    /**
     * 脱下背包时调用（例如把超出的物品吐出来）。
     */
    public abstract void onTakeOff(ItemStack stack, Player player, EntityMaid maid);

    /**
     * 脱下来后掉落的物品形态。
     */
    public ItemStack getTakeOffItemStack(ItemStack stack, @Nullable Player player, EntityMaid maid) {
        return this.getItem().getDefaultInstance();
    }

    /**
     * 该背包允许使用的最大槽位数（0-based 上界，即「前 N 格可用」）。
     */
    public abstract int getAvailableMaxContainerIndex();

    /**
     * 女仆死亡生成墓碑时回调（上游 {@code IMaidBackpack#onSpawnTombstone}）。
     *
     * <p>上游用它把「背包自己的额外数据」也塞进墓碑 —— 目前只有熔炉背包（燃料 + 烧炼进度）
     * 和储罐背包（液体）有这种数据，其余背包的实现是空方法。</p>
     *
     * <p><b>与上游的差异：</b>这两套 {@code IBackpackData} 在本移植版还没移植
     * （见 {@code FurnaceBackpack} / {@code TankBackpack} 的类注释），所以这里给出
     * 空实现而不是抽象方法 —— 语义与上游「没有额外数据的背包」完全一致，
     * 等数据层到位后在这两个子类里覆写即可。</p>
     */
    public void onSpawnTombstone(EntityMaid maid,
                                 com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone tombstone) {
    }

    /**
     * 掉落「空背包也装得下」的那些基础格以外的所有物品。
     *
     * <p>脱下背包时使用：基础 6 格（{@link BackpackLevel#EMPTY_CAPACITY}）永远属于女仆本人，
     * 只有背包扩展出来的格子需要吐出来。</p>
     */
    protected final void dropAllItems(EntityMaid maid) {
        ItemsUtil.dropEntityItems(maid, maid.getMaidInv(), BackpackLevel.EMPTY_CAPACITY);
    }

    /**
     * 按「换上的新背包」决定要掉落哪些物品。
     *
     * @param stack 玩家手里那件物品（剪刀 → 空背包 → 全部扩展格掉落；
     *              新背包物品 → 只掉落超出新背包容量的部分）
     */
    protected final void dropRelativeItems(ItemStack stack, EntityMaid maid) {
        BackpackManager.findBackpack(stack).ifPresentOrElse(
                backpack -> ItemsUtil.dropEntityItems(maid, maid.getMaidInv(),
                        backpack.getAvailableMaxContainerIndex()),
                () -> this.dropAllItems(maid));
    }
}

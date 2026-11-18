package com.github.tartaricacid.touhoulittlemaid.api.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.items.IItemHandler;

/**
 * 女仆用隙间传输物品时触发，移植自上游 {@code api/event/MaidWirelessIOEvent}。
 *
 * <p>可取消：取消后这一轮<b>一件物品都不搬</b>。上游在此之上还留了
 * {@link #getMaidInv()} / {@link #getChestInv()} 两个物品栏引用，
 * 扩展可以在取消之前自己搬一遍（比如「只搬矿石」这类自定义规则）。</p>
 *
 * <p>每个方向是独立的子类（{@code MaidToChest} / {@code ChestToMaid}），
 * 这样监听器只订阅自己关心的方向 —— 上游也是这么分的。</p>
 *
 * <p><b>注意</b>：这个事件每 100 tick 触发一次、每只戴隙间的女仆各一次，
 * 取消与否都<b>不会</b>阻止后面那条「使用隙间」的进度判据 —— 上游如此。</p>
 */
@Cancelable
public abstract class MaidWirelessIOEvent extends Event {
    private final EntityMaid maid;
    /**
     * 女仆的物品栏（背包 + 双手，不含盔甲）。
     */
    private final IItemHandler maidInv;
    /**
     * 箱子的物品栏。
     */
    private final IItemHandler chestInv;
    /**
     * 隙间过滤清单（9 格）。
     */
    private final IItemHandler filterInv;
    /**
     * 是否是黑名单模式。
     */
    private final boolean isBlacklist;
    /**
     * 槽位配置：{@code true} 表示该槽位<b>不</b>允许传输。
     */
    private final boolean[] slotConfig;

    public MaidWirelessIOEvent(EntityMaid maid, IItemHandler maidInv, IItemHandler chestInv,
                               IItemHandler filterInv, boolean isBlacklist, boolean[] slotConfig) {
        this.maid = maid;
        this.maidInv = maidInv;
        this.chestInv = chestInv;
        this.filterInv = filterInv;
        this.isBlacklist = isBlacklist;
        this.slotConfig = slotConfig;
    }

    public EntityMaid getMaid() {
        return this.maid;
    }

    public IItemHandler getMaidInv() {
        return this.maidInv;
    }

    public IItemHandler getChestInv() {
        return this.chestInv;
    }

    public IItemHandler getFilterInv() {
        return this.filterInv;
    }

    public boolean isBlacklist() {
        return this.isBlacklist;
    }

    public boolean[] getSlotConfig() {
        return this.slotConfig;
    }

    /**
     * 女仆 → 箱子。
     */
    public static class MaidToChest extends MaidWirelessIOEvent {
        public MaidToChest(EntityMaid maid, IItemHandler maidInv, IItemHandler chestInv,
                           IItemHandler filterInv, boolean isBlacklist, boolean[] slotConfig) {
            super(maid, maidInv, chestInv, filterInv, isBlacklist, slotConfig);
        }
    }

    /**
     * 箱子 → 女仆。
     */
    public static class ChestToMaid extends MaidWirelessIOEvent {
        public ChestToMaid(EntityMaid maid, IItemHandler maidInv, IItemHandler chestInv,
                           IItemHandler filterInv, boolean isBlacklist, boolean[] slotConfig) {
            super(maid, maidInv, chestInv, filterInv, isBlacklist, slotConfig);
        }
    }
}


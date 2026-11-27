package com.github.tartaricacid.touhoulittlemaid.compat.trinkets.menu;
import com.github.tartaricacid.touhoulittlemaid.compat.trinkets.TrinketsHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
/**
 * 女仆的饰品界面容器（Trinkets 版），对应上游 {@code compat/curios/menu/CuriosContainer}。
 *
 * <h2>版式</h2>
 * <p>与上游一致：<b>玩家物品栏 + 女仆护甲/主副手 + 饰品格网格</b>，
 * 不重复摆女仆自己的背包格（见 {@code MaidMainContainer} 的 {@code withBackpack} 参数）。
 * 饰品格从 {@code 143,37} 起，每行 6 格、步距 18 —— 与上游
 * {@code 143 + (i % 6) * 18, 37 + (i / 6) * 18} 逐像素相同。</p>
 *
 * <h2>为什么不做分页</h2>
 * <p>上游把每页固定 36 格并做翻页，是因为 Curios 的槽位数完全由玩家装了哪些饰品决定，
 * 可能非常多。本移植版的槽位集合来自我们自己的数据文件
 * （{@code data/trinkets/entities/maid.json}，四个槽：项链 / 戒指 / 帽子 / 腰带），
 * 一页 6×6 绰绰有余，所以<b>不做分页</b>。</p>
 *
 * <p>这不只是「省事」：翻页需要 {@code AbstractContainerMenu#slots.clear()} 之后重建，
 * 而 1.18.2 的 {@code lastSlots} / {@code remoteSlots} 是 <b>private</b>
 * （到 1.20 才放开），清不掉就会在下一页收到上页的残留同步包，
 * 表现是「翻页后格子里的物品错位」。等真需要多页时再考虑换一条路。</p>
 */
public class TrinketContainer extends MaidMainContainer {
    private static final int GRID_X = 143;
    private static final int GRID_Y = 37;
    private static final int COLUMNS = 6;
    /**
     * 一页最多 36 格（6 行）。超出部分不摆 —— 见类注释的「不做分页」。
     */
    private static final int MAX_SLOTS = 36;
    /**
     * 本页摆了多少个饰品格（界面据此决定背景画多大）。
     */
    private final int trinketSlotCount;
    public TrinketContainer(MenuType<?> type, int id, Inventory inventory, int entityId) {
        // withBackpack = false：女仆自己的背包格在主界面已经有了
        super(type, id, inventory, entityId, false);
        this.trinketSlotCount = this.addTrinketSlots();
    }
    public int getTrinketSlotCount() {
        return this.trinketSlotCount;
    }
    /**
     * 摆饰品格，返回实际摆了几格。
     */
    private int addTrinketSlots() {
        if (this.maid == null) {
            return 0;
        }
        List<SlotReference> slots = this.collectSlots();
        int count = Math.min(slots.size(), MAX_SLOTS);
        for (int i = 0; i < count; i++) {
            SlotReference ref = slots.get(i);
            TrinketInventory inventory = ref.inventory();
            String group = inventory.getSlotType() == null ? "" : inventory.getSlotType().getGroup();
            String name = inventory.getSlotType() == null ? "" : inventory.getSlotType().getName();
            int x = GRID_X + (i % COLUMNS) * 18;
            int y = GRID_Y + (i / COLUMNS) * 18;
            this.addSlot(new MaidTrinketSlot(this.maid, inventory, ref.index(), group, name, x, y));
        }
        return count;
    }
    private List<SlotReference> collectSlots() {
        if (this.maid == null) {
            return new ArrayList<>();
        }
        return TrinketsHandler.getHandler(this.maid)
                .map(TrinketsHandler::allSlots)
                .orElseGet(ArrayList::new);
    }
    /**
     * 原版 {@code setItem} 在槽位号越界时会直接抛。客户端在菜单同步的头几帧
     * 可能收到还带着旧下标的写入，所以这里挡一下（上游同样有一层保护）。
     */
    @Override
    public void setItem(int slotId, int stateId, ItemStack stack) {
        if (slotId >= 0 && slotId < this.slots.size()) {
            super.setItem(slotId, stateId, stack);
        }
    }
    /**
     * Shift 点击：玩家栏的东西优先塞饰品格，塞不下再塞女仆护甲/主副手；
     * 反过来从其它格 Shift 点击就进玩家栏。
     *
     * <p>下标分界与 {@code MaidMainContainer} 完全一致：0..35 玩家、36..41 护甲+手、
     * 其后是饰品格（因为背包格被关掉了，42 起就是饰品）。</p>
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int equipmentEnd = PLAYER_INVENTORY_SIZE + 6;
        if (index < PLAYER_INVENTORY_SIZE) {
            if (!this.moveItemStackTo(stack, equipmentEnd, this.slots.size(), false)
                    && !this.moveItemStackTo(stack, PLAYER_INVENTORY_SIZE, equipmentEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, PLAYER_INVENTORY_SIZE, true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }
    /**
     * 遍历用：当前所有饰品格（按下标顺序）。
     */
    public List<MaidTrinketSlot> trinketSlots() {
        List<MaidTrinketSlot> out = new ArrayList<>();
        for (Slot slot : this.slots) {
            if (slot instanceof MaidTrinketSlot trinket) {
                out.add(trinket);
            }
        }
        return out;
    }
}

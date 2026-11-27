package com.github.tartaricacid.touhoulittlemaid.compat.trinkets;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Trinkets 联动的<b>真正实现</b>。
 *
 * <p>本类的方法签名里直接出现 Trinkets 的类型，所以<b>只能在
 * {@link TrinketsCompat#isLoaded()} 为真时被调用</b> —— 那之后 Trinkets 的类已经在
 * classpath 上，链接不会失败。所有外部调用点都经过 {@code TrinketsCompat} 的转发。</p>
 *
 * <h2>槽位从哪来</h2>
 * <p>Trinkets 3.3.1 的实体槽位是<b>数据驱动</b>的：{@code EntitySlotLoader} 扫描
 * {@code data/trinkets/entities/*.json}，读其中的 {@code entities}（实体类型 id 列表）与
 * {@code slots}（{@code "组/槽"} 列表），把 {@code data/trinkets/slots/<组>/<槽>.json}
 * 定义的槽挂到这些实体上。本移植版随包提供了一份，见
 * {@code data/trinkets/entities/maid.json}。</p>
 */
public final class TrinketsHandler {
    private TrinketsHandler() {
    }

    public static AbstractContainerMenu createMenu(int syncId, Inventory inventory, int entityId) {
        return new com.github.tartaricacid.touhoulittlemaid.compat.trinkets.menu.TrinketContainer(
                com.github.tartaricacid.touhoulittlemaid.init.InitMenu.TRINKET_MAIN,
                syncId, inventory, entityId);
    }

    /**
     * 取女仆身上的 Trinkets 组件。
     */
    public static Optional<TrinketComponent> getHandler(EntityMaid maid) {
        return TrinketsApi.getTrinketComponent(maid);
    }

    /**
     * 把所有饰品槽摊平成一个列表，供界面按页取。
     *
     * <p>顺序与 {@code TrinketComponent#getInventory()} 的 {@code Map} 顺序一致
     * （外层组、内层槽），与上游 {@code ICuriosItemHandler#getCurios()} 的遍历顺序同构。</p>
     */
    public static List<SlotReference> allSlots(TrinketComponent component) {
        List<SlotReference> out = new ArrayList<>();
        for (Map<String, TrinketInventory> group : component.getInventory().values()) {
            for (TrinketInventory inventory : group.values()) {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    out.add(new SlotReference(inventory, i));
                }
            }
        }
        return out;
    }

    public static int slotCount(EntityMaid maid) {
        return getHandler(maid).map(component -> allSlots(component).size()).orElse(0);
    }

    /**
     * 死亡时把饰品倒进墓碑。
     *
     * <p>倒完就<b>真的空了</b>：Trinkets 自己的掉落逻辑随后仍然会跑，但那时槽位是空的，
     * 于是不会出现「墓碑里一份、地上又掉一份」。</p>
     */
    public static void drainToTombstone(EntityMaid maid, EntityTombstone tombstone) {
        Optional<TrinketComponent> component = getHandler(maid);
        if (component.isEmpty()) {
            return;
        }
        for (Map<String, TrinketInventory> group : component.get().getInventory().values()) {
            for (TrinketInventory inventory : group.values()) {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.removeItem(i, inventory.getMaxStackSize());
                    if (!stack.isEmpty()) {
                        tombstone.insertItem(stack);
                    }
                }
            }
        }
    }
}


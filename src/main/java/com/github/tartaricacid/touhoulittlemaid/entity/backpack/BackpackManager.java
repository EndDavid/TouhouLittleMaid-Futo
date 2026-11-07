package com.github.tartaricacid.touhoulittlemaid.entity.backpack;

import com.github.tartaricacid.touhoulittlemaid.api.backpack.IMaidBackpack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 女仆背包注册表，移植自上游 {@code entity/backpack/BackpackManager}。
 *
 * <p>已注册：{@code empty} / {@code small_backpack} / {@code middle_backpack} /
 * {@code big_backpack} / {@code crafting_table_backpack} / {@code ender_chest_backpack} /
 * {@code furnace_backpack} / {@code tank}。</p>
 *
 * <p><b>与上游的差异：</b>上游还维护 {@code BACKPACK_MODEL_MAP}
 * （客户端模型 + 贴图，供 {@code LayerMaidBackpack} 渲染用）。
 * 渲染层尚未移植，因此本类只保留逻辑层需要的两张表：id → 背包、物品 → 背包。</p>
 */
public final class BackpackManager {
    private static final Map<ResourceLocation, IMaidBackpack> BACKPACK_ID_MAP = new HashMap<>();
    private static final Map<Item, IMaidBackpack> BACKPACK_ITEM_MAP = new HashMap<>();
    private static IMaidBackpack emptyBackpack;

    private BackpackManager() {
    }

    public static void init() {
        BACKPACK_ID_MAP.clear();
        BACKPACK_ITEM_MAP.clear();
        emptyBackpack = new EmptyBackpack();
        add(emptyBackpack);
        add(new SmallBackpack());
        add(new MiddleBackpack());
        add(new BigBackpack());
        add(new CraftingTableBackpack());
        add(new EnderChestBackpack());
        add(new FurnaceBackpack());
        add(new TankBackpack());
    }

    public static IMaidBackpack getEmptyBackpack() {
        return emptyBackpack;
    }

    public static Optional<IMaidBackpack> findBackpack(ResourceLocation id) {
        return Optional.ofNullable(BACKPACK_ID_MAP.get(id));
    }

    /**
     * 按背包物品找类型。空物品 / 非背包物品返回 {@code Optional.empty()}。
     *
     * <p>这里<b>不能</b>兜底成空背包：交互层用 {@code ifPresent} 判断
     * 「玩家手里拿的是不是背包」，兜底会让空手也能触发换背包。</p>
     */
    public static Optional<IMaidBackpack> findBackpack(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BACKPACK_ITEM_MAP.get(stack.getItem()));
    }

    /**
     * 按 id 找背包，找不到就返回空背包。上游多处使用这种「兜底」语义。
     */
    public static IMaidBackpack getOrDefault(@Nullable ResourceLocation id) {
        if (id == null) {
            return emptyBackpack;
        }
        IMaidBackpack backpack = BACKPACK_ID_MAP.get(id);
        return backpack == null ? emptyBackpack : backpack;
    }

    /**
     * 按背包物品找类型；空物品 / 非背包物品返回空背包（兜底版本）。
     */
    public static IMaidBackpack findByItem(ItemStack stack) {
        return findBackpack(stack).orElse(emptyBackpack);
    }

    /**
     * 给玩家上「背包物品」冷却，防止一次点击来回换装（上游同名方法）。
     */
    public static void addBackpackCooldown(Player player) {
        for (Item backpack : BACKPACK_ITEM_MAP.keySet()) {
            player.getCooldowns().addCooldown(backpack, 20);
        }
    }

    public static void add(IMaidBackpack backpack) {
        BACKPACK_ID_MAP.put(backpack.getId(), backpack);
        Item item = backpack.getItem();
        if (item != Items.AIR) {
            BACKPACK_ITEM_MAP.put(item, backpack);
        }
    }

    public static Map<ResourceLocation, IMaidBackpack> getBackpackMap() {
        return BACKPACK_ID_MAP;
    }
}

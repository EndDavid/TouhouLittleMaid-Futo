package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemExtinguisher;
import com.github.tartaricacid.touhoulittlemaid.item.ItemMaidBackpack;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

public final class InitItems {
    /** 与原模组同名的主创造模式物品栏。 */
    public static final CreativeModeTab MAIN_TAB = FabricItemGroupBuilder
            .create(TouhouLittleMaid.rl("main"))
            .icon(() -> new ItemStack(InitItems.MAID_SPAWN_EGG))
            .build();

    public static final Item MAID_SPAWN_EGG = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("maid_spawn_egg"),
            // 与上游一致：底色与斑点色都是纯白（ForgeSpawnEggItem(..., 0xffffff, 0xffffff, ...)）
            new SpawnEggItem(InitEntities.MAID, 0xFFFFFF, 0xFFFFFF,
                    new FabricItemSettings().tab(MAIN_TAB)));

    /**
     * 女仆妖精刷怪蛋。底色与斑点色沿用上游的纯白（与女仆刷怪蛋同款处理），
     * 真正的辨识靠 {@code item/fairy_spawn_egg.png} 这张物品贴图。
     */
    public static final Item FAIRY_SPAWN_EGG = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("fairy_spawn_egg"),
            new SpawnEggItem(InitEntities.FAIRY, 0xFFFFFF, 0xFFFFFF,
                    new FabricItemSettings().tab(MAIN_TAB)));

    /**
     * 灭火器（上游 {@code InitItems.EXTINGUISHER}）。
     *
     * <p>玩家无法使用，只供女仆的灭火模式 / 攻击模式副手增伤使用。</p>
     */
    public static final Item EXTINGUISHER = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("extinguisher"), new ItemExtinguisher());

    // ------------------------------------------------------------------ 御币

    /**
     * 博丽御币（上游 {@code HAKUREI_GOHEI}）。女仆主手持有它、且工作模式为
     * {@code danmaku_attack} 时会发射弹幕。
     */
    public static final Item HAKUREI_GOHEI = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("hakurei_gohei"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei());

    /**
     * 东风谷御币（上游 {@code SANAE_GOHEI}）：与博丽御币<b>同一个类</b>，
     * 差异只在物品模型 / 贴图（上游也是这样注册的）。
     */
    public static final Item SANAE_GOHEI = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("sanae_gohei"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei());

    // ------------------------------------------------------------------ P 点

    /**
     * P 点物品（上游 {@code InitItems.POWER_POINT}）。
     *
     * <p>右击丢出后会变成 {@code EntityPowerPoint} 实体，被吸收时按 4P = 1XP
     * 折算成经验（女仆拾取时优先拿去修补「经验修补」装备）。</p>
     */
    public static final Item POWER_POINT = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("power_point"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemPowerPoint());

    // ------------------------------------------------------------------ 背包

    public static final Item MAID_BACKPACK_SMALL = registerBackpack("maid_backpack_small");
    public static final Item MAID_BACKPACK_MIDDLE = registerBackpack("maid_backpack_middle");
    public static final Item MAID_BACKPACK_BIG = registerBackpack("maid_backpack_big");
    public static final Item CRAFTING_TABLE_BACKPACK = registerBackpack("crafting_table_backpack");
    public static final Item ENDER_CHEST_BACKPACK = registerBackpack("ender_chest_backpack");
    public static final Item FURNACE_BACKPACK = registerBackpack("furnace_backpack");
    /**
     * 储罐背包。上游是 {@code ItemTankBackpack}（带流体组件），流体层移植前先用普通背包物品。
     */
    public static final Item TANK_BACKPACK = registerBackpack("tank_backpack");

    /**
     * 背包物品统一 {@code stacksTo(1)}（与上游一致：背包是独占装备，不做堆叠）。
     */
    private static Item registerBackpack(String name) {
        return Registry.register(Registry.ITEM, TouhouLittleMaid.rl(name),
                new ItemMaidBackpack(new FabricItemSettings().stacksTo(1).tab(MAIN_TAB)));
    }

    private InitItems() {
    }

    public static void init() {
        // 静态字段即完成注册
    }
}

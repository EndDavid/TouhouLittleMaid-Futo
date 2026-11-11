package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemExtinguisher;
import com.github.tartaricacid.touhoulittlemaid.item.ItemMaidBackpack;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.core.NonNullList;
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
            // 帕秋莉手册是可选前置：装了才把《记忆中的幻想乡》摆进物品栏，
            // 顺序也与 TLMO 1.20.1 一致（手册排在最前面）。
            //
            // 注意 Fabric 的 appendItems 是【替换】默认填充，不会自己调一遍原版逻辑
            // （反编译 FabricItemGroupBuilder$1#fillItemList 可见：stacksForDisplay 非空时
            // 只调它、不调 super）。所以这里必须自己按原版规则把物品补回去，否则物品栏会空掉。
            .appendItems((items, tab) -> {
                NonNullList<ItemStack> defaults = NonNullList.create();
                for (Item item : Registry.ITEM) {
                    item.fillItemCategory(tab, defaults);
                }
                if (com.github.tartaricacid.touhoulittlemaid.compat.patchouli.PatchouliCompat.isLoaded()) {
                    com.github.tartaricacid.touhoulittlemaid.compat.patchouli.CreativeTabCompat.appendBook(items);
                }
                items.addAll(defaults);
            })
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

    /**
     * 文文的相机（上游 {@code InitItems.CAMERA}）。
     *
     * <p>对着自己的女仆右键即可把她收进一张照片，用于跨维度 / 远距离搬运。</p>
     */
    public static final Item CAMERA = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("camera"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemCamera());

    /**
     * 照片（上游 {@code InitItems.PHOTO}）：相机拍出来的结果，右击方块放出女仆。
     */
    public static final Item PHOTO = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("photo"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemPhoto());

    // ------------------------------------------------------------------ 神社庭灯

    /**
     * 神社庭灯（上游 {@code InitItems.MAID_BEACON}）。
     *
     * <p>方块本体与方块实体在 {@code InitBlocks} 注册，这里只注册物品形态。
     * 类构造器里会读 {@code InitBlocks.MAID_BEACON}，因此主入口必须先调用
     * {@code InitBlocks.init()} 再调用 {@code InitItems.init()}。</p>
     */
    public static final Item MAID_BEACON = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("maid_beacon"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemMaidBeacon());

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

    // ------------------------------------------------------------------ 女仆饰品

    /*
     * 饰品（Bauble）的物品侧注册。行为侧在 item/bauble/BaubleManager 里绑定。
     *
     * 耐久数值逐条取自上游 InitItems：极意 6、爆炸 32、火焰 128、投射物 64、
     * 魔法 128、摔落 32、溺水 64、机敏织物 64；物品磁铁与静音无耐久（普通饰品）。
     *
     * 注意 BOTH 类物品都 stacksTo(1) —— 饰品是「装备」，不做堆叠。
     */

    public static final Item ULTRAMARINE_ORB_ELIXIR = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("ultramarine_orb_elixir"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemDamageableBauble(6));

    public static final Item EXPLOSION_PROTECT_BAUBLE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("explosion_protect_bauble"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemDamageableBauble(32));

    public static final Item FIRE_PROTECT_BAUBLE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("fire_protect_bauble"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemDamageableBauble(128));

    public static final Item PROJECTILE_PROTECT_BAUBLE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("projectile_protect_bauble"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemDamageableBauble(64));

    public static final Item MAGIC_PROTECT_BAUBLE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("magic_protect_bauble"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemDamageableBauble(128));

    public static final Item FALL_PROTECT_BAUBLE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("fall_protect_bauble"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemDamageableBauble(32));

    public static final Item DROWN_PROTECT_BAUBLE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("drown_protect_bauble"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemDamageableBauble(64));

    public static final Item NIMBLE_FABRIC = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("nimble_fabric"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemDamageableBauble(64));

    public static final Item ITEM_MAGNET_BAUBLE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("item_magnet_bauble"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemNormalBauble());

    public static final Item MUTE_BAUBLE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("mute_bauble"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemNormalBauble());

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

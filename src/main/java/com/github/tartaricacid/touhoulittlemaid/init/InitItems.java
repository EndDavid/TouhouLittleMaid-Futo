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

    /**
     * 坐垫专用创造模式物品栏（上游 {@code MaidGroup.CHAIR_TAB}）。
     *
     * <p>坐垫有二十多种模型，全塞进主物品栏会把其余物品挤得找不到，所以上游单开一栏。
     * 图标用默认坐垫 {@code touhou_little_maid:cushion} 的带 NBT 物品 ———
     * 这里的 lambda 是<b>延迟求值</b>的，因此可以先声明本栏、后声明 {@link #CHAIR}。</p>
     */
    public static final CreativeModeTab CHAIR_TAB = FabricItemGroupBuilder
            .create(TouhouLittleMaid.rl("chair"))
            .icon(() -> com.github.tartaricacid.touhoulittlemaid.item.ItemChair.setData(
                    new ItemStack(InitItems.CHAIR),
                    new com.github.tartaricacid.touhoulittlemaid.item.ItemChair.Data(
                            "touhou_little_maid:cushion", 0.0F, true, false)))
            .appendItems((items, tab) -> {
                NonNullList<ItemStack> defaults = NonNullList.create();
                for (Item item : Registry.ITEM) {
                    item.fillItemCategory(tab, defaults);
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

    // ------------------------------------------------------------------ 胶片 / 神社

    /**
     * 胶片（上游 {@code InitItems.FILM}）。
     *
     * <p>女仆死亡时掉落，NBT 里存着那只女仆的完整快照；放进神社（或祭坛的
     * {@code reborn_maid} 配方）即可复活。见 {@code item/ItemFilm}。</p>
     */
    public static final Item FILM = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("film"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemFilm());

    /**
     * 神社（上游 {@code InitItems.SHRINE}）：方块物品形态。
     *
     * <p>类构造器里会读 {@code InitBlocks.SHRINE}，因此主入口必须先调用
     * {@code InitBlocks.init()} 再调用 {@code InitItems.init()}（与神社庭灯同款约束）。</p>
     */
    public static final Item SHRINE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("shrine"),
            new net.minecraft.world.item.BlockItem(
                    com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.SHRINE,
                    new FabricItemSettings().tab(MAIN_TAB)));

    // ------------------------------------------------------------------ 女仆床 / 稻草人 / 替身地藏

    /**
     * 女仆床（上游 {@code InitItems.MAID_BED}）。
     *
     * <p>用 {@code ItemMaidBed} 而不是普通 {@code BlockItem}：放置时的方块更新标志位
     * 不一样，否则双格方块会在放置瞬间自己塌掉。</p>
     */
    public static final Item MAID_BED = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("maid_bed"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemMaidBed());

    /**
     * 稻草人（上游 {@code InitItems.SCARECROW}）。
     */
    public static final Item SCARECROW = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("scarecrow"),
            new net.minecraft.world.item.BlockItem(
                    com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.SCARECROW,
                    new FabricItemSettings().tab(MAIN_TAB)));

    /**
     * 替身地藏（上游 {@code InitItems.SUBSTITUTE_JIZO}）：给女仆永久无敌。
     */
    public static final Item SUBSTITUTE_JIZO = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("substitute_jizo"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemSubstituteJizo());

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

    // ------------------------------------------------------------------ 坐垫

    /**
     * 自定义坐垫（上游 {@code InitItems.CHAIR}）。
     *
     * <p>一个物品承载<b>所有</b>坐垫模型：模型 id、坐高、可骑、无重力四项写在 NBT 里，
     * 由 {@code ItemChair.Data} 读写。因此物品栏里看到的一排坐垫其实是同一个物品的
     * 不同 NBT 变体（由 {@code fillItemCategory} 铺出）。</p>
     */
    public static final Item CHAIR = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("chair"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemChair());

    /**
     * 坐垫碰撞箱显示器（上游 {@code InitItems.CHAIR_SHOW}）。
     *
     * <p>手持并蹲下时，所有坐垫只画红色线框，方便对齐多方块家具。</p>
     */
    public static final Item CHAIR_SHOW = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("chair_show"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemChairShow());

    /**
     * 好感度工具三件套（上游 {@code InitItems.FAVORABILITY_TOOL_ADD / _REDUCE / _FULL}）。
     *
     * <p>创造模式专属的调试工具：右键女仆直接加 / 减 / 拉满好感度。三个物品共用一个类，
     * 靠 {@code type} 字符串区分，见 {@code item.ItemFavorabilityTool}。
     * 效果不写在物品里，而在 {@code EntityMaid#useFavorabilityTool}（上游是
     * {@code event/maid/UseFavorabilityToolEvent}）。</p>
     */
    public static final Item FAVORABILITY_TOOL_ADD = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("favorability_tool_add"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemFavorabilityTool("add"));

    public static final Item FAVORABILITY_TOOL_REDUCE = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("favorability_tool_reduce"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemFavorabilityTool("reduce"));

    public static final Item FAVORABILITY_TOOL_FULL = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("favorability_tool_full"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemFavorabilityTool("full"));


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

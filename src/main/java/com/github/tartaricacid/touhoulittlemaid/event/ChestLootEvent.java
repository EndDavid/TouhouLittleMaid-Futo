package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * 把神社与 P 点塞进原版所有的箱子战利品里，对应上游的两条
 * <b>全局战利品修饰器（Global Loot Modifier）</b>：
 *
 * <pre>
 * data/touhou_little_maid/loot_modifiers/shrine.json       chance 0.05  parameter_set "minecraft:chest"
 * data/touhou_little_maid/loot_modifiers/power_point.json  chance 0.50  parameter_set "minecraft:chest"
 * </pre>
 *
 * <h2>为什么改成代码实现</h2>
 * <p>「全局战利品修饰器」是 Forge 独有的<b>数据类型</b>（{@code forge:global_loot_modifiers}），
 * 1.18.2 原版和 Fabric 都没有这个东西 —— 原版的战利品表只有「每个表写自己的池」。
 * Fabric 提供的等价物是 {@code LootTableEvents.MODIFY}：每张表在编译期被回调一次，
 * 回调里可以往 {@code LootTable.Builder} 追加池。所以这里把上游那两个 JSON
 * 逐条翻译成「向所有 chests/* 表追加一个带概率条件的池」。</p>
 *
 * <h2>为什么只认 {@code source.isBuiltin()}</h2>
 * <p>{@code MODIFY} 对同一张表可能回调多次：原版内置一次，数据包覆盖时再一次。
 * 上游的全局修饰器只挂在原版表上（{@code parameter_set_name: minecraft:chest} 是
 * 「这属于哪个原版参数集」的声明），所以这里也只处理内置来源，
 * 否则数据包改过的箱子会掉两份。</p>
 *
 * <h2>为什么是 {@code chests/}</h2>
 * <p>原版的「箱子参数集」在 1.18.2 里就是 {@code minecraft:chests/*} 这一族
 * （{@code simple_dungeon}、{@code abandoned_mineshaft}、{@code village/*} …）。
 * 上游声明 {@code minecraft:chest} 参数集，与之等价。</p>
 */
public final class ChestLootEvent {
    /**
     * 原版箱子战利品表的 id 前缀。
     */
    private static final String CHEST_TABLE_PREFIX = "chests/";

    /**
     * 与上游 {@code loot_modifiers/shrine.json} 的 {@code random_chance} 一致。
     */
    private static final float SHRINE_CHANCE = 0.05F;

    /**
     * 与上游 {@code loot_modifiers/power_point.json} 的 {@code random_chance} 一致。
     */
    private static final float POWER_POINT_CHANCE = 0.5F;

    private ChestLootEvent() {
    }

    public static void init() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (source != LootTableSource.VANILLA) {
                return;
            }
            if (!id.getPath().startsWith(CHEST_TABLE_PREFIX)) {
                return;
            }
            tableBuilder.pool(additionPool(InitItems.SHRINE, SHRINE_CHANCE).build());
            tableBuilder.pool(additionPool(InitItems.POWER_POINT, POWER_POINT_CHANCE).build());
        });
    }

    /**
     * 一个「1 次抽取、按概率决定给不给」的池 —— 上游 {@code addition} 修饰器的语义。
     *
     * <p>{@code setRolls(1)} + {@code when(randomChance(p))}：整池要么抽 1 次要么不抽，
     * 与 {@code bonus_rolls} 无关。物品本身的 {@code setCount} 保持默认 1。</p>
     */
    private static LootPool.Builder additionPool(Item item, float chance) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(item))
                .when(LootItemRandomChanceCondition.randomChance(chance));
    }
}

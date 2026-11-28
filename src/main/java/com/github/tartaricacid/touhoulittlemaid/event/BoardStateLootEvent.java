package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.datapack.BoardStateData;
import com.github.tartaricacid.touhoulittlemaid.datapack.BoardStateDataReload;
import com.github.tartaricacid.touhoulittlemaid.datapack.pojo.BoardStateRecord;
import com.github.tartaricacid.touhoulittlemaid.item.ItemBoardState;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.List;

/**
 * 把残局道具塞进两个原版箱子，对应上游 1.20.1 的两条全局战利品修饰器：
 *
 * <pre>
 * data/touhou_little_maid/loot_modifiers/stronghold_library_board_state.json  -> minecraft:chests/stronghold_library
 * data/touhou_little_maid/loot_modifiers/village_cartographer.json            -> minecraft:chests/village/village_cartographer
 * </pre>
 *
 * <p>两者都把 {@code touhou_little_maid:chest/random_board_state} 这张表挂进箱子。
 * 那张表是「抽 1 次、在三种棋里等概率挑一个，再按 {@code weight} 挑该棋的一条残局」。</p>
 *
 * <h2>为什么不用上游那个自定义战利品函数</h2>
 * <p>上游写了个 {@code board_state_randomly} 战利品函数，它需要一个注册进
 * {@code Registry.LOOT_FUNCTION_TYPE} 的类型。Fabric 没有「注册战利品函数类型」的事件，
 * 唯一可行的是再开一个 Mixin 往 {@code LootItemFunctions} 的私有表里塞东西 ——
 * 为了一个「随机挑一条数据」的功能不值得。这里换成<b>直接用原版的
 * {@link SetNbtFunction}</b>：把每条数据编译成一个带固定 NBT 的条目，
 * 权重就是数据自己的 {@code weight}。抽签交给原版的战利品池，行为等价。</p>
 *
 * <h2>等概率地挑「哪种棋」</h2>
 * <p>上游是「先等概率挑物品，再按权重挑残局」。直接把所有条目摊平到同一个池里，
 * 物品被挑中的概率会变成「与该棋的条目数成正比」。所以这里给每件物品的条目
 * 按 {@code NORMALIZER / 该物品权重和} 缩放，让每件物品的总权重都等于
 * {@code NORMALIZER} —— 于是三件物品各占 1/3，与上游一致。</p>
 *
 * <p><b>只在 {@code source == VANILLA} 时注入</b>，理由同 {@code ChestLootEvent}：
 * 数据包覆盖同一张表时会再回调一次，否则一个箱子会掉两份。</p>
 */
public final class BoardStateLootEvent {
    /**
     * 与上游 {@code loot_modifiers/*.json} 里的 {@code loot_table_id} 一一对应。
     */
    private static final ResourceLocation STRONGHOLD_LIBRARY =
            new ResourceLocation("minecraft", "chests/stronghold_library");
    private static final ResourceLocation VILLAGE_CARTOGRAPHER =
            new ResourceLocation("minecraft", "chests/village/village_cartographer");

    /**
     * 上游战利品表里的标签。数据包里 {@code tags} 含它的条目才会被抽到。
     */
    private static final String LIBRARY_TAG = "library";

    /**
     * 每件物品归一化之后的总权重。取 60 是为了让每条数据的权重仍然是个不太小的整数
     * （自带数据每件 5 条、权重 1，于是每条变成 12）。
     */
    private static final int NORMALIZER = 60;

    private BoardStateLootEvent() {
    }

    public static void init() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!id.equals(STRONGHOLD_LIBRARY) && !id.equals(VILLAGE_CARTOGRAPHER)) {
                return;
            }
            if (source != LootTableSource.VANILLA) {
                return;
            }
            // 加载顺序不可靠：实测 Fabric 的 MODIFY 比 BoardStateDataReload 监听器先跑。
            // 不补这一下，池子会因为数据表为空而根本不存在（「掉不出残局道具」）。
            if (BoardStateData.isEmpty()) {
                BoardStateDataReload.load(resourceManager);
                com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid.LOGGER.debug(
                        "[Futo] board states loaded on demand for {}: gomoku={} xiangqi={} chess={}",
                        id,
                        BoardStateData.getGomokuRecords().size(),
                        BoardStateData.getXiangqiRecords().size(),
                        BoardStateData.getChessRecords().size());
            }
            LootPool.Builder pool = boardStatePool();
            if (pool != null) {
                tableBuilder.pool(pool.build());
            }
        });
    }

    /**
     * 组一个「抽 1 次」的池；一条可用数据都没有时返回 {@code null}（不加空池）。
     */
    private static LootPool.Builder boardStatePool() {
        LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1));
        boolean added = false;
        added |= addItem(pool, com.github.tartaricacid.touhoulittlemaid.init.InitItems.GOMOKU_BOARD_STATE,
                BoardStateData.getGomokuRecords());
        added |= addItem(pool, com.github.tartaricacid.touhoulittlemaid.init.InitItems.CCHESS_BOARD_STATE,
                BoardStateData.getXiangqiRecords());
        added |= addItem(pool, com.github.tartaricacid.touhoulittlemaid.init.InitItems.WCHESS_BOARD_STATE,
                BoardStateData.getChessRecords());
        return added ? pool : null;
    }

    /**
     * 把一种棋的全部残局都加成条目。
     *
     * @return 是否至少加了一条
     */
    private static boolean addItem(LootPool.Builder pool, Item item, List<BoardStateRecord> records) {
        int total = 0;
        for (BoardStateRecord record : records) {
            if (record.hasTag(LIBRARY_TAG)) {
                total += Math.max(0, record.weight());
            }
        }
        if (total <= 0) {
            return false;
        }
        boolean added = false;
        for (BoardStateRecord record : records) {
            if (!record.hasTag(LIBRARY_TAG) || record.data() == null || record.data().isEmpty()) {
                continue;
            }
            // 归一化：让这一件物品的所有记录加起来等于 NORMALIZER
            int weight = Math.max(1, (int) Math.round(record.weight() * (double) NORMALIZER / total));
            pool.add(LootItem.lootTableItem(item)
                    .setWeight(weight)
                    .apply(SetNbtFunction.setTag(nbtOf(record))));
            added = true;
        }
        return added;
    }

    /**
     * 与 {@code ItemBoardState#setState} 写入的字段完全一致，只是这里直接构造 NBT。
     */
    private static CompoundTag nbtOf(BoardStateRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ItemBoardState.DATA_TAG, record.data());
        BoardStateRecord.Display display = record.display();
        tag.putString(ItemBoardState.DESC_TAG, display == null || display.description() == null
                ? "" : display.description());
        tag.putString(ItemBoardState.AUTHOR_TAG, display == null || display.author() == null
                ? "" : display.author());
        return tag;
    }

    /**
     * 调试用：随机挑一个指定物品可用的残局道具（`/give` 的替代，手册与测试都用得到）。
     */
    public static ItemStack createRandom(Item item) {
        ItemStack stack = new ItemStack(item);
        List<BoardStateRecord> records = BoardStateData.getRecordsByItem(stack).stream()
                .filter(record -> record.hasTag(LIBRARY_TAG))
                .toList();
        BoardStateRecord record = com.github.tartaricacid.touhoulittlemaid.util.WeightedPicker
                .pickRandom(records, BoardStateRecord::weight);
        if (record != null) {
            BoardStateRecord.Display display = record.display();
            ItemBoardState.setState(stack, record.data(),
                    display == null ? "" : display.description(),
                    display == null ? "" : display.author());
        }
        return stack;
    }
}


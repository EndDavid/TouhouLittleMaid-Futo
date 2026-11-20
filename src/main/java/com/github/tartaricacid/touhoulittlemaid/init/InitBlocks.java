package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockAltar;
import com.github.tartaricacid.touhoulittlemaid.block.BlockGarageKit;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBeacon;
import com.github.tartaricacid.touhoulittlemaid.block.BlockStatue;
import com.github.tartaricacid.touhoulittlemaid.block.BlockScarecrow;
import com.github.tartaricacid.touhoulittlemaid.block.BlockShrine;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityShrine;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 方块 / 方块实体注册表，对应上游 {@code init/InitBlocks}。
 *
 * <p>上游用 Forge 的 {@code DeferredRegister}，本移植版与 {@code InitEntities} /
 * {@code InitItems} 保持一致：静态字段直接 {@link Registry#register}，{@link #init()}
 * 只负责触发类初始化。</p>
 *
 * <p><b>目前移植了神社庭灯（maid_beacon）、祭坛（altar）、神社（shrine）、
 * 女仆床（maid_bed）与稻草人（scarecrow）。</b>
 * 上游其余 11 个方块（雕像 / 手办柜 / 模型切换器 / 野餐垫 / 棋类 / 键盘 / 书架 / 电脑 …）
 * 的模型与贴图已在资源里，但依赖尚未移植的子系统，等对应系统落地后往这里加即可。</p>
 */
public final class InitBlocks {
    /**
     * 神社庭灯的下半部分。上半部分由 {@code setPlacedBy} 放下，方块实体挂在上半部分上
     * （与上游一致：{@code newBlockEntity} 对 {@code position=down} 返回 {@code null}）。
     */
    public static final Block MAID_BEACON = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("maid_beacon"), new BlockMaidBeacon());

    /**
     * 方块实体类型。上游把它放在 {@code TileEntityMaidBeacon.TYPE} 里，由
     * {@code DeferredRegister} 惰性注册；Fabric 是立即注册，放在这里可以避免
     * 「注册表 → 方块实体类 → 注册表」的静态初始化环。
     */
    public static final BlockEntityType<TileEntityMaidBeacon> MAID_BEACON_TE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("maid_beacon"),
            FabricBlockEntityTypeBuilder.create(TileEntityMaidBeacon::new, MAID_BEACON).build());

    // ------------------------------------------------------------------ 祭坛

    /**
     * 祭坛方块。<b>没有物品形态</b>（上游也不注册物品）：它只是多方块结构建成后的
     * 占位方块，玩家看到的那台大模型由 {@code TileEntityAltarRenderer} 画。
     *
     * <p>建造方式：搭好红羊毛 + 原木的多方块结构，用御币右击中心的红羊毛
     * （{@code ItemHakureiGohei#useOn} → {@code MultiBlockAltar#build}）。</p>
     */
    public static final Block ALTAR = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("altar"), new BlockAltar());

    public static final BlockEntityType<TileEntityAltar> ALTAR_TE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("altar"),
            FabricBlockEntityTypeBuilder.create(TileEntityAltar::new, ALTAR).build());

    // ------------------------------------------------------------------ 神社

    /**
     * 神社（复兴之社）。方块本体见 {@link BlockShrine}，物品形态在 {@code InitItems} 注册。
     */
    public static final Block SHRINE = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("shrine"), new BlockShrine());

    public static final BlockEntityType<TileEntityShrine> SHRINE_TE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("shrine"),
            FabricBlockEntityTypeBuilder.create(TileEntityShrine::new, SHRINE).build());

    // ------------------------------------------------------------------ 女仆床 / 稻草人

    /**
     * 女仆床。双格方块（FOOT + HEAD），<b>没有方块实体</b> —— 状态全在 {@code BlockState} 里
     * （{@code PART} / {@code OCCUPIED} / {@code FACING}），睡觉的是女仆自己。
     *
     * <p>物品形态是 {@code item/ItemMaidBed}（覆盖了 {@code placeBlock} 的标志位），
     * 不能直接用 {@code BlockItem}。</p>
     */
    public static final Block MAID_BED = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("maid_bed"), new BlockMaidBed());

    /**
     * 稻草人。同样是双格方块，用来抑制女仆妖精的生成（见 {@code MiscConfig#SCARECROW_RANGE}）。
     * 它的 POI 只注册在下半格，见 {@code entity/poi/MaidPoiManager}。
     */
    public static final Block SCARECROW = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("scarecrow"), new BlockScarecrow());

    // ------------------------------------------------------------------ 雕像 / 手办

    /**
     * 雕像方块。一座雕像由 N 个本方块拼成（1x1x1 / 1x2x1 / 2x4x2 / 3x6x3），
     * 由 {@code ItemChisel} 凿出来，拆任意一格整座还原成粘土。
     *
     * <p><b>没有物品形态</b>：玩家拿不到「雕像方块」，只能拿凿子凿出来。
     * 手办烧 / 挖之后掉的是粘土，不是它。</p>
     */
    public static final Block STATUE = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("statue"), new BlockStatue());

    public static final BlockEntityType<TileEntityStatue> STATUE_TE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("statue"),
            FabricBlockEntityTypeBuilder.create(TileEntityStatue::new, STATUE).build());

    /**
     * 手办方块。单格，方块实体里存一份实体 NBT，物品形态见 {@code InitItems.GARAGE_KIT}。
     */
    public static final Block GARAGE_KIT = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("garage_kit"), new BlockGarageKit());

    public static final BlockEntityType<TileEntityGarageKit> GARAGE_KIT_TE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("garage_kit"),
            FabricBlockEntityTypeBuilder.create(TileEntityGarageKit::new, GARAGE_KIT).build());

    // ------------------------------------------------------------------ 棋类

    /**
     * 中国象棋棋盘（上游 {@code InitBlocks.CCHESS}）。3×3 多方块，只有中心格带方块实体。
     *
     * <p>{@code getRenderShape} 是 {@code ENTITYBLOCK_ANIMATED}，所以 {@code models/block/cchess.json}
     * 里只有一个 {@code particle} 贴图 —— 真正的模型由 {@code TileEntityCChessRenderer} 画。</p>
     */
    public static final Block CCHESS = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("cchess"), new com.github.tartaricacid.touhoulittlemaid.block.BlockCChess());

    public static final BlockEntityType<com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityCChess> CCHESS_TE =
            Registry.register(Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("cchess"),
                    FabricBlockEntityTypeBuilder.create(
                            com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityCChess::new, CCHESS).build());

    /**
     * 五子棋棋盘（上游 {@code InitBlocks.GOMOKU}）。同样是 3×3 多方块，只有中心格带方块实体。
     */
    public static final Block GOMOKU = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("gomoku"), new com.github.tartaricacid.touhoulittlemaid.block.BlockGomoku());

    public static final BlockEntityType<com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku> GOMOKU_TE =
            Registry.register(Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("gomoku"),
                    FabricBlockEntityTypeBuilder.create(
                            com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku::new, GOMOKU).build());

    /**
     * 国际象棋棋盘（上游 {@code InitBlocks.WCHESS}）。同样是 3×3 多方块。
     */
    public static final Block WCHESS = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("wchess"), new com.github.tartaricacid.touhoulittlemaid.block.BlockWChess());

    public static final BlockEntityType<com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityWChess> WCHESS_TE =
            Registry.register(Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("wchess"),
                    FabricBlockEntityTypeBuilder.create(
                            com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityWChess::new, WCHESS).build());

    // ------------------------------------------------------------------ 娱乐方块（女仆的「娱乐」行为）

    /**
     * 键盘（上游 {@code InitBlocks.KEYBOARD}）。
     *
     * <p>这三块（键盘 / 书架 / 电脑）与三张棋盘共用 {@code BlockJoy} + {@code TileEntityJoy}：
     * 空手右击会生成一个 {@code EntitySit} 让人坐下，女仆的 {@code MaidJoyTask}
     * 则通过 {@code joy_block} 兴趣点找到没人坐的那一个。区别只在碰撞箱、坐姿与好感度类型。</p>
     *
     * <p>{@code getRenderShape} 是 {@code ENTITYBLOCK_ANIMATED}，所以
     * {@code models/block/keyboard.json} 里只有一个 {@code particle} 贴图 ——
     * 真正的模型由 {@code TileEntityKeyboardRenderer} 用 {@code KeyboardModel} 画。</p>
     */
    public static final Block KEYBOARD = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("keyboard"), new com.github.tartaricacid.touhoulittlemaid.block.BlockKeyboard());

    public static final BlockEntityType<com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityKeyboard> KEYBOARD_TE =
            Registry.register(Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("keyboard"),
                    FabricBlockEntityTypeBuilder.create(
                            com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityKeyboard::new, KEYBOARD).build());

    /**
     * 书架（上游 {@code InitBlocks.BOOKSHELF}）。碰撞箱很矮（5/16 格），女仆是跪坐在上面前看书。
     */
    public static final Block BOOKSHELF = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("bookshelf"), new com.github.tartaricacid.touhoulittlemaid.block.BlockBookshelf());

    public static final BlockEntityType<com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityBookshelf> BOOKSHELF_TE =
            Registry.register(Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("bookshelf"),
                    FabricBlockEntityTypeBuilder.create(
                            com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityBookshelf::new, BOOKSHELF).build());

    /**
     * 电脑（上游 {@code InitBlocks.COMPUTER}）。座位在方块顶面（y=1）。
     */
    public static final Block COMPUTER = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("computer"), new com.github.tartaricacid.touhoulittlemaid.block.BlockComputer());

    public static final BlockEntityType<com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityComputer> COMPUTER_TE =
            Registry.register(Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("computer"),
                    FabricBlockEntityTypeBuilder.create(
                            com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityComputer::new, COMPUTER).build());

    private InitBlocks() {
    }

    public static void init() {
        // 静态字段即完成注册
    }
}
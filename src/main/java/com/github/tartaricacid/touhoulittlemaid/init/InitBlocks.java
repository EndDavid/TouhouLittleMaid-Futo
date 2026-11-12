package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockAltar;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBeacon;
import com.github.tartaricacid.touhoulittlemaid.block.BlockScarecrow;
import com.github.tartaricacid.touhoulittlemaid.block.BlockShrine;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;
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

    private InitBlocks() {
    }

    public static void init() {
        // 静态字段即完成注册
    }
}

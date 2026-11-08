package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBeacon;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;
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
 * <p><b>目前只移植了神社庭灯（maid_beacon）。</b>上游其余 14 个方块
 * （祭坛 / 雕像 / 手办柜 / 模型切换器 / 野餐垫 / 棋类 / 键盘 / 书架 / 电脑 / 神社 / 稻草人 …）
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

    private InitBlocks() {
    }

    public static void init() {
        // 静态字段即完成注册
    }
}

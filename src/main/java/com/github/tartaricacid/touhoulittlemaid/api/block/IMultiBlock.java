package com.github.tartaricacid.touhoulittlemaid.api.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 多方块结构接口，逐字移植自上游 {@code api/block/IMultiBlock}。
 *
 * <p>目前的实现只有 {@code MultiBlockAltar}（祭坛）。触发方式：玩家主手持御币，
 * 对着结构中心的<b>红羊毛</b>右键 —— 见 {@code ItemHakureiGohei#useOn}。</p>
 *
 * <p>为什么不用原版的结构方块 / 结构模板自动判定：祭坛允许「任意原木」，
 * 结构模板里写的是 {@code oak_log}；只有 {@link #isMatch} 才有机会放宽这一条，
 * 所以匹配逻辑必须走这个接口。</p>
 */
public interface IMultiBlock {
    /**
     * 触发构建时，被点击的方块是否为核心方块。
     */
    boolean isCoreBlock(BlockState blockState);

    /**
     * 触发构建时，点击面朝向是否合法（上下两面不行，因为结构是水平朝向的）。
     */
    boolean directionIsSuitable(Direction direction);

    /**
     * 结构中心（= 被点击的那个核心方块）在模板坐标系里的位置。
     */
    BlockPos getCenterPos(Direction direction);

    /**
     * 取该朝向对应的结构模板。
     */
    StructureTemplate getTemplate(ServerLevel world, Direction direction);

    /**
     * 世界里的方块布局是否与模板一致。
     */
    boolean isMatch(Level world, BlockPos posStart, Direction direction, StructureTemplate template);

    /**
     * 真正把方块替换成多方块形态。
     */
    void build(Level worldIn, BlockPos posStart, Direction direction, StructureTemplate template);
}


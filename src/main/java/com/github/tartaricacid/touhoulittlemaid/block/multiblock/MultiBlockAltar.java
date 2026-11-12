package com.github.tartaricacid.touhoulittlemaid.block.multiblock;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMultiBlock;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.mixin.StructureTemplateAccessor;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import com.github.tartaricacid.touhoulittlemaid.util.PosListData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

/**
 * 祭坛的多方块定义，逐字移植自上游 {@code block/multiblock/MultiBlockAltar}。
 *
 * <h2>结构长什么样</h2>
 * <p>四个方向的模板存在 {@code data/touhou_little_maid/structures/altar_*.nbt}：</p>
 * <ul>
 *     <li>底座：一整片红羊毛（中心那一格就是玩家要用御币右键的地方）；</li>
 *     <li>四角：4 根原木柱子，高 3 格；</li>
 *     <li>顶部 {@code y == 2} 的一圈原木 —— 这 4 个位置就是放贡品的槽。</li>
 * </ul>
 *
 * <p>模板里原木写的是 {@code oak_log}，但 {@link #isMatch} 会把这一条放宽成
 * 「任意 {@code #minecraft:logs}」，所以用深色橡木、樱花原木都能搭 ——
 * 这也是为什么必须走 {@link IMultiBlock} 接口而不是原版的结构判定。</p>
 */
public class MultiBlockAltar implements IMultiBlock {
    private static final ResourceLocation ALTAR_SOUTH = new ResourceLocation(TouhouLittleMaid.MOD_ID, "altar_south");
    private static final ResourceLocation ALTAR_NORTH = new ResourceLocation(TouhouLittleMaid.MOD_ID, "altar_north");
    private static final ResourceLocation ALTAR_EAST = new ResourceLocation(TouhouLittleMaid.MOD_ID, "altar_east");
    private static final ResourceLocation ALTAR_WEST = new ResourceLocation(TouhouLittleMaid.MOD_ID, "altar_west");

    /**
     * 四个朝向各自的「模板起点相对核心方块（红羊毛）的偏移」。
     * 这些数字来自结构模板的实际尺寸，不能随意改。
     */
    private static final BlockPos SOUTH_POS = new BlockPos(-4, -3, 0);
    private static final BlockPos NORTH_POS = new BlockPos(-3, -3, -7);
    private static final BlockPos EAST_POS = new BlockPos(0, -3, -3);
    private static final BlockPos WEST_POS = new BlockPos(-7, -3, -4);

    @Override
    public boolean isCoreBlock(BlockState blockState) {
        return blockState.is(Blocks.RED_WOOL);
    }

    @Override
    public boolean isMatch(Level world, BlockPos posStart, Direction direction, StructureTemplate template) {
        // 1.18.2 里 palettes 是 private（上游靠 Forge 的 AT 打开），走 @Accessor
        StructureTemplate.Palette palette = ((StructureTemplateAccessor) template).getPalettes().get(0);

        for (StructureTemplate.StructureBlockInfo blockInfo : palette.blocks()) {
            BlockState worldState = world.getBlockState(posStart.offset(blockInfo.pos));
            BlockState infoState = blockInfo.state;
            // 原木只查 tag，允许任意树种
            if (infoState.is(Blocks.OAK_LOG)) {
                if (!worldState.is(BlockTags.LOGS)) {
                    return false;
                }
            } else if (!worldState.equals(infoState)) {
                // 其他方块必须完全一致（含方块状态）
                return false;
            }
        }
        return true;
    }

    @Override
    public void build(Level worldIn, BlockPos posStart, Direction direction, StructureTemplate template) {
        PosListData posList = new PosListData();
        PosListData canPlaceItemPosList = new PosListData();
        StructureTemplate.Palette palette = ((StructureTemplateAccessor) template).getPalettes().get(0);

        for (StructureTemplate.StructureBlockInfo blockInfo : palette.blocks()) {
            posList.add(posStart.offset(blockInfo.pos));
            // 顶部那一圈原木 = 放贡品的位置
            if (blockInfo.pos.getY() == 2 && blockInfo.state.is(BlockTags.LOGS)) {
                canPlaceItemPosList.add(posStart.offset(blockInfo.pos));
            }
        }

        BlockPos currentCenterPos = posStart.subtract(getCenterPos(direction));
        for (StructureTemplate.StructureBlockInfo blockInfo : palette.blocks()) {
            BlockPos currentPos = posStart.offset(blockInfo.pos);
            BlockState currentState = worldIn.getBlockState(currentPos);
            worldIn.setBlock(currentPos, InitBlocks.ALTAR.defaultBlockState(), Block.UPDATE_ALL);
            BlockEntity te = worldIn.getBlockEntity(currentPos);
            if (te instanceof TileEntityAltar altar) {
                // 只有核心那一格负责渲染整台模型
                boolean isRender = currentPos.equals(currentCenterPos);
                boolean canPlaceItem = blockInfo.pos.getY() == 2 && blockInfo.state.is(BlockTags.LOGS);
                altar.setAltarData(currentState, isRender, canPlaceItem, direction,
                        posList, canPlaceItemPosList);
            }
        }
    }

    @Override
    public boolean directionIsSuitable(Direction direction) {
        return direction != Direction.DOWN && direction != Direction.UP;
    }

    @Override
    public BlockPos getCenterPos(Direction facing) {
        return switch (facing) {
            case NORTH -> SOUTH_POS;
            case EAST -> WEST_POS;
            case WEST -> EAST_POS;
            default -> NORTH_POS;
        };
    }

    @Override
    public StructureTemplate getTemplate(ServerLevel world, Direction facing) {
        return switch (facing) {
            case NORTH -> getAltarTemplate(world, ALTAR_SOUTH);
            case EAST -> getAltarTemplate(world, ALTAR_WEST);
            case WEST -> getAltarTemplate(world, ALTAR_EAST);
            default -> getAltarTemplate(world, ALTAR_NORTH);
        };
    }

    private StructureTemplate getAltarTemplate(ServerLevel world, ResourceLocation location) {
        return world.getStructureManager().getOrCreate(location);
    }
}

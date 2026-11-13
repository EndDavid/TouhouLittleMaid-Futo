package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.pathfinder.PathComputationType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 雕像方块，逐字移植自上游 {@code block/BlockStatue}。
 *
 * <p>一座雕像由 N 个本方块拼成，渲染由核心那格的 {@code TileEntityStatue} 负责
 * （所以 {@code getRenderShape} 是 {@code ENTITYBLOCK_ANIMATED}，方块本身不画东西）。</p>
 *
 * <h2>拆解语义</h2>
 * <p>玩家挖掉<b>任意一格</b>，整座雕像的所有方块都要变回粘土（否则会留下半座
 * 无主的方块实体）。{@code playerWillDestroy} 负责这件事，并按上游的规则
 * 额外掉一块粘土 —— 注意掉的是<b>一块粘土</b>，不是 N 块，也不是手办。</p>
 *
 * <h2>与上游的差异</h2>
 * <table border="1">
 *     <caption>Forge 独占 API 的替代</caption>
 *     <tr><th>上游</th><th>本移植版</th><th>影响</th></tr>
 *     <tr><td>{@code onBlockExploded}</td><td>{@code wasExploded}
 *         （在方块变空气<b>之前</b>调用）</td><td>无</td></tr>
 *     <tr><td>{@code initializeClient}（粘土碎屑粒子）</td><td>无对应物</td>
 *         <td>挖掘时掉的是石头质感粒子，不是粘土色</td></tr>
 * </table>
 */
public class BlockStatue extends Block implements EntityBlock {
    /**
     * 是不是「迷你雕像」（只有 1×1×1 那种）。
     *
     * <p>它的作用是：只有迷你雕像会 {@code randomTick}，也就是只有它能被火烧成手办。
     * 大雕像被烧不会变形 —— 上游如此，别当成 bug 改掉。</p>
     */
    public static final BooleanProperty IS_TINY = BooleanProperty.create("is_tiny");

    public BlockStatue() {
        super(BlockBehaviour.Properties.of(Material.CLAY).strength(1, 2).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(IS_TINY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(IS_TINY);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityStatue(pos, state);
    }

    @Override
    public void playerWillDestroy(Level worldIn, BlockPos pos, BlockState state, Player player) {
        if (!worldIn.isClientSide) {
            this.getStatue(worldIn, pos).ifPresent(statue -> {
                this.restoreClayBlock(worldIn, pos, statue);
                if (!player.isCreative()) {
                    popResource(worldIn, pos, new ItemStack(Blocks.CLAY));
                }
            });
        }
        super.playerWillDestroy(worldIn, pos, state, player);
    }

    /**
     * 爆炸也要把整座雕像还原成粘土，否则会留下一堆指向已消失核心的方块实体。
     *
     * <p>上游写的是 {@code onBlockExploded}（Forge 独占，在方块<b>已经</b>变空气之后调用）；
     * 1.18.2 的对应物是 {@code wasExploded}，它在方块变空气<b>之前</b>调用 ——
     * 所以这里能安全地读到本格的方块实体。</p>
     */
    @Override
    public void wasExploded(Level world, BlockPos pos, Explosion explosion) {
        if (!world.isClientSide) {
            this.getStatue(world, pos).ifPresent(statue -> this.restoreClayBlock(world, pos, statue));
        }
        super.wasExploded(world, pos, explosion);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter worldIn, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(IS_TINY);
    }

    /**
     * 迷你雕像脚下方块是火时，烧成手办（女仆数据原样搬过去）。
     */
    @Override
    public void randomTick(BlockState state, ServerLevel worldIn, BlockPos pos, Random random) {
        if (worldIn.getBlockState(pos.below()).is(Blocks.FIRE)) {
            this.getStatue(worldIn, pos).ifPresent(statue -> {
                worldIn.setBlockAndUpdate(pos, InitBlocks.GARAGE_KIT.defaultBlockState());
                worldIn.levelEvent(net.minecraft.world.level.block.LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
                BlockEntity te = worldIn.getBlockEntity(pos);
                if (te instanceof TileEntityGarageKit garageKit && statue.getExtraMaidData() != null) {
                    garageKit.setData(statue.getFacing(), statue.getExtraMaidData());
                }
            });
        }
    }

    private Optional<TileEntityStatue> getStatue(BlockGetter world, BlockPos pos) {
        BlockEntity te = world.getBlockEntity(pos);
        if (te instanceof TileEntityStatue statue) {
            return Optional.of(statue);
        }
        return Optional.empty();
    }

    /**
     * 把这座雕像除 {@code pos} 以外的所有方块还原成粘土（{@code pos} 自己由调用方
     * 所在的破坏流程处理）。
     */
    private void restoreClayBlock(@Nonnull Level worldIn, @Nonnull BlockPos pos, TileEntityStatue statue) {
        List<BlockPos> posList = statue.getAllBlocks();
        for (BlockPos storagePos : posList) {
            if (!storagePos.equals(pos)) {
                getStatue(worldIn, storagePos).ifPresent(s ->
                        worldIn.setBlock(storagePos, Blocks.CLAY.defaultBlockState(), Block.UPDATE_ALL));
            }
        }
    }
}

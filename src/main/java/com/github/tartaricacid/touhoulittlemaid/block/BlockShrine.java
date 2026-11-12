package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.item.ItemFilm;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityShrine;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 神社（复兴之社）方块，逐字移植自上游 {@code block/BlockShrine}。
 *
 * <h2>流程（三步，全在 {@link #use} 里）</h2>
 * <ol>
 *     <li>空手 + 神社为空 + 手持胶片 → <b>放入胶片</b>；</li>
 *     <li>空手 + 神社里有胶片 → <b>复活</b>：消耗玩家 3/4 的血（血量不足
 *         「上限一半 + 1」时拒绝），在神社上方放出那只女仆，胶片消耗掉；</li>
 *     <li>潜行 + 神社里有胶片 → 把胶片取回来。</li>
 * </ol>
 *
 * <p>复活出来的女仆<b>保留胶片里的全部数据</b>（名字、模型、好感度、经验……），
 * 因为胶片存的就是一份 {@code saveWithoutId} 快照，见 {@link ItemFilm}。</p>
 */
public class BlockShrine extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * 三段式外形：底座 16×5、中段 12×5、顶部 8×6。
     */
    public static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 5, 16),
            Block.box(2, 5, 2, 14, 10, 14),
            Block.box(4, 10, 4, 12, 16, 12));

    public BlockShrine() {
        super(Properties.of(Material.WOOD).sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player playerIn,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND && worldIn.getBlockEntity(pos) instanceof TileEntityShrine shrine) {
            if (playerIn.isShiftKeyDown()) {
                if (!shrine.isEmpty()) {
                    ItemStack storageItem = shrine.extractStorageItem();
                    shrine.refresh();
                    ItemHandlerHelper.giveItemToPlayer(playerIn, storageItem);
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }
            if (shrine.isEmpty()) {
                if (shrine.canInsert(playerIn.getMainHandItem())) {
                    shrine.insertStorageItem(ItemHandlerHelper.copyStackWithSize(playerIn.getMainHandItem(), 1));
                    playerIn.getMainHandItem().shrink(1);
                    shrine.refresh();
                    return InteractionResult.SUCCESS;
                }
                if (!worldIn.isClientSide) {
                    playerIn.sendMessage(new TranslatableComponent(
                            "message.touhou_little_maid.shrine.not_film"), Util.NIL_UUID);
                }
                return InteractionResult.PASS;
            }
            if (playerIn.getMainHandItem().isEmpty()) {
                if (playerIn.getHealth() < (playerIn.getMaxHealth() / 2) + 1) {
                    if (!worldIn.isClientSide) {
                        playerIn.sendMessage(new TranslatableComponent(
                                "message.touhou_little_maid.shrine.health_low"), Util.NIL_UUID);
                    }
                    return InteractionResult.FAIL;
                }
                playerIn.setHealth(0.25f);
                ItemStack film = shrine.getStorageItem();
                ItemFilm.filmToMaid(film, worldIn, pos.above(), playerIn);
                shrine.refresh();
                if (playerIn instanceof ServerPlayer serverPlayer) {
                    InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.SHRINE_REBORN_MAID);
                }
            }
        }
        return super.use(state, worldIn, pos, playerIn, hand, hit);
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        BlockEntity blockEntity = worldIn.getBlockEntity(pos);
        if (blockEntity instanceof TileEntityShrine shrine) {
            ItemStack storageItem = shrine.extractStorageItem();
            if (!storageItem.isEmpty()) {
                Block.popResource(worldIn, pos.offset(0, 1, 0), storageItem);
            }
        }
        super.onRemove(state, worldIn, pos, newState, isMoving);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState blockState) {
        return new TileEntityShrine(pos, blockState);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter worldIn, BlockPos pos,
                                  net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}


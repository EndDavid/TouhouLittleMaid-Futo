package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.block.properties.PicnicMatPart;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemPicnicBasket;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityPicnicMat;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 野餐垫，移植自上游 1.20.1 的 {@code block/BlockPicnicMat}
 * （1.18.2 与 1.20.1 的差异只在方块属性 API，逻辑逐行相同）。
 *
 * <h2>5×5 多方块，但只有中心格有内容</h2>
 * <p>放下野餐篮会一次性铺出 25 格。食物、四张座位、渲染全部以<b>中心格</b>为准 ——
 * 边角格只做两件事：挡住玩家（1/16 格高，走上去会垫脚），以及告诉渲染器「我在哪一侧」。
 * 每格都有自己的方块实体，但它们的 {@code centerPos} 都指回中心
 * （见 {@link #setPlacedBy}），所以拆掉任意一格都能找回中心去取食物。</p>
 *
 * <h2>与 1.20.1 的两处差异</h2>
 * <ol>
 *     <li><b>没有 {@code Properties#forceSolidOn()}。</b>那是 1.19.3+ 才有的，
 *         1.18.2 的 {@code BlockBehaviour.Properties} 里没有这个方法。</li>
 *     <li><b>没有 {@code onBlockExploded}。</b>那是 Forge 钩子。这里统一放进
 *         {@link #onRemove} + {@link #playerWillDestroy}，配合
 *         {@link TileEntityPicnicMat#claimBasketDrop()} 的一次性闸门，
 *         拆任意一格 / 被炸 / 被活塞顶掉都只掉一个野餐篮。</li>
 * </ol>
 */
public class BlockPicnicMat extends Block implements EntityBlock {
    public static final EnumProperty<PicnicMatPart> PART = EnumProperty.create("part", PicnicMatPart.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final VoxelShape AABB = Block.box(0, 0, 0, 16, 1, 16);

    /**
     * 5×5 是以中心为原点、半径 2 的正方形。
     */
    private static final int RADIUS = 2;

    public BlockPicnicMat() {
        super(Properties.of(Material.WOOD).sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(PART, PicnicMatPart.CENTER));
    }

    // ------------------------------------------------------------------ 女仆坐下

    /**
     * 让女仆坐到野餐垫的空位上，由 {@code MaidFindHomeMealTask} 调用。
     *
     * <p>四张座位的位置与朝向是写死的常量偏移（见 {@link #sitPosition}），
     * 与 {@code models/entity/picnic_mat} 里那四个 {@code sit} 方块一一对应：
     * 不跟着 {@code FACING} 转，因为整张贴图的朝向本身就是固定的四边形布局。</p>
     */
    public void startMaidSit(EntityMaid maid, BlockState state, Level worldIn, BlockPos pos) {
        if (!(worldIn instanceof ServerLevel serverLevel)
                || !(worldIn.getBlockEntity(pos) instanceof TileEntityPicnicMat picnicMat)) {
            return;
        }
        // 只能选中中心方块
        if (!state.getValue(PART).isCenter()) {
            return;
        }
        // 遍历，寻找是否有空位
        boolean hasEmptySit = false;
        int sitIndex = -1;
        for (UUID uuid : picnicMat.getSitIds()) {
            sitIndex++;
            if (uuid.equals(Util.NIL_UUID)) {
                hasEmptySit = true;
                break;
            }
            Entity oldSitEntity = serverLevel.getEntity(uuid);
            if (oldSitEntity == null || !oldSitEntity.isAlive()) {
                hasEmptySit = true;
                break;
            }
        }
        if (!hasEmptySit) {
            return;
        }
        Vec3 sitPosition = sitPosition(sitIndex);
        Vec3 sitPos = new Vec3(pos.getX() + sitPosition.x, pos.getY() + sitPosition.y + 0.0625,
                pos.getZ() + sitPosition.z);
        EntitySit newSitEntity = new EntitySit(worldIn, sitPos, Type.ON_HOME_MEAL.getTypeName(), pos);
        // 朝外坐：四个座位分别在四个象限，取「从中心指向自己」的方向
        double y = sitPosition.z < 0 ? -1 : 1;
        double x = sitPosition.x < 0 ? -1 : 1;
        double rotOffset = Math.toDegrees(Math.atan2(y, x));
        newSitEntity.setYRot((float) rotOffset + 90);
        worldIn.addFreshEntity(newSitEntity);
        picnicMat.setSitId(sitIndex, newSitEntity.getUUID());
        maid.startRiding(newSitEntity);
    }

    /**
     * 四张座位相对中心格的偏移。索引即 {@code SitIds} 里的下标。
     *
     * <p>数值来自上游（{@code 2,2} / {@code -1,2} / {@code -1,-1} / {@code 2,-1}）——
     * 它们不是「四个角」，而是模型里那四块小垫子所在的位置，别按对称性去改。</p>
     */
    private Vec3 sitPosition(int sitIndex) {
        switch (sitIndex) {
            case 1:
                return new Vec3(-1, 0, 2);
            case 2:
                return new Vec3(-1, 0, -1);
            case 3:
                return new Vec3(2, 0, -1);
            case 0:
            default:
                return new Vec3(2, 0, 2);
        }
    }

    // ------------------------------------------------------------------ 交互

    @Override
    public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player playerIn, InteractionHand hand, BlockHitResult hit) {
        if (worldIn.isClientSide) {
            return InteractionResult.PASS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(worldIn.getBlockEntity(pos) instanceof TileEntityPicnicMat picnicMat)) {
            return InteractionResult.FAIL;
        }
        BlockPos centerPos = picnicMat.getCenterPos();
        if (!(worldIn.getBlockEntity(centerPos) instanceof TileEntityPicnicMat picnicMatCenter)) {
            return InteractionResult.FAIL;
        }
        ItemStack itemInHand = playerIn.getItemInHand(hand);
        if (itemInHand.isEdible()) {
            return placeFood(itemInHand, picnicMatCenter);
        }
        // 潜行 + 空手 = 从最后往前取一份食物出来
        if (itemInHand.isEmpty() && playerIn.isDiscrete()) {
            return takeFood(playerIn, picnicMatCenter);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult placeFood(ItemStack food, TileEntityPicnicMat picnicMatCenter) {
        int count = food.getCount();
        ItemStack resultStack = ItemHandlerHelper.insertItemStacked(picnicMatCenter.getHandler(), food.copy(), false);
        picnicMatCenter.refresh();
        // 一份都没塞进去就当作没发生（否则手上那一组会被白白标记成「已交互」）
        int shrinkCount = count - resultStack.getCount();
        if (shrinkCount <= 0) {
            return InteractionResult.FAIL;
        }
        food.shrink(shrinkCount);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult takeFood(Player playerIn, TileEntityPicnicMat picnicMatCenter) {
        ItemStackHandler handler = picnicMatCenter.getHandler();
        int size = handler.getSlots() - 1;
        for (int i = size; i >= 0; i--) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStack outputStack = handler.extractItem(i, handler.getSlotLimit(i), false);
                picnicMatCenter.refresh();
                ItemHandlerHelper.giveItemToPlayer(playerIn, outputStack);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.FAIL;
    }

    // ------------------------------------------------------------------ 放置与拆除

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos centerPos = context.getClickedPos();
        for (int i = -RADIUS; i <= RADIUS; i++) {
            for (int j = -RADIUS; j <= RADIUS; j++) {
                BlockPos searchPos = centerPos.offset(i, 0, j);
                if (!context.getLevel().getBlockState(searchPos).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(worldIn, pos, state, placer, stack);
        if (worldIn.isClientSide) {
            return;
        }
        for (int i = -RADIUS; i <= RADIUS; i++) {
            for (int j = -RADIUS; j <= RADIUS; j++) {
                BlockPos searchPos = pos.offset(i, 0, j);
                // 正中心的不用放置（它就是被放下的那一格，方块实体已经存在）
                if (!searchPos.equals(pos)) {
                    worldIn.setBlock(searchPos, state.setValue(PART, PicnicMatPart.SIDE), Block.UPDATE_ALL);
                }
                if (worldIn.getBlockEntity(searchPos) instanceof TileEntityPicnicMat picnicMat) {
                    picnicMat.setCenterPos(pos);
                }
            }
        }
        // 把野餐篮里的食物搬进中心方块
        if (worldIn.getBlockEntity(pos) instanceof TileEntityPicnicMat picnicMat
                && stack.is(InitItems.PICNIC_BASKET)) {
            picnicMat.setHandler(ItemPicnicBasket.getContainer(stack));
        }
    }

    @Override
    public boolean canSurvive(BlockState blockState, LevelReader level, BlockPos blockPos) {
        BlockPos below = blockPos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        handlePicnicMatRemove(world, pos, state);
        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        // 只在「这个方块真的消失了」时处理（setPlacedBy 铺周围 24 格时 newState 仍是同一个方块）
        if (!isMoving && !newState.is(state.getBlock())) {
            handlePicnicMatRemove(world, pos, state);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    /**
     * 拆掉任意一格 → 清空整张垫子、掉出一个装着剩余食物的野餐篮、丢掉四个座位实体。
     *
     * <p><b>为什么要一次性闸门：</b>这个方法会在两种时机被调用（玩家破坏走
     * {@code playerWillDestroy}，爆炸 / 活塞 / {@code /setblock} 走 {@code onRemove}），
     * 而它内部又用 {@code setBlockAndUpdate(AIR)} 清掉其余 24 格 ——
     * 那 24 格各自也会触发一次 {@code onRemove}，形成有限次递归
     * （已变成空气的格子不会再命中 {@code is(this)}），但<b>野餐篮会被掉出 25 次</b>。
     * {@code claimBasketDrop()} 让「掉篮子」这件事在整个结构里只成功一次。</p>
     */
    private void handlePicnicMatRemove(Level world, BlockPos pos, BlockState state) {
        if (world.isClientSide) {
            return;
        }
        if (!(world.getBlockEntity(pos) instanceof TileEntityPicnicMat picnicMat)) {
            return;
        }
        BlockPos centerPos = picnicMat.getCenterPos();
        if (world.getBlockEntity(centerPos) instanceof TileEntityPicnicMat picnicMatCenter) {
            if (picnicMatCenter.claimBasketDrop()) {
                ItemStack stack = new ItemStack(InitItems.PICNIC_BASKET);
                ItemPicnicBasket.setContainer(stack, picnicMatCenter.getHandler());
                popResource(world, centerPos, stack);
            }
            if (world instanceof ServerLevel serverLevel) {
                for (UUID uuid : picnicMatCenter.getSitIds()) {
                    if (uuid.equals(Util.NIL_UUID)) {
                        continue;
                    }
                    Entity entity = serverLevel.getEntity(uuid);
                    if (entity instanceof EntitySit) {
                        entity.discard();
                    }
                }
            }
        }
        for (int i = -RADIUS; i <= RADIUS; i++) {
            for (int j = -RADIUS; j <= RADIUS; j++) {
                BlockPos offset = centerPos.offset(i, 0, j);
                if (world.getBlockState(offset).is(InitBlocks.PICNIC_MAT)) {
                    world.setBlockAndUpdate(offset, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    // ------------------------------------------------------------------ 杂项

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityPicnicMat(pos, state);
    }

    /**
     * 方块本身一格都不画，全部交给 {@code TileEntityPicnicMatRenderer}。
     * 所以 {@code blockstates/picnic_mat.json} 里只有一个 {@code particle} 贴图。
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter worldIn, BlockPos pos, PathComputationType type) {
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return AABB;
    }

    @Override
    public BlockState rotate(BlockState pState, Rotation pRot) {
        return pState.setValue(FACING, pRot.rotate(pState.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState pState, Mirror pMirror) {
        return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
    }
}


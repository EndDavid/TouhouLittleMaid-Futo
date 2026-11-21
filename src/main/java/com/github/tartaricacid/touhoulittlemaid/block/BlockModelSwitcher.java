package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.item.ItemModelSwitcher;
import com.github.tartaricacid.touhoulittlemaid.network.OpenSwitcherGuiPacket;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityModelSwitcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 模型切换器方块，移植自上游 {@code block/BlockModelSwitcher}。
 *
 * <p>它不是「装饰方块」而是<b>展示装置</b>：用物品右击一只女仆完成绑定，
 * 放下方块、在界面里配好若干「模式」之后，往它的<b>左右两侧</b>各接一根红石线，
 * 每来一次脉冲就把女仆的模型 / 头顶文字 / 朝向换成下一个模式。</p>
 *
 * <h2>为什么红石要从侧面接</h2>
 * <p>{@link #canConnectRedstone} 只放行 {@code FACING} 的两个侧向 ——
 * 正面留给玩家右击开界面，背面通常贴着墙。这样「左右各一根线」就等于
 * 「上一张 / 下一张」，不需要额外做两个输入口。</p>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li>{@code BlockBehaviour.Properties} 而不是 1.20 的 {@code Properties.of()}（1.18.2 同名同形，
 *         只是上游用了 {@code MapColor} 的默认值，这里同样不指定）。</li>
 *     <li>网络发包换成 Fabric 的 {@code ServerPlayNetworking#send}（见 {@link OpenSwitcherGuiPacket}）。</li>
 * </ol>
 */
public class BlockModelSwitcher extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockModelSwitcher() {
        super(Properties.of(Material.STONE).sound(SoundType.STONE).strength(50.0F, 1200.0F));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /*
     * 上游重写了 Forge 的 Block#canConnectRedstone，让两侧的红石线「拐弯指进方块」。
     *
     * 这个方法在 1.18.2 原版里**不存在**（用 javap 把整个 net/minecraft 的 class 全扫过，
     * canConnectRedstone 一次都没有出现），它是 Forge 给 BlockBehaviour 打的补丁。
     * 而它纯粹是外观：红石线要不要把线拉到这一侧。
     *
     * 功能上并不需要它 —— RedStoneWireBlock#getSignal(state, level, pos, dir) 的实现是
     * `return state.getValue(POWER);`，根本不看 dir，所以「线在这一侧没弯过来」也照样能把
     * 功率读给邻居。本类读的是 level.getSignal(左/右邻居, 那一侧)，与线的外观无关。
     */

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityModelSwitcher(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * 红石脉冲：只有「无信号 → 有信号」那一次才算数，所以用 {@code isPowered} 记边沿。
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        boolean leftSignal = level.getSignal(pos.offset(facing.getCounterClockWise().getNormal()),
                facing.getCounterClockWise()) > 0;
        boolean rightSignal = level.getSignal(pos.offset(facing.getClockWise().getNormal()),
                facing.getClockWise()) > 0;
        boolean hasSignal = leftSignal || rightSignal;
        if (!(level.getBlockEntity(pos) instanceof TileEntityModelSwitcher switcher)) {
            return;
        }
        if (switcher.isPowered() == hasSignal) {
            return;
        }
        switcher.setPowered(!switcher.isPowered());
        if (!switcher.isPowered()) {
            return;
        }
        UUID uuid = switcher.getUuid();
        if (uuid == null) {
            return;
        }
        int size = switcher.getInfoList().size();
        if (size <= 0) {
            // 列表为空时不要算下标：上游这里会除以零取模，本移植版直接跳过
            return;
        }
        int index = calculateIndex(leftSignal, size, switcher.getIndex());
        switcher.setIndex(index);
        Entity entity = level instanceof ServerLevel serverLevel ? serverLevel.getEntity(uuid) : null;
        if (entity instanceof EntityMaid maid && entity.isAlive()) {
            this.setMaidData(switcher, maid);
        }
    }

    /**
     * 把当前模式应用到女仆身上。
     *
     * <p>文字为空时<b>要显式关掉</b> {@code customNameVisible} 并把名字清空 ——
     * 否则上一张模式留下的名字会一直挂在头顶（{@code setCustomName(null)} 只是清除自定义名，
     * 可见性标志是独立的）。</p>
     */
    private void setMaidData(TileEntityModelSwitcher switcher, EntityMaid maid) {
        TileEntityModelSwitcher.ModeInfo modelInfo = switcher.getModelInfo();
        if (modelInfo == null) {
            return;
        }
        maid.setModelId(modelInfo.getModelId().toString());
        if (StringUtils.isNotBlank(modelInfo.getText())) {
            maid.setCustomName(Component.nullToEmpty(modelInfo.getText()));
            maid.setCustomNameVisible(true);
        } else {
            maid.setCustomName(null);
            maid.setCustomNameVisible(false);
        }
        BlockPos blockPos = maid.blockPosition();
        maid.setPos(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);
        maid.setYRot(modelInfo.getDirection().toYRot());
        maid.setYHeadRot(modelInfo.getDirection().toYRot());
        maid.setYBodyRot(modelInfo.getDirection().toYRot());
    }

    private int calculateIndex(boolean leftSignal, int size, int index) {
        if (leftSignal) {
            return index < size - 1 ? index + 1 : 0;
        }
        return index > 0 ? index - 1 : size - 1;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof TileEntityModelSwitcher) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                OpenSwitcherGuiPacket.send(serverPlayer, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    /**
     * 放下方块时把物品里存的「绑定 UUID + 模式列表」搬进方块实体。
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof TileEntityModelSwitcher switcher) {
            ItemModelSwitcher.itemStackToTileEntity(stack, switcher);
            switcher.refresh();
        }
    }

    /**
     * 拆掉方块时把方块实体的内容装回物品再掉出来。
     *
     * <p>不能靠战利品表：那张表只会掉一个「空白」的方块物品，绑定信息与模式列表会丢。
     * 上游同样在这里手动 {@code popResource}，并且<b>不</b>在战利品表里再掉一份。</p>
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving) {
            if (level.getBlockEntity(pos) instanceof TileEntityModelSwitcher switcher) {
                popResource(level, pos, ItemModelSwitcher.tileEntityToItemStack(switcher));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * 方块本体走普通 JSON 模型（{@code models/block/model_switcher.json}），
     * 不像祭坛 / 棋盘那样由方块实体渲染器画。
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}


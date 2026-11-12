package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.AltarItemHandler;
import com.github.tartaricacid.touhoulittlemaid.util.PosListData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 祭坛（多方块）的方块实体，移植自上游 {@code tileentity/TileEntityAltar}。
 *
 * <h2>它为什么「每个祭坛格都有一份」</h2>
 * <p>祭坛是一座多方块结构，整座由 8？（这里按结构模板而定）个独立方块组成，
 * <b>每个方块都是同一个 {@code BlockAltar} + 同一个方块实体类型</b>。彼此靠下面这些字段保持一致：</p>
 * <ul>
 *     <li>{@code blockPosList}：整座祭坛的所有坐标；</li>
 *     <li>{@code canPlaceItemPosList}：顶部 4 根原木（能放贡品的位置）；</li>
 *     <li>{@code storageState}：建坛之前这里原本是什么方块 —— 拆坛时用它还原；</li>
 *     <li>{@code isRender}：只有<b>一个</b>方块实体负责画那台大模型，
 *         否则整座模型会被画 N 遍（N = 祭坛占的方块数），叠在一起又卡又闪；</li>
 *     <li>{@code direction}：整座结构朝向，渲染时据此旋转。</li>
 * </ul>
 *
 * <h2>与上游的差异</h2>
 * <p>上游把数据塞进 Forge 独有的 {@code getTileData()}（一个不参与原版序列化的旁路
 * {@link CompoundTag}），再在 {@code saveAdditional} 里把它和原版数据一起落盘。
 * Fabric 没有那层旁路，这里直接写进 {@code saveAdditional} 的 tag、从 {@code load}
 * 的 tag 读 —— 落盘结果与上游逐字段等价。</p>
 */
public class TileEntityAltar extends BlockEntity {
    private static final String STORAGE_ITEM = "StorageItem";
    private static final String IS_RENDER = "IsRender";
    private static final String CAN_PLACE_ITEM = "CanPlaceItem";
    private static final String STORAGE_STATE_ID = "StorageBlockStateId";
    private static final String DIRECTION = "Direction";
    private static final String STORAGE_BLOCK_LIST = "StorageBlockList";
    private static final String CAN_PLACE_ITEM_POS_LIST = "CanPlaceItemPosList";

    public final AltarItemHandler handler = new AltarItemHandler();

    private boolean isRender = false;
    private boolean canPlaceItem = false;
    private BlockState storageState = Blocks.AIR.defaultBlockState();
    private PosListData blockPosList = new PosListData();
    private PosListData canPlaceItemPosList = new PosListData();
    private Direction direction = Direction.SOUTH;

    public TileEntityAltar(BlockPos blockPos, BlockState blockState) {
        super(InitBlocks.ALTAR_TE, blockPos, blockState);
    }

    /**
     * 建坛时由 {@code MultiBlockAltar#build} 一次性写入整份描述。
     */
    public void setAltarData(BlockState storageState, boolean isRender, boolean canPlaceItem,
                             Direction direction, PosListData blockPosList,
                             PosListData canPlaceItemPosList) {
        this.isRender = isRender;
        this.canPlaceItem = canPlaceItem;
        this.storageState = storageState;
        this.direction = direction;
        this.blockPosList = blockPosList;
        this.canPlaceItemPosList = canPlaceItemPosList;
        refresh();
    }

    @Override
    public void saveAdditional(CompoundTag compound) {
        compound.putBoolean(IS_RENDER, isRender);
        compound.putBoolean(CAN_PLACE_ITEM, canPlaceItem);
        compound.putInt(STORAGE_STATE_ID, Block.getId(storageState));
        compound.put(STORAGE_ITEM, handler.serializeNBT());
        compound.putString(DIRECTION, direction.getSerializedName());
        compound.put(STORAGE_BLOCK_LIST, blockPosList.serialize());
        compound.put(CAN_PLACE_ITEM_POS_LIST, canPlaceItemPosList.serialize());
        super.saveAdditional(compound);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        isRender = nbt.getBoolean(IS_RENDER);
        canPlaceItem = nbt.getBoolean(CAN_PLACE_ITEM);
        storageState = Block.stateById(nbt.getInt(STORAGE_STATE_ID));
        handler.deserializeNBT(nbt.getCompound(STORAGE_ITEM));
        Direction dir = Direction.byName(nbt.getString(DIRECTION));
        direction = dir == null ? Direction.SOUTH : dir;
        blockPosList.deserialize(nbt.getList(STORAGE_BLOCK_LIST, Tag.TAG_COMPOUND));
        canPlaceItemPosList.deserialize(nbt.getList(CAN_PLACE_ITEM_POS_LIST, Tag.TAG_COMPOUND));
    }

    /**
     * 整座祭坛跨度约 8×5×8，默认的「刚好一个方块」的裁剪盒会让模型被切掉大半。
     *
     * <p><b>没有 {@code @Override}：</b>{@code BlockEntity#getRenderBoundingBox} 是 Forge
     * 给 {@code BlockEntity} 打的补丁，原版 1.18.2 里不存在这个方法。Fabric 侧靠
     * {@code BlockEntityRenderer#shouldRenderOffScreen()} 关掉视锥裁剪，
     * 所以这个方法现在只是「给渲染器读的元数据」，而不是被引擎调用的钩子。</p>
     */
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-9, -5, -9), worldPosition.offset(9, 5, 9));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 数据变了就通知客户端重建 —— 祭坛的模型与贡品全靠方块实体同步。
     */
    public void refresh() {
        this.setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    public boolean isRender() {
        return isRender;
    }

    public boolean isCanPlaceItem() {
        return canPlaceItem;
    }

    public BlockState getStorageState() {
        return storageState;
    }

    public PosListData getBlockPosList() {
        return blockPosList;
    }

    public PosListData getCanPlaceItemPosList() {
        return canPlaceItemPosList;
    }

    /**
     * 这个位置上的贡品。放在底盘上的那些祭坛格不参与合成，因此直接返回空。
     */
    public ItemStack getStorageItem() {
        if (canPlaceItem) {
            return handler.getStackInSlot(0);
        }
        return ItemStack.EMPTY;
    }

    public Direction getDirection() {
        return direction;
    }
}

package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * 神社（复兴之社）的方块实体，移植自上游 {@code tileentity/TileEntityShrine}。
 *
 * <p>只存一格，且<b>只接受胶片</b>（{@code touhou_little_maid:film}）——
 * 神社的唯一用途就是「用胶片把一只死去的女仆换回来」：
 * 放入胶片 → 空手右键 → 消耗 3/4 的生命值，女仆在神社上方复活。</p>
 *
 * <p>与上游的差异同 {@link TileEntityAltar}：数据直接写进原版 tag，
 * 不再经过 Forge 的 {@code getTileData()} 旁路。</p>
 */
public class TileEntityShrine extends BlockEntity {
    private static final String STORAGE_ITEM = "StorageItem";

    private final ItemStackHandler handler = new ItemStackHandler() {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem() == InitItems.FILM;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public TileEntityShrine(BlockPos pos, BlockState blockState) {
        super(InitBlocks.SHRINE_TE, pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.put(STORAGE_ITEM, handler.serializeNBT());
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        handler.deserializeNBT(nbt.getCompound(STORAGE_ITEM));
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
     * 数据变了就同步给客户端 —— 神社上方那块悬浮旋转的胶片完全靠这个。
     */
    public void refresh() {
        this.setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    public ItemStack getStorageItem() {
        return handler.getStackInSlot(0);
    }

    public void insertStorageItem(ItemStack stack) {
        handler.insertItem(0, stack, false);
    }

    public ItemStack extractStorageItem() {
        return handler.extractItem(0, 1, false);
    }

    public boolean isEmpty() {
        return handler.getStackInSlot(0).isEmpty();
    }

    public boolean canInsert(ItemStack stack) {
        return handler.isItemValid(0, stack);
    }
}


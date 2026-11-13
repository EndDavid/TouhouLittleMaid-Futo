package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 手办方块实体，逐字移植自上游 {@code tileentity/TileEntityGarageKit}。
 *
 * <p>比 {@link TileEntityStatue} 简单得多：手办永远是<b>单格</b>方块，
 * 只有「朝向」和「一份实体 NBT」两个字段。</p>
 *
 * <p>它同时也是「雕像被火烧掉」的落点：{@code BlockStatue#randomTick} 发现脚下是火时，
 * 会把这一格换成 {@code GARAGE_KIT} 并把雕像里的女仆数据搬过来 ——
 * 表现就是「粘土雕像被烧成了手办」，女仆模型不丢。</p>
 */
public class TileEntityGarageKit extends BlockEntity {
    private static final String FACING_TAG = "GarageKitFacing";
    private static final String EXTRA_DATA = "ExtraData";

    private Direction facing = Direction.NORTH;
    private CompoundTag extraData = new CompoundTag();

    public TileEntityGarageKit(BlockPos pos, BlockState state) {
        super(InitBlocks.GARAGE_KIT_TE, pos, state);
    }

    @Override
    public void saveAdditional(CompoundTag compound) {
        compound.putString(FACING_TAG, this.facing.getSerializedName());
        compound.put(EXTRA_DATA, this.extraData);
        super.saveAdditional(compound);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        Direction loadedFacing = Direction.byName(nbt.getString(FACING_TAG));
        this.facing = loadedFacing == null ? Direction.NORTH : loadedFacing;
        this.extraData = nbt.getCompound(EXTRA_DATA);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public Direction getFacing() {
        return this.facing;
    }

    public CompoundTag getExtraData() {
        return this.extraData;
    }

    /**
     * 写入朝向与实体数据，并让客户端重读。
     *
     * <p>放置（{@code setPlacedBy}）、拿刷怪蛋点它、雕像烧成手办，三条路径都走这里，
     * 所以通知逻辑只写一份。</p>
     */
    public void setData(Direction facing, CompoundTag extraData) {
        this.facing = facing;
        this.extraData = extraData;
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.level.getBlockState(this.worldPosition);
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
        }
    }
}


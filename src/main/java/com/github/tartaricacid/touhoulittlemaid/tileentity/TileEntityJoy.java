package com.github.tartaricacid.touhoulittlemaid.tileentity;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 娱乐方块（棋类 / 键盘 / 书架 / 电脑）的共同基类，移植自上游 {@code tileentity/TileEntityJoy}。
 *
 * <p>它只做一件事：记住「坐在上面的实体是谁」。方块自己不保存座位，
 * 座位是一个独立的 {@code EntitySit}（不参与碰撞、不会移动、没有乘客十 tick 后自毁），
 * 于是玩家和女仆可以坐同一张椅子而互不干扰，方块被拆掉时也只需要 {@code discard()} 那一个实体。</p>
 *
 * <h2>与上游的两处差异</h2>
 * <ol>
 *     <li><b>不再经过 {@code getTileData()}。</b>那是 Forge 提供的懒创建子标签，1.18.2 没有；
 *         {@code saveAdditional} 收到的 {@code tag} 本来就是方块实体自己的标签，直接写进去即可。</li>
 *     <li><b>没有 {@code getRenderBoundingBox()}</b>。1.18.2 的 {@code BlockEntity} 根本没有这个钩子
 *         （1.20 才有 {@code BlockEntityRenderer#getRenderBoundingBox}）。棋盘是 3×3 格、
 *         渲染器只挂在中心那一格上，因此中心离开视锥时整张桌子会一起消失。
 *         要修只能 Mixin {@code LevelRenderer}，与本移植版雕像渲染盒的处理方式一致 —— 属于已知限制。</li>
 * </ol>
 */
public abstract class TileEntityJoy extends BlockEntity {
    private static final String SIT_ID = "SitId";
    private UUID sitId = Util.NIL_UUID;

    public TileEntityJoy(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.putUUID(SIT_ID, this.sitId);
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.sitId = nbt.getUUID(SIT_ID);
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
     * 把方块实体的数据推给客户端。
     *
     * <p><b>为什么要单独一个方法：</b>{@code setChanged()} 只标脏存档、写盘，
     * 不会发 {@code ClientboundBlockEntityDataPacket}。棋盘的每一步都要让客户端重画棋子，
     * 而棋子数据是存在方块实体里的（不在同步数据里），少了这一步客户端会一直显示初始棋局。</p>
     */
    public void refresh() {
        this.setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    public UUID getSitId() {
        return this.sitId;
    }

    public void setSitId(UUID sitId) {
        this.sitId = sitId;
    }
}


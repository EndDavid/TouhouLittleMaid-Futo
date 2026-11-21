package com.github.tartaricacid.touhoulittlemaid.tileentity;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
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
import java.util.UUID;

/**
 * 野餐垫的方块实体，移植自上游 {@code tileentity/TileEntityPicnicMat}。
 *
 * <p><b>只有中心那一格有这个方块实体</b>（见 {@code BlockPicnicMat#setPlacedBy}：
 * 其余 24 格虽然也 {@code setBlock}，但它们的方块实体都由同一句
 * {@code setCenterPos} 指回中心）。所以食物、座位、渲染都以中心格为准，
 * 边角格访问到的方块实体身上这些字段其实是空的 ——
 * 上游所有调用点都先 {@code getCenterPos()} 再取方块实体，这里照抄。</p>
 *
 * <h2>与上游的两处差异（1.18.2 框架差异）</h2>
 * <ol>
 *     <li><b>不经过 {@code getTileData()}。</b>那是 Forge 提供的懒创建子标签，
 *         1.18.2 没有；{@code saveAdditional} 收到的 {@code tag} 本身就是方块实体自己的标签。</li>
 *     <li><b>没有 {@code getRenderBoundingBox()}。</b>1.18.2 的 {@code BlockEntity} 根本没有这个钩子
 *         （要 1.20 才有 {@code BlockEntityRenderer#getRenderBoundingBox}）。野餐垫是 5×5、
 *         渲染器只挂在中心格上，所以中心离开视锥时整张垫子会一起消失 ——
 *         与棋盘 / 雕像同一个已知限制。</li>
 * </ol>
 */
public class TileEntityPicnicMat extends BlockEntity {
    private static final String CENTER_POS_NAME = "CenterPos";
    private static final String STORAGE_ITEM = "StorageItem";
    private static final String SIT_IDS = "SitIds";
    /**
     * 四张座位。{@code NIL_UUID} 表示空位，具体顺序见 {@code BlockPicnicMat#sitPosition}。
     */
    private static final int SIT_COUNT = 4;

    private final ItemStackHandler handler = new ItemStackHandler(9) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem().isEdible();
        }
    };
    private final UUID[] sitIds = new UUID[]{Util.NIL_UUID, Util.NIL_UUID, Util.NIL_UUID, Util.NIL_UUID};
    private BlockPos centerPos = BlockPos.ZERO;
    /**
     * 「野餐篮已经掉过了」的一次性闸门。
     *
     * <p>拆掉 25 格里的任意一格都会走到 {@code BlockPicnicMat#handlePicnicMatRemove}，
     * 而那个方法内部又会把其余格子改成空气、再各自触发一次 —— 不做闸门的话
     * 一次拆除会掉出 25 个野餐篮。字段<b>不入存档</b>：它与这一份结构共存亡。</p>
     */
    private boolean basketDropped = false;

    public TileEntityPicnicMat(BlockPos pos, BlockState blockState) {
        super(com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.PICNIC_MAT_TE, pos, blockState);
    }

    public void setSitId(int index, UUID uuid) {
        if (index < 0 || index >= SIT_COUNT) {
            return;
        }
        this.sitIds[index] = uuid;
        this.refresh();
    }

    public UUID[] getSitIds() {
        return this.sitIds;
    }

    public BlockPos getCenterPos() {
        return this.centerPos;
    }

    public void setCenterPos(BlockPos centerPos) {
        this.centerPos = centerPos;
        this.refresh();
    }

    public ItemStack getStorageItem(int slotId) {
        return this.handler.getStackInSlot(slotId);
    }

    public boolean isEmpty(int slotId) {
        return this.handler.getStackInSlot(slotId).isEmpty();
    }

    public ItemStackHandler getHandler() {
        return this.handler;
    }

    /**
     * 把一整个物品栏复制进来（放置野餐垫时从野餐篮的 NBT 里搬）。
     *
     * <p>越界的槽位直接放弃而不是抛异常：野餐篮的格数由物品 NBT 决定，
     * 玩家从旧版本升级上来的存档里可能出现更多格，那种情况应该「多出来的丢掉」，
     * 而不是让放置动作崩在半路、留下一个 24 格已被拆掉的残局。</p>
     */
    public void setHandler(ItemStackHandler stackHandler) {
        for (int i = 0; i < stackHandler.getSlots(); i++) {
            if (i >= this.handler.getSlots()) {
                return;
            }
            this.handler.setStackInSlot(i, stackHandler.getStackInSlot(i));
        }
        this.refresh();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.put(CENTER_POS_NAME, NbtUtils.writeBlockPos(this.centerPos));
        tag.put(STORAGE_ITEM, this.handler.serializeNBT());
        ListTag listTag = new ListTag();
        for (UUID uuid : this.sitIds) {
            listTag.add(NbtUtils.createUUID(uuid));
        }
        tag.put(SIT_IDS, listTag);
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains(CENTER_POS_NAME, Tag.TAG_COMPOUND)) {
            this.centerPos = NbtUtils.readBlockPos(nbt.getCompound(CENTER_POS_NAME));
        }
        if (nbt.contains(STORAGE_ITEM, Tag.TAG_COMPOUND)) {
            this.handler.deserializeNBT(nbt.getCompound(STORAGE_ITEM));
        }
        ListTag sitIdsTag = nbt.getList(SIT_IDS, Tag.TAG_INT_ARRAY);
        int i = 0;
        for (Tag uuidTag : sitIdsTag) {
            if (i >= SIT_COUNT) {
                break;
            }
            this.sitIds[i] = NbtUtils.loadUUID(uuidTag);
            i++;
        }
    }

    /**
     * 标脏 + 把方块实体数据推给客户端。
     *
     * <p>{@code setChanged()} 只负责写盘，不会发 {@code ClientboundBlockEntityDataPacket}；
     * 而食物列表、四张座位都是存在方块实体里的（不在同步数据里），
     * 少了 {@code sendBlockUpdated} 客户端会一直画着空垫子。</p>
     */
    public void refresh() {
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.level.getBlockState(this.worldPosition);
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
        }
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

    /**
     * 「让野餐篮掉出来」这件事只允许成功一次，返回 {@code true} 的那一次才真正掉落。
     * 供 {@code BlockPicnicMat#handlePicnicMatRemove} 使用，理由见 {@link #basketDropped}。
     */
    public boolean claimBasketDrop() {
        if (this.basketDropped) {
            return false;
        }
        this.basketDropped = true;
        return true;
    }
}

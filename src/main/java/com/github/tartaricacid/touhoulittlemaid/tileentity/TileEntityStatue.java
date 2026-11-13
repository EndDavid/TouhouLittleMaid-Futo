package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 雕像方块实体，逐字移植自上游 {@code tileentity/TileEntityStatue}。
 *
 * <p>一座雕像由 N 个 {@code BlockStatue} 方块拼出来（1×1×1 / 1×2×1 / 2×4×2 / 3×6×3），
 * 每个方块都有自己的方块实体，但<b>只有「核心」那个负责渲染与持有女仆数据</b>。
 * 其余方块只记住核心在哪，拆的时候据此把整座雕像还原成粘土。</p>
 *
 * <h2>三个字段的分工</h2>
 * <table border="1">
 *     <caption>字段表</caption>
 *     <tr><th>字段</th><th>作用</th></tr>
 *     <tr><td>{@code size}</td><td>尺寸枚举，同时决定缩放（0.5 / 1 / 2 / 3 倍）与占位体积</td></tr>
 *     <tr><td>{@code coreBlockPos}</td><td>核心方块坐标。非核心方块靠它找到「老大」</td></tr>
 *     <tr><td>{@code allBlocks}</td><td>整座雕像所有方块坐标，拆解时按它逐个还原成粘土</td></tr>
 * </table>
 *
 * <p>{@code extraMaidData} 只存在核心方块上；别的方块存的是 {@code null}，
 * 存了也没人读。</p>
 */
public class TileEntityStatue extends BlockEntity {
    private static final String STATUE_SIZE_TAG = "StatueSize";
    private static final String CORE_BLOCK_TAG = "CoreBlock";
    private static final String CORE_BLOCK_POS_TAG = "CoreBlockPos";
    private static final String STATUE_FACING_TAG = "StatueFacing";
    private static final String ALL_BLOCKS_TAG = "AllBlocks";
    private static final String EXTRA_MAID_DATA = "ExtraMaidData";

    private Size size = Size.SMALL;
    private boolean isCoreBlock = false;
    private BlockPos coreBlockPos = BlockPos.ZERO;
    private Direction facing = Direction.NORTH;
    private List<BlockPos> allBlocks = Lists.newArrayList();
    @Nullable
    private CompoundTag extraMaidData = null;

    public TileEntityStatue(BlockPos pos, BlockState state) {
        super(InitBlocks.STATUE_TE, pos, state);
    }

    /**
     * 方块被 {@code ItemChisel} 凿出来时由它一次写全。
     *
     * <p>注意<b>每个</b>方块都会调一次这个方法：核心那格传 {@code isCoreBlock = true}
     * 且带 {@code extraData}，其余格子传 {@code false} 与 {@code null}。</p>
     */
    public void setForgeData(Size size, boolean isCoreBlock, BlockPos coreBlockPos, Direction facing,
                             List<BlockPos> allBlocks, @Nullable CompoundTag extraData) {
        this.size = size;
        this.isCoreBlock = isCoreBlock;
        this.coreBlockPos = coreBlockPos;
        this.facing = facing;
        this.allBlocks = allBlocks;
        this.extraMaidData = extraData;
        this.refresh();
    }

    @Override
    public void saveAdditional(CompoundTag compound) {
        // 上游把字段写进 getTileData()（Forge 的旁路 tag），这里直接写进 compound。
        // 落盘结果逐字段等价，只是少了一层中转。
        compound.putInt(STATUE_SIZE_TAG, this.size.ordinal());
        compound.putBoolean(CORE_BLOCK_TAG, this.isCoreBlock);
        compound.put(CORE_BLOCK_POS_TAG, NbtUtils.writeBlockPos(this.coreBlockPos));
        compound.putString(STATUE_FACING_TAG, this.facing.getSerializedName());
        ListTag blockList = new ListTag();
        for (BlockPos pos : this.allBlocks) {
            blockList.add(NbtUtils.writeBlockPos(pos));
        }
        compound.put(ALL_BLOCKS_TAG, blockList);
        if (this.extraMaidData != null) {
            compound.put(EXTRA_MAID_DATA, this.extraMaidData);
        }
        super.saveAdditional(compound);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.size = Size.getSizeByIndex(nbt.getInt(STATUE_SIZE_TAG));
        this.isCoreBlock = nbt.getBoolean(CORE_BLOCK_TAG);
        this.coreBlockPos = NbtUtils.readBlockPos(nbt.getCompound(CORE_BLOCK_POS_TAG));
        Direction loadedFacing = Direction.byName(nbt.getString(STATUE_FACING_TAG));
        // byName 对未知/空字符串返回 null，上游没兜底；方块状态里存的是有效值，
        // 但读档损坏时宁可退回 NORTH 也不要让后续 switch 抛 NPE。
        this.facing = loadedFacing == null ? Direction.NORTH : loadedFacing;
        this.allBlocks.clear();
        ListTag blockList = nbt.getList(ALL_BLOCKS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < blockList.size(); i++) {
            this.allBlocks.add(NbtUtils.readBlockPos(blockList.getCompound(i)));
        }
        if (nbt.contains(EXTRA_MAID_DATA, Tag.TAG_COMPOUND)) {
            this.extraMaidData = nbt.getCompound(EXTRA_MAID_DATA);
        } else {
            this.extraMaidData = null;
        }
    }

    /**
     * 渲染包围盒。大雕像（3×6×3）远超一个方块，不扩大的话走远一点模型就被裁掉。
     */
    // 1.18.2 culls block entities through the RENDERER, not the block entity itself.
    // The real box lives in TileEntityStatueRenderer.
    public AABB renderBoundingBoxCompat() {
        return new AABB(this.worldPosition.offset(-5, -1, -5), this.worldPosition.offset(5, 10, 5));
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
     * 存档 + 通知客户端重读。上游同名方法。
     */
    public void refresh() {
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.level.getBlockState(this.worldPosition);
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    public Size getSize() {
        return this.size;
    }

    public boolean isCoreBlock() {
        return this.isCoreBlock;
    }

    public BlockPos getCoreBlockPos() {
        return this.coreBlockPos;
    }

    public Direction getFacing() {
        return this.facing;
    }

    public List<BlockPos> getAllBlocks() {
        return this.allBlocks;
    }

    @Nullable
    public CompoundTag getExtraMaidData() {
        return this.extraMaidData;
    }

    /**
     * 雕像尺寸。{@code scale} 是渲染缩放，{@code dimension} 是占位方块体积。
     *
     * <p>顺序即「从小到大」，{@code ItemChisel} 会<b>从大到小</b>尝试放置，
     * 所以 {@code ordinal()} 越小代表凿子消耗的耐久越少。</p>
     */
    public enum Size {
        TINY(0.5F, new Vec3i(1, 1, 1)),
        SMALL(1.0F, new Vec3i(1, 2, 1)),
        MIDDLE(2.0F, new Vec3i(2, 4, 2)),
        BIG(3.0F, new Vec3i(3, 6, 3));

        private final float scale;
        private final Vec3i dimension;

        Size(float scale, Vec3i dimension) {
            this.scale = scale;
            this.dimension = dimension;
        }

        public static Size getSizeByIndex(int index) {
            return Size.values()[Mth.clamp(index, 0, Size.values().length - 1)];
        }

        public float getScale() {
            return scale;
        }

        public Vec3i getDimension() {
            return dimension;
        }
    }
}


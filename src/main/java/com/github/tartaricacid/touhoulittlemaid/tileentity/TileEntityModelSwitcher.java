package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 模型切换器的方块实体，移植自上游 {@code tileentity/TileEntityModelSwitcher}。
 *
 * <p>它存三样东西：</p>
 * <ol>
 *     <li><b>绑定女仆的 UUID</b> —— 手持方块右击女仆时由 {@code ItemModelSwitcher} 写进<b>物品</b> NBT，
 *         方块放下后再搬到这里；</li>
 *     <li><b>模式列表</b> —— 每个条目是一个 {@link ModeInfo}（模型 id + 显示名 + 朝向），
 *         由界面编辑；</li>
 *     <li><b>当前下标</b> —— 左右两侧来一次红石脉冲就往前走/往后走一格，
 *         然后把这个条目的模型 / 名字 / 朝向应用到女仆身上。</li>
 * </ol>
 *
 * <h2>与上游的一处差异</h2>
 * <p>上游用 Forge 的 {@code getPersistentData()}（懒创建的子标签，最终序列化到 {@code ForgeData} 下）。
 * 1.18.2 没有这个东西，这里直接写进 {@code saveAdditional} 收到的 tag —— 也是本移植版
 * 所有方块实体的统一做法（见 {@code TileEntityPicnicMat} 的相同说明）。</p>
 */
public class TileEntityModelSwitcher extends BlockEntity {
    public static final String INFO_LIST = "info_list";
    public static final String ENTITY_UUID = "entity_uuid";
    public static final String LIST_INDEX = "list_index";

    private List<ModeInfo> infoList = Lists.newArrayList();
    private boolean isPowered;
    @Nullable
    private UUID uuid;
    private int index;

    public TileEntityModelSwitcher(BlockPos pos, BlockState blockState) {
        super(InitBlocks.MODEL_SWITCHER_TE, pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (ModeInfo info : this.infoList) {
            listTag.add(info.serialize());
        }
        tag.put(INFO_LIST, listTag);
        if (this.uuid != null) {
            tag.put(ENTITY_UUID, NbtUtils.createUUID(this.uuid));
        }
        tag.putInt(LIST_INDEX, this.index);
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.infoList.clear();
        ListTag listTag = tag.getList(INFO_LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            ModeInfo info = new ModeInfo();
            info.deserialize(listTag.getCompound(i));
            this.infoList.add(info);
        }
        Tag uuidTag = tag.get(ENTITY_UUID);
        if (uuidTag != null) {
            this.uuid = NbtUtils.loadUUID(uuidTag);
        }
        this.index = tag.getInt(LIST_INDEX);
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

    @Nullable
    public UUID getUuid() {
        return this.uuid;
    }

    public int getIndex() {
        return this.index;
    }

    public void setIndex(int index) {
        this.index = index;
        this.refresh();
    }

    public boolean isPowered() {
        return this.isPowered;
    }

    /**
     * 只记标志位，<b>不</b>标脏 —— 它只用于「信号上升沿」判定，不需要存档。
     * 上游同样没有刷新。
     */
    public void setPowered(boolean powered) {
        this.isPowered = powered;
    }

    @Nullable
    public ModeInfo getModelInfo() {
        if (0 <= this.index && this.index < this.infoList.size()) {
            return this.infoList.get(this.index);
        }
        return null;
    }

    public List<ModeInfo> getInfoList() {
        return this.infoList;
    }

    public void setInfoList(List<ModeInfo> infoList) {
        this.infoList = infoList;
        this.refresh();
    }

    /**
     * 标脏 + 推给客户端。列表与下标都在方块实体里（不在同步数据里），
     * 少了 {@code sendBlockUpdated} 客户端界面会一直显示旧内容。
     */
    public void refresh() {
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.level.getBlockState(this.worldPosition);
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * 一个「模式」：用哪个模型、头顶显示什么名字、朝哪个方向。
     *
     * <p>三个字段都在界面里编辑，并且要同时经由 NBT（存档 / 物品）与网络（S2C 界面数据、C2S 保存）
     * 两条路走，所以读写方法写成一对 {@code serialize/deserialize} + {@code toBuf/fromBuf}。</p>
     */
    public static class ModeInfo {
        private ResourceLocation modelId;
        private String text;
        private Direction direction;

        public ModeInfo() {
        }

        public ModeInfo(ResourceLocation modelId, String text, Direction direction) {
            this.modelId = modelId;
            this.text = text;
            this.direction = direction;
        }

        public static ModeInfo fromBuf(FriendlyByteBuf buf) {
            return new ModeInfo(buf.readResourceLocation(), buf.readUtf(),
                    Direction.from2DDataValue(buf.readVarInt()));
        }

        public ResourceLocation getModelId() {
            return this.modelId;
        }

        public void setModelId(ResourceLocation modelId) {
            this.modelId = modelId;
        }

        public String getText() {
            return this.text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Direction getDirection() {
            return this.direction;
        }

        public void setDirection(Direction direction) {
            this.direction = direction;
        }

        public void toBuf(FriendlyByteBuf buf) {
            buf.writeResourceLocation(this.modelId);
            buf.writeUtf(this.text);
            buf.writeVarInt(this.direction.get2DDataValue());
        }

        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putString("model_id", this.modelId.toString());
            tag.putString("text", this.text);
            tag.putInt("direction", this.direction.get2DDataValue());
            return tag;
        }

        public void deserialize(CompoundTag nbt) {
            this.modelId = new ResourceLocation(nbt.getString("model_id"));
            this.text = nbt.getString("text");
            this.direction = Direction.from2DDataValue(nbt.getInt("direction"));
        }
    }
}


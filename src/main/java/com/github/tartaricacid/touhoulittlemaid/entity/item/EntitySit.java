package com.github.tartaricacid.touhoulittlemaid.entity.item;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.StringUtils;

/**
 * 「座位」实体，移植自上游 {@code entity/item/EntitySit}。
 *
 * <p>它是个彻底的哑实体：不移动、不被推动、打不掉、不参与碰撞，只负责让玩家 / 女仆
 * 挂在自己身上坐着。方块的 {@code SitId} 指向它，于是「这张棋盘现在有人吗」只要查一次实体表。</p>
 *
 * <p><b>为什么不用方块自带的座位逻辑：</b>原版没有「坐下」的通用机制（船 / 矿车都是独立实体），
 * 而玩家坐下的位置必须能被服务端记录、被客户端插值 —— 只有实体能做这件事。</p>
 *
 * <h2>十 tick 自毁</h2>
 * <p>{@link #checkPassengers()} 在没有人坐满 10 tick 之后 {@code discard()}。
 * 这解决的是「玩家坐下 → 方块被拆 → 座位实体留在半空」这种残留：拆方块时
 * {@code BlockJoy#onRemove} 会主动丢掉它，但区块卸载 / 崩服 / 其它模组强拆都可能绕过那一步，
 * 有了这条兜底，残留最多存在半秒。</p>
 */
public class EntitySit extends Entity {
    private static final EntityDataAccessor<String> SIT_TYPE =
            SynchedEntityData.defineId(EntitySit.class, EntityDataSerializers.STRING);
    private int passengerTick = 0;
    private BlockPos associatedBlockPos = BlockPos.ZERO;

    public EntitySit(EntityType<?> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
    }

    public EntitySit(Level worldIn, Vec3 pos, String joyType, BlockPos associatedBlockPos) {
        this(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.SIT, worldIn);
        this.setPos(pos);
        this.setJoyType(joyType);
        this.associatedBlockPos = associatedBlockPos;
    }

    /**
     * 乘客坐在座位「里面」而不是顶上：座位本身只有 0.1 格高，不往下压乘客会浮着。
     */
    @Override
    public double getPassengersRidingOffset() {
        return -0.25;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SIT_TYPE, "");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("SitJoyType", Tag.TAG_STRING)) {
            this.setJoyType(tag.getString("SitJoyType"));
        }
        if (tag.contains("AssociatedBlockPos", Tag.TAG_COMPOUND)) {
            this.associatedBlockPos = NbtUtils.readBlockPos(tag.getCompound("AssociatedBlockPos"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (StringUtils.isNotBlank(this.getJoyType())) {
            tag.putString("SitJoyType", this.getJoyType());
        }
        tag.put("AssociatedBlockPos", NbtUtils.writeBlockPos(this.associatedBlockPos));
    }

    @Override
    public void tick() {
        if (!this.level.isClientSide) {
            this.checkOutOfWorld();
            this.checkPassengers();
            if (this.getFirstPassenger() instanceof EntityMaid maid) {
                this.tickMaid(maid);
            }
        }
    }

    /**
     * 每 20 tick 给一次好感度，并检查「这个作息 / 这个工作模式允许继续坐着吗」。
     */
    private void tickMaid(EntityMaid maid) {
        // 坐姿由座位主导：不每 tick 覆盖朝向的话，女仆的 brain 会把她扭回自己原来的朝向
        maid.setYRot(this.getYRot());
        maid.setYHeadRot(this.getYRot());
        if (tickCount % 20 == 0) {
            FavorabilityManager manager = maid.getFavorabilityManager();
            String joyType = this.getJoyType();
            IMaidTask task = maid.getTask();

            manager.apply(joyType);
            // 空闲状态下娱乐方块可以随便坐
            if (this.isIdleSchedule(maid)) {
                return;
            }
            // 工作状态下只有「允许坐在娱乐方块上」的模式（棋类工作）可以继续坐
            if (this.isWorkSchedule(maid) && task.canSitInJoy(maid, joyType)) {
                return;
            }
            // 否则不允许在上面待着
            maid.stopRiding();
        }
    }

    private void checkPassengers() {
        if (this.getPassengers().isEmpty()) {
            passengerTick++;
        } else {
            passengerTick = 0;
        }
        if (passengerTick > 10) {
            this.discard();
        }
    }

    private boolean isIdleSchedule(EntityMaid maid) {
        return maid.getScheduleDetail() == Activity.IDLE;
    }

    private boolean isWorkSchedule(EntityMaid maid) {
        return maid.getScheduleDetail() == Activity.WORK;
    }

    public BlockPos getAssociatedBlockPos() {
        return associatedBlockPos;
    }

    @Override
    public boolean skipAttackInteraction(Entity pEntity) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        return false;
    }

    @Override
    public void move(MoverType pType, Vec3 pPos) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double pX, double pY, double pZ) {
    }

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    @Override
    public void thunderHit(ServerLevel pLevel, LightningBolt pLightning) {
    }

    @Override
    public void refreshDimensions() {
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    /**
     * 生成生成包。
     *
     * <p>1.18.2 的 {@code Entity#getAddEntityPacket} 是 <b>abstract</b>（javap 确认），
     * 没有父类默认实现可以转发。上游用 Forge 的
     * {@code NetworkHooks.getEntitySpawningPacket}；原版对应物就是
     * {@link ClientboundAddEntityPacket}，它会把实体类型、UUID、坐标、旋转与数据
     * 一并下发。座位实体没有自定义拖尾数据，所以直接用它就够。</p>
     */
    @Override
    public Packet<?> getAddEntityPacket() {
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this);
    }

    public String getJoyType() {
        return this.entityData.get(SIT_TYPE);
    }

    public void setJoyType(String type) {
        this.entityData.set(SIT_TYPE, type);
    }
}


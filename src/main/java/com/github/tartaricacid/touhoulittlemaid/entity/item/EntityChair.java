package com.github.tartaricacid.touhoulittlemaid.entity.item;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoChairEntity;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.ChairConfig;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemChair;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 坐垫（自定义坐垫）实体，移植自上游 {@code entity/item/EntityChair}。
 *
 * <p>它是一个「可以被骑的装饰物」：右键坐上去，蹲下右键打开换肤界面，蹲下左键拆除回收。
 * 模型来自 {@code maid_chair.json} 里声明的一串模型 id（默认 {@code touhou_little_maid:cushion}）。
 *
 * <p><b>与上游的差异：</b>
 * <ul>
 *     <li>{@code isPickable()} 用 {@code level.isClientSide()} 代替 Forge 的 {@code EffectiveSide}，
 *         理由同 {@link AbstractEntityFromItem}。</li>
 *     <li>{@code getBoundingBoxForCulling()} 没有移植：它依赖上游 {@code BedrockModel#getRenderBoundingBox()}，
 *         本移植版把旧版模型统一转成了 {@code GeoModel}，没有现成的包围盒缓存。
 *         失去的只是「模型很大但实体框在屏幕外时仍能画出来」这一优化，不影响正确性。</li>
 *     <li>{@code getDim()}（1.12 时代遗留的维度序数）没有移植，没有任何调用点。</li>
 * </ul>
 */
public class EntityChair extends AbstractEntityFromItem {
    private static final EntityDataAccessor<String> MODEL_ID =
            SynchedEntityData.defineId(EntityChair.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> MOUNTED_HEIGHT =
            SynchedEntityData.defineId(EntityChair.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> TAMEABLE_CAN_RIDE =
            SynchedEntityData.defineId(EntityChair.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(EntityChair.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final String MODEL_ID_TAG = "ModelId";
    private static final String MOUNTED_HEIGHT_TAG = "MountedHeight";
    private static final String TAMEABLE_CAN_RIDE_TAG = "TameableCanRide";
    private static final String OWNER_UUID_TAG = "OwnerUUID";

    private static final String DEFAULT_MODEL_ID = "touhou_little_maid:cushion";

    @Environment(EnvType.CLIENT)
    private GeckoChairEntity animatable;

    protected EntityChair(EntityType<EntityChair> type, Level level) {
        super(type, level);
        if (level.isClientSide()) {
            this.animatable = new GeckoChairEntity(this);
        }
    }

    public EntityChair(Level level) {
        this(InitEntities.CHAIR, level);
    }

    public EntityChair(Level level, double x, double y, double z, float yaw) {
        this(level);
        this.setPos(x, y, z);
        this.setRot(yaw, 0);
    }

    /**
     * 供 {@code InitEntities} 使用。尺寸与上游 {@code TYPE} 一致：0.875 × 0.5。
     */
    public static EntityType<EntityChair> createType() {
        return EntityType.Builder.<EntityChair>of(EntityChair::new, MobCategory.MISC)
                .sized(0.875F, 0.5F)
                .clientTrackingRange(10)
                .build(TouhouLittleMaid.MOD_ID + ":chair");
    }

    /**
     * 属性表。
     *
     * <p><b>这个方法是必须的，漏了会在构造实体时直接 NPE。</b>
     * {@link LivingEntity} 的构造函数里就调了 {@code setHealth(getMaxHealth())}，
     * 而 {@code getMaxHealth()} 读的是 {@code Attributes.MAX_HEALTH}；
     * {@code AttributeMap} 在找不到实例时会回落到构造时传进来的 {@code AttributeSupplier} ——
     * Fabric 那边这个 supplier 由 {@code FabricDefaultAttributeRegistry} 提供，
     * <b>没有注册就是 {@code null}</b>，于是：</p>
     *
     * <pre>
     * NullPointerException: Cannot invoke "AttributeSupplier.getValue(Attribute)"
     *     because "this.supplier" is null
     *   at AttributeMap.getValue
     *   at LivingEntity.getMaxHealth
     *   at LivingEntity.&lt;init&gt;
     * </pre>
     *
     * <p>上游（Forge）不需要显式写这一步，因为 Forge 给未注册的实体兜了默认属性表；
     * Fabric 没有那个兜底，凡是直接或间接继承 {@code LivingEntity} 的类型都必须注册。
     * 坐垫用的是 {@link LivingEntity#createLivingAttributes()}（生命 / 击退抗性 / 移速 /
     * 护甲 / 护甲韧性），与上游「当方盒子用」的定位一致 —— 它不移动，也从不掉血。</p>
     */
    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MODEL_ID, DEFAULT_MODEL_ID);
        this.entityData.define(MOUNTED_HEIGHT, 0.0F);
        this.entityData.define(TAMEABLE_CAN_RIDE, true);
        this.entityData.define(OWNER_UUID, Optional.empty());
    }

    /**
     * 有生物走到坐垫上时自动让它坐下（可骑的坐垫才有这个行为）。
     */
    @Override
    protected void pushEntities() {
        if (!isTameableCanRide()) {
            return;
        }
        if (!this.level.isClientSide) {
            List<TamableAnimal> list = this.level.getEntitiesOfClass(TamableAnimal.class,
                    getBoundingBox().expandTowards(0, 0.5, 0),
                    e -> !e.isInSittingPose() && !e.isPassenger() && e.getPassengers().isEmpty());
            list.stream().findFirst().ifPresent(entity -> entity.startRiding(this));
        }
    }

    /**
     * 此参数会影响钓鱼钩和客户端的渲染交互，因此只在客户端返回 true。
     */
    @Override
    public boolean isPickable() {
        return this.level.isClientSide();
    }

    @NotNull
    @Override
    public InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (player.getItemInHand(hand).interactLivingEntity(player, this, hand).consumesAction()) {
                return InteractionResult.SUCCESS;
            }
            // TODO(换肤界面)：上游在这里发 OpenChairGuiMessage 让客户端打开 ChairModelGui。
            // 那个界面是 AbstractModelGui 的子类，而本移植版的 AbstractModelGui 目前把模型类型
            // 钉死在 MaidModelInfo 上，需要先把它泛型化才能复用。在此之前蹲下右键保持无动作，
            // 换模型只能通过创造模式物品栏里的各个坐垫变体来达成。
        } else if (!this.level.isClientSide && getPassengers().isEmpty() && !player.isPassenger()) {
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public double getPassengersRidingOffset() {
        return getMountedHeight();
    }

    @Override
    protected boolean canKillEntity(Player player) {
        if (ChairConfig.CHAIR_CAN_DESTROYED_BY_ANYONE.get()) {
            return true;
        }
        return this.getOwnerUUID().map(uuid -> player.getUUID().equals(uuid)).orElse(true);
    }

    @Override
    protected SoundEvent getHitSound() {
        return SoundEvents.WOOL_BREAK;
    }

    @Override
    protected Item getWithItem() {
        return InitItems.CHAIR;
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(MODEL_ID_TAG, Tag.TAG_STRING)) {
            setModelId(compound.getString(MODEL_ID_TAG));
        }
        if (compound.contains(MOUNTED_HEIGHT_TAG, Tag.TAG_FLOAT)) {
            setMountedHeight(compound.getFloat(MOUNTED_HEIGHT_TAG));
        }
        if (compound.contains(TAMEABLE_CAN_RIDE_TAG, Tag.TAG_BYTE)) {
            setTameableCanRide(compound.getBoolean(TAMEABLE_CAN_RIDE_TAG));
        }
        if (compound.contains(OWNER_UUID_TAG)) {
            setOwnerUUID(NbtUtils.loadUUID(Objects.requireNonNull(compound.get(OWNER_UUID_TAG))));
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString(MODEL_ID_TAG, getModelId());
        compound.putFloat(MOUNTED_HEIGHT_TAG, getMountedHeight());
        compound.putBoolean(TAMEABLE_CAN_RIDE_TAG, isTameableCanRide());
        this.getOwnerUUID().ifPresent(uuid -> compound.putUUID(OWNER_UUID_TAG, uuid));
    }

    @Nullable
    @Override
    public Entity getControllingPassenger() {
        Entity firstPassenger = getFirstPassenger();
        if (firstPassenger instanceof LivingEntity) {
            return firstPassenger;
        }
        return super.getControllingPassenger();
    }

    public String getModelId() {
        return this.entityData.get(MODEL_ID);
    }

    public void setModelId(String modelId) {
        this.entityData.set(MODEL_ID, modelId);
    }

    public float getMountedHeight() {
        return this.entityData.get(MOUNTED_HEIGHT);
    }

    public void setMountedHeight(float height) {
        this.entityData.set(MOUNTED_HEIGHT, Mth.clamp(height, -0.5F, 2.5F));
    }

    public boolean isTameableCanRide() {
        return this.entityData.get(TAMEABLE_CAN_RIDE);
    }

    public void setTameableCanRide(boolean canRide) {
        this.entityData.set(TAMEABLE_CAN_RIDE, canRide);
    }

    public Optional<UUID> getOwnerUUID() {
        return this.entityData.get(OWNER_UUID);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    public void setOwner(@Nullable Player player) {
        if (player != null) {
            this.setOwnerUUID(player.getUUID());
        }
    }

    public boolean hasPassenger() {
        return !getPassengers().isEmpty();
    }

    public float getPassengerYaw() {
        if (!getPassengers().isEmpty()) {
            return getPassengers().get(0).getYRot();
        }
        return 0;
    }

    public float getYaw() {
        return getYRot();
    }

    public float getPassengerPitch() {
        if (!getPassengers().isEmpty()) {
            return getPassengers().get(0).getXRot();
        }
        return 0;
    }

    @Override
    protected ItemStack getKilledStack() {
        return ItemChair.setData(InitItems.CHAIR.getDefaultInstance(),
                new ItemChair.Data(getModelId(), getMountedHeight(), isTameableCanRide(), isNoGravity()));
    }

    @Environment(EnvType.CLIENT)
    public GeckoChairEntity getAnimatableEntity() {
        return this.animatable;
    }
}

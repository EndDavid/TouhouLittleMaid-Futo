package com.github.tartaricacid.touhoulittlemaid.entity.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 「箱子」实体，移植自上游 {@code entity/item/EntityBox}。
 *
 * <p>祭坛的 {@code spawn_box} 配方产出的就是这个东西：一只<b>盒子里坐着一只女仆</b>的蛋糕盒。
 * 它一共三层，玩家每右键一次开一层（{@link #FIRST_STAGE} → 0），开完自动消失并掉 2~4 张纸。
 * 盒子里那只女仆全程挂着隐身效果，所以看起来就是「盒子自己动」。</p>
 *
 * <h2>为什么 {@code baseTick} 要自己写重力</h2>
 * <p>{@link Entity} 不是 {@code LivingEntity}，没有 {@code aiStep} 里那套重力；上游因此
 * 在 {@code baseTick} 最前面手动 {@code move(MoverType.SELF, delta + (0, -0.1, 0))}。
 * 这一句必须留在 {@code super.baseTick()} <b>之前</b>，否则同步包里的坐标会落后一 tick。</p>
 *
 * <h2>与上游的差异</h2>
 * <p>{@code NetworkHooks.getEntitySpawningPacket} 换成原版的 {@link ClientboundAddEntityPacket}
 * —— Forge 那个只是包了一层同名的原版包，没有附加数据。</p>
 */
public class EntityBox extends Entity {
    public static final int FIRST_STAGE = 64;
    public static final int SECOND_STAGE = 60;
    public static final int THIRD_STAGE = 0;
    public static final int MAX_TEXTURE_SIZE = 8;
    private static final EntityDataAccessor<Integer> OPEN_STAGE =
            SynchedEntityData.defineId(EntityBox.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TEXTURE_INDEX =
            SynchedEntityData.defineId(EntityBox.class, EntityDataSerializers.INT);
    private static final String STAGE_TAG = "OpenStage";
    private static final String TEXTURE_TAG = "TextureIndex";

    public EntityBox(EntityType<?> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        this.setRandomTexture();
    }

    @Override
    public void baseTick() {
        if (!this.isNoGravity() && !this.onGround) {
            this.move(MoverType.SELF, this.getDeltaMovement().add(0, -0.1, 0));
        }
        super.baseTick();
        int stage = getOpenStage();
        this.stageChange(stage);
        this.getPassengers().stream().filter(e -> e instanceof TamableAnimal)
                .forEach(e -> applyInvisibilityEffect((TamableAnimal) e, stage));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.getOpenStage() == FIRST_STAGE) {
            setOpenStage(FIRST_STAGE - 1);
            this.playSound(InitSounds.BOX_OPEN, 3.0f, 1.0f);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (this.getOpenStage() == SECOND_STAGE) {
            setOpenStage(SECOND_STAGE - 1);
            this.playSound(InitSounds.BOX_OPEN, 3.0f, 1.0f);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (this.getOpenStage() == THIRD_STAGE) {
            setOpenStage(THIRD_STAGE - 1);
            this.playSound(InitSounds.BOX_OPEN, 3.0f, 2.0f);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.interact(player, hand);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OPEN_STAGE, FIRST_STAGE);
        this.entityData.define(TEXTURE_INDEX, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains(STAGE_TAG)) {
            setOpenStage(compound.getInt(STAGE_TAG));
        }
        if (compound.contains(TEXTURE_TAG)) {
            setTextureIndex(compound.getInt(TEXTURE_TAG));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt(STAGE_TAG, getOpenStage());
        compound.putInt(TEXTURE_TAG, getTextureIndex());
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.isAlive();
    }

    @Override
    public boolean isPickable() {
        return this.isAlive();
    }

    public int getOpenStage() {
        return this.entityData.get(OPEN_STAGE);
    }

    public void setOpenStage(int stage) {
        this.entityData.set(OPEN_STAGE, Mth.clamp(stage, -1, FIRST_STAGE));
    }

    public int getTextureIndex() {
        return this.entityData.get(TEXTURE_INDEX);
    }

    public void setTextureIndex(int index) {
        this.entityData.set(TEXTURE_INDEX, Mth.clamp(index, 1, MAX_TEXTURE_SIZE - 1));
    }

    public void setRandomTexture() {
        setTextureIndex(random.nextInt(MAX_TEXTURE_SIZE));
    }

    private void stageChange(int stage) {
        if (stage != FIRST_STAGE && stage != SECOND_STAGE && stage != THIRD_STAGE) {
            this.setOpenStage(stage - 1);
        }
        if (stage < THIRD_STAGE) {
            this.discard();
            if (!this.level.isClientSide) {
                this.spawnAtLocation(Items.PAPER, 2 + random.nextInt(3));
            }
        }
    }

    private void applyInvisibilityEffect(TamableAnimal tameable, int stage) {
        if (stage >= FIRST_STAGE) {
            tameable.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2, 1, false, false));
        }
    }

    /**
     * 供 {@code InitEntities} 使用，尺寸与上游 {@code TYPE} 一致。
     */
    public static EntityType<EntityBox> createType() {
        return EntityType.Builder.<EntityBox>of(EntityBox::new, MobCategory.MISC)
                .sized(2.0F, 2.0F)
                .clientTrackingRange(10)
                .build(com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid.MOD_ID + ":box");
    }
}


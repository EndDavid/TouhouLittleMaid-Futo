package com.github.tartaricacid.touhoulittlemaid.entity.projectile;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFishing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * 女仆专用鱼钩，移植自上游 {@code entity/projectile/MaidFishingHook}。
 *
 * <p>与原版 {@code FishingHook} 的区别：原版靠「玩家右键收杆」推进状态，
 * 而女仆没有玩家输入，所以这里<b>把「上钩 → 自动收杆」整个流程写进实体自身</b>：
 * 抛出后自动等待、咬钩、并按时收杆结算战利品。</p>
 *
 * <p>与上游的差异（都因为对应子系统未移植）：</p>
 * <ul>
 *     <li>去掉 Forge 的 {@code MaidFishedEvent}（钩子事件），改为直接结算；</li>
 *     <li>去掉进度触发器 {@code MAID_EVENT}（钓到鱼 / 附魔书）；</li>
 *     <li>{@code maid.getLuck()} 是 Forge 给 {@code LivingEntity} 加的方法，这里改由
 *         {@code EntityMaid#getLuck} 按原版「幸运 / 霉运」药水效果换算。</li>
 * </ul>
 */
public class MaidFishingHook extends Entity {
    protected static final EntityDataAccessor<Boolean> DATA_BITING =
            SynchedEntityData.defineId(MaidFishingHook.class, EntityDataSerializers.BOOLEAN);
    protected static final int MAX_OUT_OF_WATER_TIME = 10;
    protected final Random syncronizedRandom = new Random();
    protected final int luck;
    protected final int lureSpeed;
    protected boolean biting;
    protected int nibble;
    protected int timeUntilLured;
    protected int timeUntilHooked;
    protected int outOfWaterTime;
    protected int life;
    protected float fishAngle;
    /**
     * 鎸佹湁鑰咃紙濂充粏锛夈€?
     *
     * <p>娉ㄦ剰锛?.18.2 閲?{@code Projectile} 鐨勬瀯閫犲嚱鏁版槸<b>鍖呯鏈?/b>鐨勶紝妯＄粍绫绘棤娉曠户鎵?
     * 锛堜笂娓搁潬 Forge 鐨?AccessTransformer 鎵撳紑锛夈€傛墍浠ヨ繖閲岀洿鎺ョ户鎵?{@link Entity}锛?
     * 鑷繁缁存姢 owner 瀛楁 鈥斺€?鎴戜滑鏈潵涔熶笉闇€瑕?{@code Projectile} 鐨勭鎾炰激瀹抽€昏緫銆?/p>
     */
    @Nullable
    private Entity owner;
    protected boolean openWater = true;
    protected FishHookState currentState = FishHookState.FLYING;

    protected MaidFishingHook(EntityType<? extends MaidFishingHook> entityType, Level level, int luck, int lureSpeed) {
        super(entityType, level);
        this.noCulling = true;
        this.luck = Math.max(0, luck);
        this.lureSpeed = Math.max(0, lureSpeed);
    }

    public MaidFishingHook(EntityType<MaidFishingHook> entityType, Level level) {
        this(entityType, level, 0, 0);
    }

    public MaidFishingHook(EntityMaid maid, Level level, int luck, int lureSpeed, Vec3 pos) {
        this(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.MAID_FISHING_HOOK, level, luck, lureSpeed);
        this.setOwner(maid);
        this.moveTo(pos);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DATA_BITING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_BITING.equals(key)) {
            this.biting = this.getEntityData().get(DATA_BITING);
            if (this.biting) {
                Vec3 movement = this.getDeltaMovement();
                this.setDeltaMovement(movement.x,
                        -0.4 * Mth.nextFloat(this.syncronizedRandom, 0.6F, 1.0F),
                        movement.z);
            }
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }

    @Override
    public void lerpTo(double pX, double pY, double pZ, float pYaw, float pPitch, int pPosRotationIncrements,
                        boolean pTeleport) {
        // 鱼钩位置由自身物理推进，不做客户端插值（与上游一致）
    }

    @Override
    public void tick() {
        // 每个 tick 换 seed，保证两岸随机序列不一致（与原版一致）
        this.syncronizedRandom.setSeed(this.getUUID().getLeastSignificantBits() ^ this.level.getGameTime());
        super.tick();
        EntityMaid maid = this.getMaidOwner();
        if (maid == null) {
            this.discard();
        } else if (this.level.isClientSide || !this.shouldStopFishing(maid)) {
            if (this.lifeTick()) {
                return;
            }
            maid.getLookControl().setLookAt(this);
            this.fishingTick();
        }
    }

    protected boolean lifeTick() {
        if (this.onGround) {
            // 落在岸上：最多存在 100 tick 就消失
            ++this.life;
            if (this.life >= 100) {
                this.discard();
                return true;
            }
        } else {
            this.life = 0;
        }
        return false;
    }

    protected void fishingTick() {
        BlockPos blockPos = this.blockPosition();
        FluidState fluidState = this.level.getFluidState(blockPos);
        float fluidHeight = this.getFluidHeight(fluidState, blockPos);
        boolean onWaterSurface = fluidHeight > 0;

        if (this.currentState == FishHookState.FLYING) {
            if (onWaterSurface) {
                this.waterSurfaceTick();
                return;
            }
        } else if (this.currentState == FishHookState.BOBBING) {
            this.bobbingTick(blockPos, fluidHeight);
            this.checkOpenWater(blockPos);
            if (onWaterSurface) {
                this.bitingTick(blockPos);
            } else {
                this.outOfWaterTime = Math.min(MAX_OUT_OF_WATER_TIME, this.outOfWaterTime + 1);
            }
        }

        this.fallTick(fluidState);
        this.move(MoverType.SELF, this.getDeltaMovement());
        // 原版 Projectile#updateRotation 是 protected，改用等价的公开工具方法
        net.minecraft.world.entity.projectile.ProjectileUtil.rotateTowardsMovement(this, 0.2F);
        if (this.currentState == FishHookState.FLYING && (this.onGround || this.horizontalCollision)) {
            this.setDeltaMovement(Vec3.ZERO);
        }
        this.setDeltaMovement(this.getDeltaMovement().scale(0.92));
        this.reapplyPosition();
    }

    protected void fallTick(FluidState fluidState) {
        if (!fluidState.is(FluidTags.WATER)) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));
        }
    }

    protected void bitingTick(BlockPos blockPos) {
        this.outOfWaterTime = Math.max(0, this.outOfWaterTime - 1);
        if (this.biting) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D,
                    -0.1D * (double) this.syncronizedRandom.nextFloat() * (double) this.syncronizedRandom.nextFloat(),
                    0.0D));
        }
        if (!this.level.isClientSide) {
            this.catchingFish(blockPos, (ServerLevel) this.level);
        }
    }

    protected void checkOpenWater(BlockPos blockPos) {
        if (this.nibble <= 0 && this.timeUntilHooked <= 0) {
            this.openWater = true;
        } else {
            this.openWater = this.openWater && this.outOfWaterTime < 10 && this.calculateOpenWater(blockPos);
        }
    }

    protected float getFluidHeight(FluidState fluidState, BlockPos blockPos) {
        float fluidHeight = 0;
        if (fluidState.is(FluidTags.WATER)) {
            fluidHeight = fluidState.getHeight(this.level, blockPos);
        }
        return fluidHeight;
    }

    protected void waterSurfaceTick() {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.3D, 0.2D, 0.3D));
        this.currentState = FishHookState.BOBBING;
    }

    protected void bobbingTick(BlockPos blockPos, float fluidHeight) {
        Vec3 movement = this.getDeltaMovement();
        double bobbingY = this.getY() + movement.y - (double) blockPos.getY() - fluidHeight;
        if (Math.abs(bobbingY) < 0.01D) {
            bobbingY += Math.signum(bobbingY) * 0.1;
        }
        this.setDeltaMovement(movement.x * 0.9,
                movement.y - bobbingY * (double) this.random.nextFloat() * 0.2,
                movement.z * 0.9);
    }

    /**
     * 钓鱼状态机：等待 → 咬钩 → 收杆。
     *
     * <p>三个计时器的流转顺序与原版 {@code FishingHook#catchingFish} 完全一致，
     * 只是「收杆」这一步由 {@link #onNibble} 自动完成，不需要玩家点击。</p>
     */
    protected void catchingFish(BlockPos pos, ServerLevel level) {
        int time = 1;
        BlockPos abovePos = pos.above();
        // 下雨会加快上钩
        if (this.random.nextFloat() < 0.25F && level.isRainingAt(abovePos)) {
            ++time;
        }
        // 看不到天空（桥下 / 洞里）会减慢上钩
        if (this.random.nextFloat() < 0.5F && !level.canSeeSky(abovePos)) {
            --time;
        }

        if (this.nibble > 0) {
            --this.nibble;
            this.onNibble(level);
        } else if (this.timeUntilHooked > 0) {
            this.timeUntilHooked -= time;
            if (this.timeUntilHooked > 0) {
                this.fishAngle += (float) (this.random.nextGaussian() * 4.0D);
                float fishAngleRad = this.fishAngle * ((float) Math.PI / 180F);
                float sin = Mth.sin(fishAngleRad);
                float cos = Mth.cos(fishAngleRad);
                double x = this.getX() + sin * this.timeUntilHooked * 0.1;
                double y = Mth.floor(this.getY()) + 1.0;
                double z = this.getZ() + cos * this.timeUntilHooked * 0.1;
                BlockState blockState = level.getBlockState(new BlockPos.MutableBlockPos(x, y - 1.0, z));
                this.spawnFishingParticle(level, blockState, x, y, z, sin, cos);
            } else {
                this.spawnNibbleParticle(level);
                this.nibble = Mth.nextInt(this.random, 20, 40);
                this.getEntityData().set(DATA_BITING, true);
            }
        } else if (this.timeUntilLured > 0) {
            this.timeUntilLured -= time;
            float probability = 0.15F;
            if (this.timeUntilLured < 20) {
                probability += (float) (20 - this.timeUntilLured) * 0.05F;
            } else if (this.timeUntilLured < 40) {
                probability += (float) (40 - this.timeUntilLured) * 0.02F;
            } else if (this.timeUntilLured < 60) {
                probability += (float) (60 - this.timeUntilLured) * 0.01F;
            }
            if (this.random.nextFloat() < probability) {
                float randomRot = Mth.nextFloat(this.random, 0.0F, 360.0F) * ((float) Math.PI / 180F);
                float randomNum = Mth.nextFloat(this.random, 25.0F, 60.0F);
                double x = this.getX() + Mth.sin(randomRot) * randomNum * 0.1;
                double y = Mth.floor(this.getY()) + 1.0;
                double z = this.getZ() + Mth.cos(randomRot) * randomNum * 0.1;
                BlockState blockState = level.getBlockState(new BlockPos.MutableBlockPos(x, y - 1.0, z));
                this.spawnSplashParticle(level, blockState, x, y, z);
            }
            if (this.timeUntilLured <= 0) {
                this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
                this.timeUntilHooked = Mth.nextInt(this.random, 20, 80);
            }
        } else {
            this.timeUntilLured = Mth.nextInt(this.random, 100, 600);
            this.timeUntilLured -= this.lureSpeed * 20 * 5;
        }
    }

    protected void spawnSplashParticle(ServerLevel level, BlockState blockState, double x, double y, double z) {
        if (blockState.is(Blocks.WATER)) {
            level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2 + this.random.nextInt(2),
                    0.1F, 0.0D, 0.1F, 0.0D);
        }
    }

    protected void spawnNibbleParticle(ServerLevel level) {
        double yOffset = this.getY() + 0.5D;
        float bbWidth = this.getBbWidth();
        this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F,
                1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        level.sendParticles(ParticleTypes.BUBBLE, this.getX(), yOffset, this.getZ(),
                (int) (1.0F + bbWidth * 20.0F), bbWidth, 0.0D, bbWidth, 0.2F);
        level.sendParticles(ParticleTypes.FISHING, this.getX(), yOffset, this.getZ(),
                (int) (1.0F + bbWidth * 20.0F), bbWidth, 0.0D, bbWidth, 0.2F);
    }

    protected void spawnFishingParticle(ServerLevel level, BlockState blockState, double x, double y, double z,
                                        float sin, float cos) {
        if (blockState.is(Blocks.WATER)) {
            if (this.random.nextFloat() < 0.15F) {
                level.sendParticles(ParticleTypes.BUBBLE, x, y - 0.1, z, 1, sin, 0.1D, cos, 0.0D);
            }
            float sinOffset = sin * 0.04F;
            float cosOffset = cos * 0.04F;
            level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, cosOffset, 0.01D, -sinOffset, 1.0D);
            level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -cosOffset, 0.01D, sinOffset, 1.0D);
        }
    }

    /**
     * 咬钩后自动收杆。
     *
     * <p>上游在这里留了 TODO：收杆成功率应该和好感度挂钩。本移植版沿用当前行为
     * （随机 2~10 tick 后必定收杆）。</p>
     */
    protected void onNibble(ServerLevel level) {
        EntityMaid maid = this.getMaidOwner();
        int retrieveTime = Mth.nextInt(this.random, 2, 10);
        if (this.nibble <= retrieveTime && maid != null) {
            ItemStack rodItem = maid.getMainHandItem();
            int rodDamage = this.retrieve(rodItem);
            this.hurtRod(maid, rodItem, rodDamage);
            maid.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                    SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F,
                    0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        }
    }

    /**
     * 收杆并结算战利品。
     *
     * @return 鱼竿应扣的耐久（钓到鱼 1，落在岸上 2，无 0）
     */
    public int retrieve(ItemStack stack) {
        EntityMaid maid = this.getMaidOwner();
        if (this.level.isClientSide || maid == null || this.shouldStopFishing(maid)) {
            return 0;
        }
        int rodDamage = 0;
        if (this.nibble > 0) {
            MinecraftServer server = this.level.getServer();
            if (server != null) {
                ServerLevel serverLevel = (ServerLevel) this.level;
                LootContext lootParams = new LootContext.Builder(serverLevel)
                        .withParameter(LootContextParams.ORIGIN, this.position())
                        .withParameter(LootContextParams.TOOL, stack)
                        .withParameter(LootContextParams.THIS_ENTITY, this)
                        .withLuck(this.luck + maid.getLuck())
                        .create(LootContextParamSets.FISHING);
                List<ItemStack> randomItems = this.getLoot(server, lootParams);
                this.addExtraLoot(randomItems);
                this.spawnLoot(randomItems, maid);
                this.afterFishing();
                rodDamage = 1;
            }
        }
        if (this.onGround) {
            rodDamage = 2;
        }
        this.discard();
        return rodDamage;
    }

    protected List<ItemStack> getLoot(MinecraftServer server, LootContext lootParams) {
        LootTable lootTable = server.getLootTables().get(BuiltInLootTables.FISHING);
        return lootTable.getRandomItems(lootParams);
    }

    protected void spawnLoot(List<ItemStack> randomItems, EntityMaid maid) {
        for (ItemStack result : randomItems) {
            ItemEntity itemEntity = new ItemEntity(this.level, maid.getX(), maid.getY() + 0.5, maid.getZ(), result);
            itemEntity.setDeltaMovement(0, 0.1, 0);
            this.level.addFreshEntity(itemEntity);
            this.level.addFreshEntity(new ExperienceOrb(maid.level, maid.getX(), maid.getY() + 0.5,
                    maid.getZ(), this.random.nextInt(6) + 1));
        }
    }

    protected void addExtraLoot(List<ItemStack> randomItems) {
    }

    protected void afterFishing() {
    }

    protected void hurtRod(EntityMaid maid, ItemStack rodItem, int rodDamage) {
        rodItem.hurtAndBreak(rodDamage, maid, m -> m.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    }

    private boolean calculateOpenWater(BlockPos pos) {
        OpenWaterType openWaterType = OpenWaterType.INVALID;
        for (int y = -1; y <= 2; ++y) {
            OpenWaterType openWaterTypeForArea =
                    this.getOpenWaterTypeForArea(pos.offset(-2, y, -2), pos.offset(2, y, 2));
            switch (openWaterTypeForArea) {
                case INVALID:
                    return false;
                case ABOVE_WATER:
                    if (openWaterType == OpenWaterType.INVALID) {
                        return false;
                    }
                    break;
                case INSIDE_WATER:
                    if (openWaterType == OpenWaterType.ABOVE_WATER) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
            openWaterType = openWaterTypeForArea;
        }
        return true;
    }

    private OpenWaterType getOpenWaterTypeForArea(BlockPos firstPos, BlockPos secondPos) {
        return BlockPos.betweenClosedStream(firstPos, secondPos)
                .map(this::getOpenWaterTypeForBlock)
                .reduce((firstType, secondType) -> firstType == secondType ? firstType : OpenWaterType.INVALID)
                .orElse(OpenWaterType.INVALID);
    }

    private OpenWaterType getOpenWaterTypeForBlock(BlockPos blockPos) {
        BlockState blockState = this.level.getBlockState(blockPos);
        if (!blockState.isAir() && !blockState.is(Blocks.LILY_PAD)) {
            FluidState fluidState = blockState.getFluidState();
            return fluidState.is(FluidTags.WATER) && fluidState.isSource()
                   && blockState.getCollisionShape(this.level, blockPos).isEmpty()
                    ? OpenWaterType.INSIDE_WATER
                    : OpenWaterType.INVALID;
        }
        return OpenWaterType.ABOVE_WATER;
    }

    public boolean isOpenWaterFishing() {
        return this.openWater;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.updateOwnerInfo(null);
        super.remove(reason);
    }

    @Override
    public void onClientRemoval() {
        this.updateOwnerInfo(null);
    }

    public void setOwner(@Nullable Entity owner) {
        this.owner = owner;
        this.updateOwnerInfo(this);
    }

    @Nullable
    public Entity getOwner() {
        return this.owner;
    }

    private void updateOwnerInfo(@Nullable MaidFishingHook fishingHook) {
        EntityMaid maid = this.getMaidOwner();
        if (maid != null) {
            maid.fishing = fishingHook;
        }
    }

    @Nullable
    public EntityMaid getMaidOwner() {
        Entity entity = this.getOwner();
        return entity instanceof EntityMaid maid ? maid : null;
    }

    /**
     * 是否应该立刻收线。
     *
     * <p>四个条件缺一不可（与原版 {@code FishingHook#shouldStopFishing} 一致）：
     * 女仆还活着、仍然骑着东西、任务仍是钓鱼、主手仍是鱼竿、且距离不超过 16 格。</p>
     */
    private boolean shouldStopFishing(EntityMaid maid) {
        ItemStack mainHandItem = maid.getMainHandItem();
        boolean hasFishingRod = mainHandItem.getItem() instanceof FishingRodItem;
        boolean isFishingTask = maid.getTask() instanceof TaskFishing;
        // 涓庝笂娓哥殑宸紓锛氫笂娓搁澶栬姹?hasVehicle锛堝コ浠嗗潗鍦ㄨ埞 / 鍧愬灚涓婏級銆?
        // 鎴戜滑鐨勬覆鏌撶绾垮湪銆岄獞涔?+ 闈炰汉褰㈡ā鍨嬨€嶆椂浼氫笂涓嬬炕杞紝涓?EntityChair 灏氭湭绉绘锛?
        // 鍥犳鏀逛负銆岀珯鍦ㄥ哺杈归挀楸笺€嶄篃鍏佽锛屽彧淇濈暀瀛樻椿 / 浠诲姟 / 楸肩 / 璺濈鍥涗釜鏉′欢銆?
        if (!maid.isRemoved() && maid.isAlive() && isFishingTask && hasFishingRod
            && this.distanceToSqr(maid) < 256) {
            return false;
        }
        this.discard();
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        Entity entity = this.getOwner();
        return new ClientboundAddEntityPacket(this, entity == null ? this.getId() : entity.getId());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        // 鍏抽敭锛欵ntity#recreateFromPacket 涓嶄細鎭㈠ owner锛堥偅鏄?Projectile 鑷繁鍋氱殑锛夈€?
        // 鎴戜滑鐨?getAddEntityPacket 鎶?owner 鐨勫疄浣?id 鏀捐繘浜?packet.getData()锛?
        // 杩欓噷蹇呴』鎵嬪伐杩樺師锛屽惁鍒欏鎴风 getMaidOwner() 鎭掍负 null锛?
        // 娓叉煋鍣ㄧ洿鎺?return锛堢湅涓嶅埌娴紓鍜岄奔绾匡級锛宼ick 閲岃繕浼氱珛鍒?discard銆?
        this.owner = this.level.getEntity(packet.getData());
        if (this.getMaidOwner() == null) {
            int dataId = packet.getData();
            TouhouLittleMaid.LOGGER.error(
                    "Failed to recreate fishing hook on client. {} (id: {}) is not a valid owner.",
                    this.level.getEntity(dataId), dataId);
            this.kill();
        }
    }

    protected enum FishHookState {
        FLYING,
        BOBBING
    }

    protected enum OpenWaterType {
        ABOVE_WATER,
        INSIDE_WATER,
        INVALID
    }
}


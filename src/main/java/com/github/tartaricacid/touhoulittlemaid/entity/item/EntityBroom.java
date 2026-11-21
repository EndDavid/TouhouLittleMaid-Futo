package com.github.tartaricacid.touhoulittlemaid.entity.item;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 魔理沙的扫帚，移植自上游 1.20.1 的 {@code entity/item/EntityBroom}。
 *
 * <h2>它为什么要两个人才能飞</h2>
 * <p>扫帚本身没有任何动力：{@link #travel(Vec3)} 只有一句话有意义 ——
 * 「驾驶位是玩家、且背上<b>还坐着一只女仆</b>」时，把 WASD 与俯仰角翻译成速度。
 * 少了女仆就直接掉下去。这条判定就是「女仆是灵力的来源」这个设定在代码里的样子，
 * 也是 {@code BroomTipsOverlay} 那句提示存在的原因。</p>
 *
 * <h2>为什么控制逻辑写在 {@code travel} 里、而不是读键盘状态</h2>
 * <p>早期 1.18.2 上游的做法是在 {@code travel} 里直接读
 * {@code Minecraft.getInstance().options.keyUp.isDown()}（还留了一句「不要问我为什么客户端数据能跑到服务端」）。
 * 那样服务端永远读不到按键，只能靠客户端自己飞、服务端被动跟着报上来的坐骑物坐标走。</p>
 *
 * <p>1.20.1 改成了「读 {@code player.zza} / {@code player.xxa} / 跳跃键」，<b>这套在 1.18.2 也成立</b>，
 * 而且完全不需要客户端类：</p>
 * <ul>
 *     <li>客户端每 tick 发 {@code ServerboundPlayerInputPacket}，它带着 {@code xxa} / {@code zza} / {@code isJumping} / {@code isShiftKeyDown}；</li>
 *     <li>服务端 {@code ServerGamePacketListenerImpl#handlePlayerInput} 把它交给
 *         {@code ServerPlayer#setPlayerInput(xxa, zza, jumping, shift)}；</li>
 *     <li>而 {@code setPlayerInput} 的函数体是 <b>{@code if (isPassenger())}</b> 才写入这四个值 ——
 *         骑扫帚恰好就是 {@code isPassenger()}。</li>
 * </ul>
 * <p>也就是说「骑着的时候，双端都能读到真实按键」，于是控制可以放在服务端算，
 * 不再依赖「客户端自己飞」。本移植版直接采用这个方案。</p>
 *
 * <h2>与上游 1.20.1 的三处差异</h2>
 * <ol>
 *     <li><b>没有 {@code IBroomControl} 接口层。</b>它在上游存在的意义是让附属模组插入自己的飞行方式
 *         （{@code ILittleMaid.registerBroomControl}）。本移植版还没有 {@code ILittleMaid} 扩展注册表，
 *         写一个只有内置实现、永远只有一个元素的「扩展点」只会是假的。
 *         行为本身与 1.20.1 的 {@code PlayerBroomControl} 一致。</li>
 *     <li><b>没有 {@code HasCustomInventoryScreen} / {@code getRiddenSpeed} / {@code onGround()} 重写。</b>
 *         这三个 API 在 1.18.2 都不存在（前一个是 1.19.4+ 的接口，后两个分别是 1.19 与 1.20 才有）。
 *         速度乘子直接在 {@code travel} 里算，效果一致。</li>
 *     <li><b>1.18.2 没有 {@code LivingEntity#travelRidden} / {@code tickRidden}。</b>
 *         上游那套「骑行输入 → {@code tickRidden} → 只让本地操纵的一侧 {@code travel}」
 *         在这里压缩成了 {@link #travel(Vec3)} 里的几行，顺序与语义逐条对齐，
 *         连同「非本地操纵的一侧不模拟位置」这条也一并搬了过来 —— 理由见那个方法的注释。</li>
 * </ol>
 */
public class EntityBroom extends AbstractEntityFromItem implements OwnableEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_ID =
            SynchedEntityData.defineId(EntityBroom.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final String OWNER_UUID_TAG = "OwnerUUID";

    public EntityBroom(EntityType<EntityBroom> entityType, Level worldIn) {
        super(entityType, worldIn);
        this.setNoGravity(true);
    }

    public EntityBroom(Level worldIn) {
        this(InitEntities.BROOM, worldIn);
    }

    /**
     * 实体类型的构造参数（上游把这段写在实体类里，这里交给 {@code InitEntities} 使用）。
     */
    public static EntityType<EntityBroom> createType() {
        return EntityType.Builder.<EntityBroom>of(EntityBroom::new, MobCategory.MISC)
                .sized(1.375F, 0.5625F)
                .clientTrackingRange(10)
                .build(TouhouLittleMaid.MOD_ID + ":broom");
    }

    /**
     * 「物理碰撞箱」：两个乘客时把碰撞箱<b>向上长一格</b>（对齐上游 1.20.1）。
     *
     * <p><b>它只用于方块碰撞，不是实体真正的碰撞箱。</b>调用方是
     * {@code EntityBroomPhysicalBoxMixin}：它把 {@code Entity#collide(Vec3)} 里取箱子那一次调用
     * 重定向到这里，所以只有「解算方块碰撞」看得到这个大一号的箱子。</p>
     *
     * <p>理由与上游一样：人 + 女仆叠起来真实高度远超 0.5625 格，碰撞箱不变的话，
     * 飞过单格空隙时上半身会直接穿过去。</p>
     *
     * <p><b>为什么不能直接改大 {@code makeBoundingBox()}（本移植版最初就是那么写的）：</b>
     * 真正的 {@code bb} 会随乘客数变化，而乘客数是<b>同步来的状态</b>。
     * 服务端刚把女仆放上去、客户端还在等 {@code ClientboundSetPassengersPacket} 的那几 tick，
     * 两端箱子高度差一整格：同一个位置在服务端「撞住了」、在客户端「是空的」，
     * 位置包来回纠正，表现就是玩家靠近扫帚时被反复推开、抖动。
     * 上游 1.20.1 的 {@code physicalBoundingBox} 字段就是为这件事存在的。</p>
     *
     * <p><b>为什么不缓存</b>（上游是缓存在字段里的）：{@code Entity} 的构造函数里就会调一次
     * {@code makeBoundingBox()}，而 Java 的实例字段初始化在父类构造之后才执行，
     * 缓存字段会被初始化式覆盖成零盒子。这里每次现算：既不依赖初始化顺序，
     * 也永远跟着最新位置走；只在一段 {@code collide} 里取一次，开销可以忽略。</p>
     */
    public AABB getPhysicalBoundingBox() {
        AABB aabb = super.makeBoundingBox();
        if (this.getPassengers().size() > 1) {
            return new AABB(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY + 1, aabb.maxZ);
        }
        return aabb;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER_ID, Optional.empty());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(OWNER_UUID_TAG)) {
            this.setOwnerUUID(NbtUtils.loadUUID(Objects.requireNonNull(compound.get(OWNER_UUID_TAG))));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        this.entityData.get(OWNER_ID).ifPresent(uuid -> compound.putUUID(OWNER_UUID_TAG, uuid));
    }

    /**
     * 骑行状态下的每 tick 移动，结构对齐上游 1.20.1 的
     * {@code LivingEntity#travelRidden(Player, Vec3)}。
     *
     * <p>1.18.2 的 {@code LivingEntity} 既没有 {@code travelRidden} 也没有 {@code tickRidden}，
     * 它的 {@code aiStep} 里是<b>无条件</b>调用 {@code this.travel(new Vec3(xxa, yya, zza))}
     * （字节码偏移 721）。所以这里把上游那三层
     * 「{@code travelRidden} → {@code tickRidden} → {@code travel}」压进 {@code travel} 一个方法里，
     * 相对顺序保持一致。</p>
     *
     * <h2>为什么必须分成「本地操纵 / 非本地操纵」两半</h2>
     * <p>上游 1.20.1 的 {@code travelRidden} 原文：</p>
     * <pre>{@code
     * private void travelRidden(Player player, Vec3 vec3) {
     *     Vec3 input = this.getRiddenInput(player, vec3);
     *     this.tickRidden(player, input);          // 浮动、朝向：两端都跑
     *     if (this.isControlledByLocalInstance()) {
     *         this.setSpeed(this.getRiddenSpeed(player));
     *         this.travel(input);                  // 只有这一侧真的 move
     *     } else {
     *         this.calculateEntityAnimation(false);
     *         this.setDeltaMovement(Vec3.ZERO);    // 另一侧：速度清零，不动
     *         this.tryCheckInsideBlocks();
     *     }
     * }
     * }</pre>
     * <p>也就是：<b>被玩家操纵的坐骑只有一侧在算位置</b>，另一侧的位置完全来自客户端上行的
     * {@code ServerboundMoveVehiclePacket}。</p>
     *
     * <p><b>本移植版最初漏掉了这个分支</b>，两端都在 {@code rideControl} + {@code move}。
     * 于是服务端也按自己的 {@code tickCount} 相位算出一条浮动速度、也在挪扫帚；
     * 而 1.18.2 的 {@code Entity#lerpTo(...)} 是<b>直接 {@code setPos}</code></b>
     * （1.18.2 里它没有插值，字节码只有 {@code setPos} + {@code setRot} 两句），
     * 客户端每收到一次位置包就被硬按到服务端那条<b>相位不同</b>的正弦上 ——
     * 两条正弦互相拉扯，浮动看起来就是一顿一顿的「生硬」，而不是平滑的上下漂。</p>
     */
    @Override
    public void travel(Vec3 vec3) {
        Entity controlling = this.getControllingPassenger();
        if (controlling instanceof Player player && this.isAlive()) {
            // 上游：tickRidden 在分支之前，两端都执行（清零摔落距离、浮动、朝向）
            this.tickRidden(player);
            if (!this.isControlledByLocalInstance()) {
                // 非本地操纵的一方不模拟位置：清零速度，位置等对面上行
                this.setDeltaMovement(Vec3.ZERO);
                this.tryCheckInsideBlocks();
                return;
            }
            if (this.hasPassenger(e -> e instanceof EntityMaid)) {
                this.rideControl(player);
                this.move(MoverType.SELF, this.getDeltaMovement());
                return;
            }
        }
        if (!this.isOnGround()) {
            // 没有「玩家 + 女仆」这组乘客，扫帚就只是往下掉
            super.travel(new Vec3(0, -0.3f, 0));
            return;
        }
        super.travel(vec3);
    }
    /**
     * 骑行时的速度控制，对应上游 1.20.1 的 {@code PlayerBroomControl#travel}。
     *
     * <p>三个输入源：{@code player.zza}（前后）、{@code player.xxa}（左右）、
     * 跳跃键（上升）与潜行键（下降）。这四个值在骑乘时双端都有效，见类注释。</p>
     *
     * <p>两个细节值得留意：</p>
     * <ul>
     *     <li>速度不是固定值，而是按 {@code MOVEMENT_SPEED} 属性换算 ——
     *         玩家基础速度 0.1、速度二是 0.14，直接用属性值会让速度药水几乎无感；
     *         所以这里把「超出 0.1 的部分」放大 2.5 倍。</li>
     *     <li>无输入时不是立刻停住，而是乘 0.75 快速减速 —— 否则扫帚会像撞墙一样「嘎」地停下来。</li>
     * </ul>
     */
    private void rideControl(Player player) {
        final float threshold = 1e-4f;
        boolean keyForward = player.zza > threshold;
        boolean keyBack = player.zza < -threshold;
        boolean keyLeft = player.xxa > threshold;
        boolean keyRight = player.xxa < -threshold;
        boolean keySneak = player.isShiftKeyDown();
        // 跳跃键在 1.18.2 里是 LivingEntity 的 protected 字段，靠 Mixin 访问器读取
        boolean keyJump = player instanceof com.github.tartaricacid.touhoulittlemaid.mixin.LivingEntityAccessor accessor
                && accessor.tlmIsJumping();
        Vec3 currentMotion = this.getDeltaMovement();
        boolean hasInput = keyForward || keyBack || keyLeft || keyRight || keyJump || keySneak;
        if (!hasInput) {
            // 没有输入时快速减速
            this.setDeltaMovement(currentMotion.scale(0.75));
            return;
        }
        float strafe = player.xxa * 0.2f;
        float vertical = 0;
        if (keyJump) {
            vertical = 0.25f;
        } else if (keySneak) {
            vertical = -0.2f;
        } else if (keyForward) {
            // 旧版的俯仰控制：抬头就爬升，保留下来不让老玩家手感变了
            vertical = -(player.getXRot() - 10) / 360f;
        }
        float forward = keyForward ? player.zza * 0.375f : player.zza * 0.2f;
        double playerSpeed = player.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        double speed = Math.max(playerSpeed - 0.1, 0) * 2.5 + 0.1;
        Vec3 targetMotion = new Vec3(strafe, vertical, forward).scale(speed * 20);
        // 转到扫帚自己的朝向：否则向前会永远朝着世界 -Z
        targetMotion = targetMotion.yRot((float) (-this.getYRot() * Math.PI / 180.0));
        // 插值到目标速度，而不是直接累加 —— 直接累加会让每 tick 的加速度叠起来，一按 W 就飞出去
        this.setDeltaMovement(currentMotion.lerp(targetMotion, 0.25f));
    }
    @Override
    protected void pushEntities() {
        // 已经坐满两人，不执行
        if (this.getPassengers().size() >= 2) {
            return;
        }
        // 已经坐了一人，但不是玩家，不执行
        if (!this.getPassengers().isEmpty() && !(this.getControllingPassenger() instanceof Player)) {
            return;
        }
        if (!this.level.isClientSide) {
            List<EntityMaid> list = this.level.getEntitiesOfClass(EntityMaid.class,
                    this.getBoundingBox().expandTowards(0.5, 0.1, 0.5), this::canMaidRide);
            list.stream().findFirst().ifPresent(entity -> entity.startRiding(this));
        }
    }

    private boolean canMaidRide(EntityMaid maid) {
        if (maid.canBrainMoving() && !maid.isVehicle() && EntitySelector.pushableBy(this).test(maid)) {
            UUID maidOwnerUUID = maid.getOwnerUUID();
            UUID broomOwnerUUID = this.getOwnerUUID();
            if (maidOwnerUUID == null || broomOwnerUUID == null) {
                return false;
            }
            return maidOwnerUUID.equals(broomOwnerUUID);
        }
        return false;
    }

    /**
     * 对应上游 1.20.1 的 {@code EntityBroom#tickRidden(Player, Vec3)}，由 {@link #travel(Vec3)}
     * 在「本地操纵判断之前」调用 —— 与上游 {@code LivingEntity#travelRidden} 里的位置一致。
     *
     * <p>位置很要紧：上游是先 {@code tickRidden} 把浮动叠进 {@code deltaMovement}，
     * 再在同一个 tick 里 {@code travel()} → {@code rideControl()} → {@code move()}。
     * 本移植版最初把这段放在 {@code aiStep} 末尾（也就是 {@code move} 之后），
     * 浮动的相位整整晚一个 tick，看起来就和上游对不上。</p>
     */
    protected void tickRidden(Player player) {
        // 记得把 fall distance 清零，否则落地会摔死
        this.fallDistance = 0;
        // 施加上下晃动（上游 1.20.1 EntityBroom#tickRidden 原样）：
        // 悬停时速度里叠一条周期 36 tick 的正弦，静止时扫帚就会小幅上下浮动。
        // 1.18.2 没有 Entity#addDeltaMovement，写成 setDeltaMovement(getDeltaMovement().add(...)) 等价。
        if (!this.isOnGround()) {
            this.setDeltaMovement(this.getDeltaMovement()
                    .add(0, 0.003 * Math.sin(this.tickCount * Math.PI / 18), 0));
        }
        // 扫帚朝向完全跟着驾驶者：yRotO / yBodyRot / yHeadRot 一起改，否则模型会「拧」一下
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        this.setRot(player.getYRot(), player.getXRot());
    }

    /**
     * 乘客坐在哪儿。
     *
     * <p>1.18.2 的覆写点只有一个参数。两人同乘时横向错开 ±0.35 格，
     * 否则两个人会完全重合（法线与碰撞箱都不足以分开他们）。</p>
     */
    @Override
    public void positionRider(Entity passenger) {
        if (this.hasPassenger(passenger)) {
            double xOffset = passenger instanceof EntityMaid ? -0.5 : 0;
            double yOffset = this.isRemoved() ? 0.01 : this.getPassengersRidingOffset() + passenger.getMyRidingOffset();
            if (this.getPassengers().size() > 1) {
                xOffset = this.getPassengers().indexOf(passenger) == 0 ? 0.35 : -0.35;
            }
            Vec3 offset = new Vec3(xOffset, yOffset, 0)
                    .yRot((float) (-this.getYRot() * Math.PI / 180 - Math.PI / 2));
            passenger.setPos(this.getX() + offset.x, this.getY() + offset.y, this.getZ() + offset.z);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.isDiscrete() && !this.isPassenger() && !(this.getControllingPassenger() instanceof Player)) {
            if (this.getPassengers().size() > 1) {
                return InteractionResult.sidedSuccess(this.level.isClientSide);
            }
            if (!this.level.isClientSide) {
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level.isClientSide);
        }
        return super.interact(player, hand);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity entity = this.getFirstPassenger();
        if (entity instanceof Player player) {
            return player;
        }
        return null;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.isAlive();
    }

    /**
     * 有玩家坐在上面时，扫帚本体<b>不可被准星选中</b>（对齐上游 1.20.1）。
     *
     * <p><b>为什么必须有这一条：</b>骑着扫帚时射线从玩家眼睛出发，而扫帚的碰撞箱
     * （1.375×0.5×0.5625）就在玩家脚下、并且向前伸出将近一格 —— 于是它会抢在
     * 前方的方块/生物之前被命中。表现就是：</p>
     * <ul>
     *     <li>准星对着前面的方块，中键取方块却取到「魔理沙的扫帚」；</li>
     *     <li>右击 / 左击打在扫帚自己身上，够不到前面的东西；</li>
     *     <li>对着女仆也点不到她。</li>
     * </ul>
     *
     * <p>上游源码里的注释写得很直白：「当玩家骑在扫帚上时，让扫帚本体不可被选中，
     * 防止其碰撞箱影响正常交互」。</p>
     *
     * <p><b>只在「有玩家乘客」时为 false，不是一直 false：</b>没人骑的时候还要能
     * 右键它上车、Shift+左键把它打掉（见 {@code AbstractEntityFromItem#applyHitEntityLogic}），
     * 所以那种情况必须交回父类的默认值。</p>
     */
    @Override
    public boolean isPickable() {
        boolean hasPlayer = this.getPassengers().stream().anyMatch(e -> e instanceof Player);
        return hasPlayer ? false : super.isPickable();
    }


    @Override
    protected boolean canAddPassenger(Entity entity) {
        return this.getPassengers().size() < 2;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0;
    }

    @Override
    protected boolean canKillEntity(Player player) {
        return true;
    }

    @Override
    protected SoundEvent getHitSound() {
        return SoundEvents.WOOL_BREAK;
    }

    @Override
    protected Item getWithItem() {
        return InitItems.BROOM;
    }

    @Override
    protected ItemStack getKilledStack() {
        return new ItemStack(this.getWithItem());
    }

    @Override
    public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource) {
        return false;
    }

    @Override
    protected void checkFallDamage(double pY, boolean pOnGround, BlockState pState, BlockPos pPos) {
        this.resetFallDistance();
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER_ID).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(OWNER_ID, Optional.ofNullable(uuid));
    }

    @Nullable
    @Override
    public Entity getOwner() {
        UUID uuid = this.getOwnerUUID();
        return uuid == null ? null : this.level.getPlayerByUUID(uuid);
    }
}


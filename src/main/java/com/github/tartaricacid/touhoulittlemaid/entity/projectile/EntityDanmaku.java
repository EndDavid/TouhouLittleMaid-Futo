package com.github.tartaricacid.touhoulittlemaid.entity.projectile;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 弹幕实体，移植自上游 {@code entity/projectile/EntityDanmaku}。
 *
 * <p><b>1.18.2 的关键点：这里能继承 {@code ThrowableProjectile}。</b>
 * 之前移植鱼钩时发现 {@code Projectile} 的构造函数是<b>包私有</b>的
 * （上游靠 Forge AT 打开），所以 {@code MaidFishingHook} 只能退化成 {@code extends Entity}。
 * 但 {@code ThrowableProjectile} 自己的三个构造函数都是 <b>{@code protected}</b>
 * （用 {@code javap} 核对过），子类可以直接调用 —— 因此弹幕可以走原版这套完整的投掷物管线
 * （命中检测、水中减速、粒子、同步），不需要重写一遍。</p>
 *
 * <p>同步数据只有 4 个：形状、颜色、伤害、重力。颜色与形状用枚举序号传输，
 * 客户端渲染器据此在 {@code textures/entity/danmaku.png} 上定位 32x32 的块。</p>
 */
public class EntityDanmaku extends ThrowableProjectile {
    /**
     * 存活上限（10 秒）。到点自动消失，避免射向天空的弹幕永久占用实体槽。
     */
    private static final int MAX_TICKS_EXISTED = 200;

    private static final EntityDataAccessor<Integer> DANMAKU_TYPE =
            SynchedEntityData.defineId(EntityDanmaku.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(EntityDanmaku.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(EntityDanmaku.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GRAVITY =
            SynchedEntityData.defineId(EntityDanmaku.class, EntityDataSerializers.FLOAT);

    /**
     * 减速等级（上游 {@code 滞弹} 附魔）。自定义附魔尚未移植，因此这个值恒为 0，
     * 但同步 / 判定链路保留，接入附魔后无需改动命中逻辑。
     */
    private int impedingLevel = 0;
    /**
     * 是否能打到末影人（上游 {@code 末影之末} 附魔）。同上，恒为 {@code false}。
     */
    private boolean hurtEnderman = false;

    public EntityDanmaku(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
    }

    public EntityDanmaku(Level level, LivingEntity thrower) {
        super(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.DANMAKU, thrower, level);
    }

    public EntityDanmaku(Level level, double x, double y, double z) {
        super(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.DANMAKU, x, y, z, level);
    }

    /**
     * 两个同主驯服生物之间不互伤（上游同名方法）。
     */
    private static boolean hasSameOwner(TamableAnimal a, TamableAnimal b) {
        if (a.getOwnerUUID() == null) {
            return false;
        }
        return a.getOwnerUUID().equals(b.getOwnerUUID());
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DANMAKU_TYPE, DanmakuType.PELLET.ordinal());
        this.entityData.define(COLOR, DanmakuColor.RED.ordinal());
        this.entityData.define(DAMAGE, 1.0F);
        this.entityData.define(GRAVITY, 0.01F);
    }

    /**
     * 撞方块时只在「碰撞体积非空」处消失。
     *
     * <p>这一点与上游一致，且很重要：草、雪片、空气这类没有碰撞体积的方块不该吃掉弹幕，
     * 否则在草地上射弹幕会莫名其妙消失。</p>
     */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        BlockPos pos = result.getBlockPos();
        BlockState blockState = this.level.getBlockState(pos);
        if (!blockState.getCollisionShape(this.level, pos).isEmpty()) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity thrower = this.getOwner();
        Entity hit = result.getEntity();

        if (!this.level.isClientSide) {
            // ---- friendly fire guards (same order as upstream 1.20.1 onHitEntity) ----
            if (thrower instanceof EntityMaid) {
                // (1) maid danmaku never hurts players (upstream 1.20.1)
                if (hit instanceof Player) {
                    this.discard();
                    return;
                }
                // (2) maid danmaku never hurts any maid, tamed or not.
                //     Upstream only compares ownerUUID, which can be null
                //     (freshly spawned / Owner tag lost) and then lets the hit through.
                if (hit instanceof IMaid) {
                    this.discard();
                    return;
                }
            }
            if (thrower instanceof TamableAnimal tameable) {
                // 同一个主人的驯服生物之间不互伤（女仆不会互相打到对方的宠物）
                if (hit instanceof TamableAnimal hitTameable && hasSameOwner(tameable, hitTameable)) {
                    this.discard();
                    return;
                }
                if (hit instanceof LivingEntity livingEntity && tameable.isAlliedTo(livingEntity)) {
                    this.discard();
                    return;
                }
                // 配置里的「远程攻击忽略列表」同样适用于弹幕
                ResourceLocation id = Registry.ENTITY_TYPE.getKey(hit.getType());
                if (id != null && MaidConfig.rangedAttackIgnore().contains(id.toString())) {
                    this.discard();
                    return;
                }
            }

            if (thrower != null && hit != thrower) {
                // 盾牌格挡必须在掉血之前判定（见 tryBlockWithShield 的注释）
                if (hit instanceof LivingEntity livingTarget && this.tryBlockWithShield(livingTarget, thrower)) {
                    this.discard();
                    return;
                }
                DamageSource source;
                if (this.hurtEnderman) {
                    // 上游写法：绕过护甲的魔法伤害，且不会触发末影人瞬移。
                    // 注意 1.18.2 的 DamageSource#bypassArmor() 是 protected，
                    // 不能在外部直接调用 —— 因此用下面的子类端一次。
                    source = new EndermanDamageSource(thrower);
                } else {
                    source = new EntityDamageSourceDanmaku(this, thrower);
                }
                hit.hurt(source, this.getDamage());
                if (this.impedingLevel > 0 && hit instanceof LivingEntity livingEntity) {
                    int duration = (20 + this.impedingLevel * 10) * 20;
                    livingEntity.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, duration, this.impedingLevel));
                }
            }
        }
        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > MAX_TICKS_EXISTED) {
            this.discard();
        }
    }

    @Override
    protected float getGravity() {
        return this.entityData.get(GRAVITY);
    }

    public EntityDanmaku setGravityVelocity(float gravity) {
        this.entityData.set(GRAVITY, gravity);
        return this;
    }

    public DanmakuType getDanmakuType() {
        return DanmakuType.getType(this.entityData.get(DANMAKU_TYPE));
    }

    public EntityDanmaku setDanmakuType(DanmakuType type) {
        this.entityData.set(DANMAKU_TYPE, type.ordinal());
        return this;
    }

    public DanmakuColor getColor() {
        return DanmakuColor.getColor(this.entityData.get(COLOR));
    }

    public EntityDanmaku setColor(DanmakuColor color) {
        this.entityData.set(COLOR, color.ordinal());
        return this;
    }

    public float getDamage() {
        return this.entityData.get(DAMAGE);
    }

    public EntityDanmaku setDamage(float damage) {
        this.entityData.set(DAMAGE, damage);
        return this;
    }

    public EntityDanmaku setImpedingLevel(int impedingLevel) {
        this.impedingLevel = impedingLevel;
        return this;
    }

    public EntityDanmaku setHurtEnderman(boolean hurtEnderman) {
        this.hurtEnderman = hurtEnderman;
        return this;
    }

    /**
     * 生成包要带上投掷者的实体 id。
     *
     * <p>{@code ClientboundAddEntityPacket(Entity)} 不带 data，客户端拿不到 owner；
     * 虽然渲染不需要 owner，但带上它更符合投掷物的惯例（也便于后续做轨迹修正）。</p>
     */
    @Override
    @NotNull
    public Packet<?> getAddEntityPacket() {
        Entity owner = this.getOwner();
        return new ClientboundAddEntityPacket(this, owner == null ? this.getId() : owner.getId());
    }

    /**
     * 上游写法：绕过护甲的魔法伤害，且不会触发末影人瞬移。
     *
     * <p>注意 1.18.2 的 {@code DamageSource#bypassArmor()} 是 <b>{@code protected}</b>，
     * 不能在外部直接链式调用 —— 所以单独写个子类在构造里打标记。</p>
     */
    public static class EndermanDamageSource extends EntityDamageSource {
        public EndermanDamageSource(Entity thrower) {
            super("indirectMagic", thrower);
            this.bypassArmor();
            this.setMagic();
        }
    }

    // ------------------------------------------------------------------ 盾牌

    /**
     * 盾牌格挡判定 + 结算，语义与 <b>1.20.1</b> 的上游一致。
     *
     * <p><b>为什么不能交给原版：</b>1.20.1 用 {@code DamageTypeTags.BYPASSES_SHIELD} 判断
     * 「能不能被盾挡」，而 {@code bypassArmor()} 只加 {@code BYPASSES_ARMOR} 标签 ——
     * 两个标签互相独立，所以 1.20.1 的弹幕「无视护甲、但仍然能被盾牌挡下」。</p>
     *
     * <p>1.18.2 没有伤害类型标签，只有一个 {@code DamageSource#isBypassArmor()}，
     * 而 {@code LivingEntity#isDamageSourceBlocked} 的第一句就是
     * {@code if (source.isBypassArmor()) return false;}（字节码核对过）。
     * 也就是说「无视护甲」和「可被格挡」在 1.18.2 用同一个标志位，<b>无法共存</b>。
     * 上游的弹幕必须无视护甲，因此格挡判定只能像原版那样在这里显式做一遍，
     * 然后把伤害源继续留给那个无视护甲的 {@code EntityDamageSourceDanmaku}。</p>
     *
     * <p>判定条件与 {@code LivingEntity#isDamageSourceBlocked} 逐步对齐：</p>
     * <ol>
     *     <li>目标正在举盾（{@code isBlocking()}）；</li>
     *     <li>目标正面朝向弹幕来源 —— 原版用「来源水平指向受害者的方向」与视线做点积,
     *         小于 0 才算挡住。这里等价地换成「受害者指向来源」大于 0。</li>
     * </ol>
     * <p>原版还有一条「穿透箭无视盾牌」，弹幕不是 {@code AbstractArrow}，不适用。</p>
     *
     * @return {@code true} 表示已格挡，调用方应当立即 {@code discard()} 且不再结算伤害
     */
    private boolean tryBlockWithShield(LivingEntity target, Entity thrower) {
        if (this.level.isClientSide || !target.isBlocking()) {
            return false;
        }
        Vec3 view = target.getViewVector(1.0F);
        Vec3 toShooter = new Vec3(thrower.getX() - target.getX(), 0.0D, thrower.getZ() - target.getZ());
        if (toShooter.lengthSqr() < 1.0E-4D) {
            // 贴脸重合时方向无意义，按格挡成功处理（否则近身完全挡不住）
            return this.applyShieldBlock(target);
        }
        return toShooter.normalize().dot(view) > 0.0D && this.applyShieldBlock(target);
    }

    /**
     * 格挡的副作用：盾牌掉耐久 + 播放格挡音效。
     *
     * <p>这里没有走 {@code LivingEntity#hurtCurrentlyUsedShield}，因为它在 vanilla 里是
     * <b>空实现</b>（{@code Code: 0: return}，字节码确认）—— 真正的耐久消耗写在
     * {@code Player#hurtCurrentlyUsedShield} 里。女仆不是 {@code Player}，
     * 走原版那条路等于「格挡成功但不掉耐久」，所以这里照 {@code Player} 的规则自己算：</p>
     * <ul>
     *     <li>只有单次伤害 ≥ 3 才掉耐久，消耗量 = {@code 1 + floor(damage)}（与 {@code Player} 一致）；</li>
     *     <li>音效照原版 {@code SHIELD_BLOCK}，音调 0.8~1.2 随机。</li>
     * </ul>
     */
    private boolean applyShieldBlock(LivingEntity target) {
        ItemStack shield = target.getUseItem();
        if (shield.isEmpty()) {
            return false;
        }
        float damage = this.getDamage();
        if (damage >= 3.0F) {
            int durability = 1 + Mth.floor(damage);
            InteractionHand hand = target.getUsedItemHand();
            shield.hurtAndBreak(durability, target, entity -> entity.broadcastBreakEvent(hand));
        }
        this.level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.SHIELD_BLOCK, target.getSoundSource(), 1.0F,
                0.8F + target.getRandom().nextFloat() * 0.4F);
        return true;
    }
}

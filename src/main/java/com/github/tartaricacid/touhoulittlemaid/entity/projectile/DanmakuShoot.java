package com.github.tartaricacid.touhoulittlemaid.entity.projectile;

import com.google.common.base.Preconditions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * 弹幕发射器（链式构造），移植自上游 {@code entity/projectile/DanmakuShoot}。
 *
 * <p>提供两种弹道：</p>
 * <ul>
 *     <li>{@link #aimedShot()} —— 单发，朝目标直射；</li>
 *     <li>{@link #fanShapedShot()} —— 扇形，把基准方向绕 Y 轴均分成 {@code fanNum} 份。</li>
 * </ul>
 *
 * <p>注意 {@link #fanShapedShot()} 的起点是 {@code -yawTotal/2}、步长是
 * {@code yawTotal/(fanNum-1)}，因此首尾正好覆盖整个扇角（不是 {@code /fanNum}）——
 * 与上游一致，改成 {@code /fanNum} 会让扇面偏一边。</p>
 */
public final class DanmakuShoot {
    private static final Random RANDOM = new Random();
    private static final double MAX_YAW = 2 * Math.PI;
    private static final int MIN_FAN_NUM = 2;

    private Level world = null;
    private LivingEntity thrower = null;
    private LivingEntity target = null;
    private DanmakuColor color = DanmakuColor.RED;
    private DanmakuType type = DanmakuType.PELLET;
    private int impedingLevel = 0;
    private float gravity = 0.01F;
    private float damage = 1.0F;
    private float velocity = 0.2F;
    private float inaccuracy = 0.2F;
    private double yawTotal = 0;
    private int fanNum = 0;
    private boolean hurtEnderman = false;

    private DanmakuShoot() {
    }

    public static DanmakuShoot create() {
        return new DanmakuShoot();
    }

    /**
     * 单发直射。
     *
     * <p>{@code offset} 是「瞄准点比目标脚底低多少」，用
     * {@code 0.3 / getBbHeight()} 归一化，这样高个子怪与矮个子怪都不会被瞄到头顶以上。</p>
     */
    public void aimedShot() {
        Preconditions.checkNotNull(world);
        Preconditions.checkNotNull(thrower);
        Preconditions.checkNotNull(target);
        if (!world.isClientSide) {
            EntityDanmaku danmaku = this.newDanmaku();
            float offset = 0.3F / target.getBbHeight();
            danmaku.shoot(target.getX() - thrower.getX(),
                    target.getY() - thrower.getY() - offset,
                    target.getZ() - thrower.getZ(), velocity, inaccuracy);
            world.addFreshEntity(danmaku);
        }
        world.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(),
                SoundEvents.SNOWBALL_THROW, thrower.getSoundSource(), 1.0F, 0.8F);
    }

    /**
     * 扇形散射。
     */
    public void fanShapedShot() {
        Preconditions.checkNotNull(world);
        Preconditions.checkNotNull(thrower);
        Preconditions.checkNotNull(target);
        Preconditions.checkArgument(yawTotal >= 0 && yawTotal <= MAX_YAW, "yaw should >=0 and <= 2pi");
        Preconditions.checkArgument(fanNum >= MIN_FAN_NUM, "fan number should >=2");
        if (!world.isClientSide) {
            float offset = 0.3F / target.getBbHeight();
            Vec3 base = new Vec3(target.getX() - thrower.getX(),
                    target.getY() - thrower.getY() - offset,
                    target.getZ() - thrower.getZ());
            double yaw = -(yawTotal / 2);
            double addYaw = yawTotal / (fanNum - 1);
            for (int i = 1; i <= fanNum; i++) {
                Vec3 dir = base.yRot((float) yaw);
                yaw += addYaw;

                EntityDanmaku danmaku = this.newDanmaku();
                danmaku.shoot(dir.x, dir.y, dir.z, velocity, inaccuracy);
                world.addFreshEntity(danmaku);
            }
        }
        world.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(),
                SoundEvents.SNOWBALL_THROW, thrower.getSoundSource(), 1.0F, 0.8F);
    }

    private EntityDanmaku newDanmaku() {
        return new EntityDanmaku(world, thrower)
                .setDamage(damage)
                .setGravityVelocity(gravity)
                .setDanmakuType(type)
                .setColor(color)
                .setImpedingLevel(impedingLevel)
                .setHurtEnderman(hurtEnderman);
    }

    // ------------------------------------------------------------------ 链式设置

    public DanmakuShoot setWorld(Level world) {
        this.world = world;
        return this;
    }

    public DanmakuShoot setThrower(LivingEntity thrower) {
        this.thrower = thrower;
        return this;
    }

    public DanmakuShoot setColor(DanmakuColor color) {
        this.color = color;
        return this;
    }

    public DanmakuShoot setRandomColor() {
        this.color = DanmakuColor.random(RANDOM);
        return this;
    }

    public DanmakuShoot setType(DanmakuType type) {
        this.type = type;
        return this;
    }

    public DanmakuShoot setRandomType() {
        this.type = DanmakuType.random(RANDOM);
        return this;
    }

    public DanmakuShoot setGravity(float gravity) {
        this.gravity = gravity;
        return this;
    }

    public DanmakuShoot setDamage(float damage) {
        this.damage = damage;
        return this;
    }

    public DanmakuShoot setTarget(LivingEntity target) {
        this.target = target;
        return this;
    }

    public DanmakuShoot setVelocity(float velocity) {
        this.velocity = velocity;
        return this;
    }

    public DanmakuShoot setInaccuracy(float inaccuracy) {
        this.inaccuracy = inaccuracy;
        return this;
    }

    public DanmakuShoot setYawTotal(double yawTotal) {
        this.yawTotal = yawTotal;
        return this;
    }

    public DanmakuShoot setFanNum(int fanNum) {
        this.fanNum = fanNum;
        return this;
    }

    public DanmakuShoot setImpedingLevel(int level) {
        this.impedingLevel = level;
        return this;
    }

    public DanmakuShoot setHurtEnderman(boolean hurtEnderman) {
        this.hurtEnderman = hurtEnderman;
        return this;
    }
}


package com.github.tartaricacid.touhoulittlemaid.entity.item;

import com.github.tartaricacid.touhoulittlemaid.capability.IPower;
import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapability;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * P 点实体，移植自上游 {@code entity/item/EntityPowerPoint}。
 *
 * <p>P 点是「灵力（Power）」的掉落物形态：由<b>女仆妖精</b>死亡时掉落
 * （{@code MiscConfig.MaidFairyPowerPoint}，默认 0.16 = 16 P）；玩家拾取会加灵力，
 * 女仆拾取则换成女仆自身经验（4P = 1XP）并优先修补「经验修补」物品。</p>
 *
 * <p><b>注意 {@code value} 的单位是 P，不是灵力：</b>1.00 灵力 = 100 P。
 * 上游所有换算都写死 {@code / 100.0F}，本移植版保持一致，
 * 因此「配置里填 0.16」与「实体上带 16」是同一件事。</p>
 *
 * <p>1.18.2 适配要点：</p>
 * <ul>
 *     <li>{@code Entity#onGround} 是 <b>protected</b> 字段，必须用 {@code isOnGround()}；</li>
 *     <li>{@code BlockState} 上没有带参数的 {@code getFriction}，
 *         摩擦系数取 {@code getBlock().getFriction()}；</li>
 *     <li>上游用 Forge 的 {@code IEntityAdditionalSpawnData} 同步 {@code value}，
 *         Fabric 没有等价物，这里改成 {@link SynchedEntityData} 同步位
 *         （代价：出生后约 1 tick 内客户端 value 为 0，图标是最小的那一档）；</li>
 *     <li>上游的「灵力」是 Forge capability，本移植版用 {@code PlayerPowerMixin}
 *         挂在玩家实体上，见 {@link IPower}。</li>
 * </ul>
 */
public class EntityPowerPoint extends Entity {
    private static final EntityDataAccessor<Integer> DATA_VALUE =
            SynchedEntityData.defineId(EntityPowerPoint.class, EntityDataSerializers.INT);

    private static final int MAX_AGE = 6000;

    /**
     * 刚被丢出 / 掉落后的拾取冷却（tick），冷却中不能被拾取（与上游一致）。
     */
    public int throwTime;
    public int age;
    private int health = 5;
    @Nullable
    private Player followingPlayer;
    private int followingTime;

    public EntityPowerPoint(EntityType<? extends EntityPowerPoint> type, Level level) {
        super(type, level);
    }

    /**
     * 生成一个 P 点。类型从 {@link InitEntities} 取，所以本类自己的静态初始化不会去碰它
     * （避免 {@code InitEntities} 与实体类互相静态初始化）。
     */
    public static EntityPowerPoint create(Level level, double x, double y, double z, int powerValue) {
        EntityPowerPoint point = new EntityPowerPoint(InitEntities.POWER_POINT, level);
        point.setPos(x, y, z);
        point.setYRot((float) (point.random.nextDouble() * 360.0));
        point.setDeltaMovement((point.random.nextDouble() * 0.2 - 0.1) * 2.0,
                point.random.nextDouble() * 0.2 * 2.0,
                (point.random.nextDouble() * 0.2 - 0.1) * 2.0);
        point.setValue(powerValue);
        return point;
    }

    // ------------------------------------------------------------------ P 点数值拆分

    /**
     * 把一个总 P 值拆成「不超过档位上限」的一块（上游同一张表）。
     *
     * <p>档位：1 / 3 / 5 / 7 / 13 / 34 / 89 / 185 / 285 / 385 / 485。
     * 拆分的目的是让掉落物数量与总价值都不离谱。</p>
     */
    public static int getPowerValue(int powerValue) {
        if (powerValue >= 485) {
            return 485;
        } else if (powerValue >= 385) {
            return 385;
        } else if (powerValue >= 285) {
            return 285;
        } else if (powerValue >= 185) {
            return 185;
        } else if (powerValue >= 89) {
            return 89;
        } else if (powerValue >= 36) {
            return 34;
        } else if (powerValue >= 17) {
            return 13;
        } else if (powerValue >= 7) {
            return 7;
        } else if (powerValue >= 5) {
            return 5;
        } else {
            return powerValue >= 3 ? 3 : 1;
        }
    }

    /**
     * P 点换算成经验：4P = 1 XP（与上游一致）。
     */
    public static int transPowerValueToXpValue(int powerValue) {
        return powerValue / 4;
    }

    public static void spawnExplosionParticle(Level level, float x, float y, float z, Random rand) {
        if (!level.isClientSide) {
            return;
        }
        for (int i = 0; i < 5; ++i) {
            float mx = (rand.nextFloat() - 0.5F) * 0.02F;
            float my = (rand.nextFloat() - 0.5F) * 0.02F;
            float mz = (rand.nextFloat() - 0.5F) * 0.02F;
            level.addParticle(ParticleTypes.CLOUD,
                    x + rand.nextFloat() - 0.5F,
                    y + rand.nextFloat() - 0.5F,
                    z + rand.nextFloat() - 0.5F,
                    mx, my, mz);
        }
    }

    // ------------------------------------------------------------------ 数据

    public int getValue() {
        return this.entityData.get(DATA_VALUE);
    }

    private void setValue(int value) {
        this.entityData.set(DATA_VALUE, value);
    }

    /**
     * 图标档位，0..10，渲染器按 4 列图集取格子。
     */
    public int getIcon() {
        int value = this.getValue();
        if (value >= 485) {
            return 10;
        } else if (value >= 385) {
            return 9;
        } else if (value >= 285) {
            return 8;
        } else if (value >= 185) {
            return 7;
        } else if (value >= 89) {
            return 6;
        } else if (value >= 36) {
            return 5;
        } else if (value >= 17) {
            return 4;
        } else if (value >= 7) {
            return 3;
        } else if (value >= 5) {
            return 2;
        } else {
            return value >= 3 ? 1 : 0;
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_VALUE, 0);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();

        if (this.throwTime > 0) {
            --this.throwTime;
        }

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.fluidMovement();
        if (!this.level.noCollision(this.getBoundingBox())) {
            this.moveTowardsClosestSpace(this.getX(),
                    (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
        }
        this.followingMovement();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.groundMovement();

        ++this.age;
        if (this.age >= MAX_AGE) {
            this.discard();
        }
    }

    private void groundMovement() {
        double slipperiness = 0.98;
        if (this.isOnGround()) {
            BlockPos pos = new BlockPos(this.getX(), this.getY() - 1.0D, this.getZ());
            slipperiness = this.level.getBlockState(pos).getBlock().getFriction() * 0.98;
        }
        this.setDeltaMovement(this.getDeltaMovement().multiply(slipperiness, 0.98, slipperiness));
        if (this.isOnGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, -0.9, 1.0));
        }
    }

    private void followingMovement() {
        double distance = 8.0;
        if (this.followingTime < this.getRandomCheckTime()) {
            if (this.followingPlayer == null || this.followingPlayer.distanceTo(this) > distance) {
                this.followingPlayer = this.level.getNearestPlayer(this, distance);
            }
            this.followingTime = this.tickCount;
        }
        if (this.followingPlayer != null && this.followingPlayer.isSpectator()) {
            this.followingPlayer = null;
        }
        if (this.followingPlayer != null) {
            Vec3 relative = new Vec3(
                    this.followingPlayer.getX() - this.getX(),
                    this.followingPlayer.getY() + this.followingPlayer.getEyeHeight() / 2.0D - this.getY(),
                    this.followingPlayer.getZ() - this.getZ());
            double length = relative.length();
            if (length < distance) {
                double factor = 1.0 - length / 8.0;
                this.setDeltaMovement(this.getDeltaMovement().add(relative.normalize().scale(factor * factor * 0.1D)));
            }
        }
    }

    private int getRandomCheckTime() {
        return this.tickCount - 20 + this.getId() % 100;
    }

    private void fluidMovement() {
        if (this.isEyeInFluid(FluidTags.WATER)) {
            Vec3 movement = this.getDeltaMovement();
            this.setDeltaMovement(movement.x * 0.99, Math.min(movement.y + 0.0005, 0.06), movement.z * 0.99);
        } else if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));
        }

        if (this.level.getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
            this.setDeltaMovement((this.random.nextDouble() - this.random.nextDouble()) * 0.2,
                    0.2,
                    (this.random.nextDouble() - this.random.nextDouble()) * 0.2);
            this.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
        }
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level.isClientSide || !this.isAlive()) {
            return false;
        }
        if (!this.isInvulnerableTo(source)) {
            this.markHurt();
            this.health -= (int) amount;
            if (this.health <= 0) {
                this.discard();
            }
        }
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    // ------------------------------------------------------------------ 拾取

    /**
     * 玩家拾取：先加灵力（Power），灵力满了之后<b>溢出的部分折算成经验</b>。
     *
     * <p>这条规则就是 wiki 上那句「P 点达到 5.00 后继续得到 P 点则会增加玩家经验值」：
     * 总容量 = {@link PowerCapability#MAX_POWER}（5.00），换算比 1.00 灵力 = 100 P。</p>
     *
     * <p>溢出量按<b>整数 P</b> 计算，与上游一致：
     * {@code residue = value - MAX*100 + power*100}，然后 4P = 1XP。
     * 用整数而不是浮点，是为了避免「0.16 灵力」这种小数在反复加减后累积误差。</p>
     */
    @Override
    public void playerTouch(Player player) {
        if (this.level.isClientSide) {
            return;
        }
        if (this.throwTime != 0 || player.takeXpDelay != 0) {
            return;
        }
        PowerCapability power = IPower.getPower(player);
        int value = this.getValue();
        player.takeXpDelay = 2;
        this.take(player, 1);
        if (value <= 0) {
            this.discard();
            return;
        }
        if (power.get() + value / 100.0F > PowerCapability.MAX_POWER) {
            float absorbed = PowerCapability.MAX_POWER - power.get();
            power.add(absorbed);
            // 溢出量：P 点总价值 - 本次真正吸收的部分
            int residualValue = value - (int) (absorbed * 100);
            int xp = transPowerValueToXpValue(residualValue);
            if (xp > 0) {
                player.giveExperiencePoints(xp);
            }
        } else {
            power.add(value / 100.0F);
        }
        this.discard();
    }

    /**
     * 广播「被拾取」动画与音效（上游同名方法）。
     *
     * <p>注意 {@code Entity} 在 1.18.2 <b>没有</b> {@code take} 方法 ——
     * 那是 {@code LivingEntity} 才有的，所以这里不能写 {@code @Override}。
     * 上游也是在 {@code EntityPowerPoint} 里自己写了一份，直接发
     * {@code ClientboundTakeItemEntityPacket}。</p>
     */
    public void take(Entity entity, int quantity) {
        if (this.isAlive() && !this.level.isClientSide) {
            ((net.minecraft.server.level.ServerLevel) this.level).getChunkSource()
                    .broadcast(this, new net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket(
                            this.getId(), entity.getId(), quantity));
        }
    }

    // ------------------------------------------------------------------ 存档 / 网络

    /**
     * 沿玩家视线丢出（等价 {@code Projectile#shootFromRotation}）。
     *
     * <p><b>为什么不直接调原版那个方法：</b>它在 1.18.2 是 {@code Projectile} 上的
     * 方法，而 {@code EntityPowerPoint} 继承的是 {@code Entity}（P 点是掉落物，
     * 不该有投掷物的全部逻辑），所以这里按原版同样的三角公式自己算一次方向。</p>
     */
    public void shootFrom(Player player, float pitchOffset, float velocity) {
        float pitch = (player.getXRot() + pitchOffset) * 0.017453292F;
        float yaw = player.getYRot() * 0.017453292F;
        double x = -net.minecraft.util.Mth.sin(yaw) * net.minecraft.util.Mth.cos(pitch);
        double y = -net.minecraft.util.Mth.sin(pitch);
        double z = net.minecraft.util.Mth.cos(yaw) * net.minecraft.util.Mth.cos(pitch);
        Vec3 dir = new Vec3(x, y, z).normalize().scale(velocity);
        this.setDeltaMovement(dir);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putShort("Health", (short) this.health);
        compound.putShort("Age", (short) this.age);
        compound.putShort("Value", (short) this.getValue());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.health = compound.getShort("Health");
        this.age = compound.getShort("Age");
        this.setValue(compound.getShort("Value"));
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}

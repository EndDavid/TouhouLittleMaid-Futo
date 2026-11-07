package com.github.tartaricacid.touhoulittlemaid.entity.item;

import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 灭火剂（灭火器喷出的那团白雾），移植自上游 {@code entity/item/EntityExtinguishingAgent}。
 *
 * <p>寿命 3 秒，第 5 tick 时执行一次范围灭火：</p>
 * <ul>
 *     <li>5x3x5 范围内的 {@code minecraft:fire} 方块被直接移除；</li>
 *     <li>2 格内的生物全部 {@code clearFire()}；</li>
 *     <li>但免疫火焰的生物会反过来被这团雾伤害（每 5 tick 2 点魔法伤害）——
 *         这是上游的设计，用来对付烈焰人这类目标。</li>
 * </ul>
 */
public class EntityExtinguishingAgent extends Entity {
    private static final int MAX_AGE = 3 * 20;
    private static final int REMOVE_FIRE_AGE = 5;
    private List<Monster> cacheFireImmuneMonster = Lists.newArrayList();

    public EntityExtinguishingAgent(EntityType<EntityExtinguishingAgent> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
    }

    public EntityExtinguishingAgent(Level worldIn, Vec3 position) {
        // 用注册表里的类型；不直接引用静态字段，避免与 InitEntities 形成静态初始化环
        this(com.github.tartaricacid.touhoulittlemaid.init.InitEntities.EXTINGUISHING_AGENT, worldIn);
        this.setPos(position.x, position.y, position.z);
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (this.tickCount > MAX_AGE) {
            this.discard();
            return;
        }
        if (this.tickCount == REMOVE_FIRE_AGE) {
            this.removeBlockFire();
            this.removeEntityFire();
        }
        this.damageFireImmuneMonster();
        if (this.level.isClientSide) {
            this.spawnCloudParticle();
        }
        this.playSound(SoundEvents.WOOL_PLACE, 2.0f - (1.8f / MAX_AGE) * this.tickCount, 0.1f);
    }

    private void spawnCloudParticle() {
        int spawnNumber = 4;
        for (int i = 0; i < spawnNumber; i++) {
            double offsetX = 2 * this.random.nextDouble() - 1;
            double offsetY = this.random.nextDouble() / 2;
            double offsetZ = 2 * this.random.nextDouble() - 1;
            this.level.addParticle(ParticleTypes.CLOUD, false,
                    this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                    0, 0.1, 0);
        }
    }

    private void damageFireImmuneMonster() {
        if (this.tickCount % 5 == 0 && this.cacheFireImmuneMonster != null && !this.cacheFireImmuneMonster.isEmpty()) {
            this.cacheFireImmuneMonster.forEach(monster -> {
                if (monster.isAlive()) {
                    monster.hurt(DamageSource.MAGIC, 2);
                }
            });
        }
    }

    private void removeEntityFire() {
        List<LivingEntity> list = this.level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(2, 1, 2));
        this.cacheFireImmuneMonster = this.level.getEntitiesOfClass(Monster.class,
                this.getBoundingBox().inflate(2, 1, 2), Entity::fireImmune);
        for (LivingEntity entity : list) {
            entity.clearFire();
        }
    }

    private void removeBlockFire() {
        int hRange = 2;
        int vRange = 1;
        BlockPos center = this.blockPosition();
        for (int x = -hRange; x <= hRange; x++) {
            for (int y = -vRange; y <= vRange; y++) {
                for (int z = -hRange; z <= hRange; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = this.level.getBlockState(pos);
                    if (state.is(Blocks.FIRE)) {
                        this.level.removeBlock(pos, false);
                    }
                }
            }
        }
    }

    /**
     * 原版 1.18.2 里 {@code Entity#getAddEntityPacket()} 是【抽象方法】（生成实体用的网络包）。
     *
     * <p>上游走 Forge 的 {@code NetworkHooks.getEntitySpawningPacket(this)}；
     * 原版等价物就是 {@link ClientboundAddEntityPacket}。</p>
     */
    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
    }
}


package com.github.tartaricacid.touhoulittlemaid.entity.monster;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.goal.FairyAttackGoal;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuShoot;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.BiPredicate;

/**
 * 女仆妖精（上游 {@code entity/monster/EntityFairy}）—— P 点的<b>唯一</b>常规来源。
 *
 * <p>她是一个会飞的远程敌对生物：贴到 6 格内悬停、蓄力 1 秒、然后朝你丢弹幕。
 * 90% 是瞄准单发，10% 是三发扇形（伤害更高，用来惩罚站桩）。</p>
 *
 * <h3>生成规则（三层，缺一层就会「满地妖精」或「一只都没有」）</h3>
 * <ol>
 *     <li><b>能不能被抽中</b>：{@code mixin/NaturalSpawnerMixin} 往 MONSTER 刷怪表里
 *         加一条权重（配置 {@code MaidFairySpawnProbability}，默认 70，僵尸是 100）。
 *         维度黑名单与「群系得有普通怪」都在那里判断。</li>
 *     <li><b>这个位置能不能刷</b>：{@link #checkFairySpawnRules}，
 *         注册进 {@code SpawnPlacements}。上游在这里查<b>稻草人 POI</b>
 *         （{@code SCARECROW_RANGE}，默认 48 格），稻草人附近不刷。</li>
 *     <li><b>刷出来长什么样</b>：{@link #finalizeSpawn} 随机发色，
 *         5% 概率把名字设成 {@code rick}（上游彩蛋，对应 {@code maid_fairy_rick.png}）。</li>
 * </ol>
 *
 * <p><b>与本移植版的一个取舍：</b>稻草人方块尚未移植，因此第 2 层的 POI 检查改成一个
 * 可插拔的钩子 {@link #setRepellentCheck}。等稻草人落地时，只需在那里注册一个
 * 「附近有没有稻草人」的判定，不必回来改妖精的代码。</p>
 */
public class EntityFairy extends Monster implements RangedAttackMob, FlyingAnimal, IHasPowerPoint {
    /**
     * 上游彩蛋：名字叫 rick 的妖精用专门的贴图。
     */
    public static final String RICK = "rick";

    private static final String FAIRY_TYPE_TAG_NAME = "FairyType";
    private static final EntityDataAccessor<Integer> FAIRY_TYPE =
            SynchedEntityData.defineId(EntityFairy.class, EntityDataSerializers.INT);
    /**
     * 瞄准单发的概率，其余走三发扇形。
     */
    private static final double AIMED_SHOT_PROBABILITY = 0.9;

    /**
     * 稻草人（或未来任何「驱散物」）的判定钩子。
     *
     * <p>返回 {@code true} 表示「这个位置被驱散，不许刷妖精」。</p>
     */
    @Nullable
    private static BiPredicate<ServerLevel, BlockPos> repellentCheck;

    public EntityFairy(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // 飞行移动控制器：maxTurn = 15，followGroundPath = true（与上游一致）
        this.moveControl = new FlyingMoveControl(this, 15, true);
    }

    public static void setRepellentCheck(@Nullable BiPredicate<ServerLevel, BlockPos> check) {
        repellentCheck = check;
    }

    public static AttributeSupplier.Builder createFairyAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 1.5D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.FLYING_SPEED, 0.4D);
    }

    /**
     * 位置合法性：先过原版敌对生物的规则（亮度、难度、方块），再查驱散物。
     */
    public static boolean checkFairySpawnRules(EntityType<EntityFairy> type, ServerLevelAccessor levelAccessor,
                                               MobSpawnType spawnType, BlockPos pos, Random random) {
        if (!Monster.checkMonsterSpawnRules(type, levelAccessor, spawnType, pos, random)) {
            return false;
        }
        if (repellentCheck != null && levelAccessor instanceof ServerLevel level) {
            return !repellentCheck.test(level, pos);
        }
        return true;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FairyAttackGoal(this, 6.0D, 1.0D));
        this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, EntityMaid.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(FAIRY_TYPE, FairyType.BLACK.ordinal());
    }

    /**
     * 掉落的总 P 点。配置以「灵力」为单位，乘 100 换成 P 点。
     */
    @Override
    public int getPowerPoint() {
        return (int) (MiscConfig.maidFairyPowerPoint() * 100);
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        dropPowerPoint(this);
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // 坠落伤害直接免疫：她是飞行单位，被击落时不该摔死
        if (source.isFall()) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    /**
     * 远程攻击：90% 瞄准单发，10% 三发 30° 扇形（伤害更高）。
     *
     * <p>伤害随难度递增（简单 1 / 普通 1.5 / 困难 2），并叠加
     * {@code distanceFactor}（由 {@code FairyAttackGoal} 给出的「贴近程度」）。</p>
     */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        float damageBase = 1.0F;
        Difficulty difficulty = target.level.getDifficulty();
        switch (difficulty) {
            case NORMAL -> damageBase = 1.5F;
            case HARD -> damageBase = 2.0F;
            default -> {
            }
        }
        if (this.random.nextFloat() <= AIMED_SHOT_PROBABILITY) {
            DanmakuShoot.create().setWorld(this.level).setThrower(this)
                    .setTarget(target).setRandomColor().setRandomType()
                    .setDamage(distanceFactor + damageBase).setGravity(0)
                    .setVelocity(0.2F * (distanceFactor + 1))
                    .setInaccuracy(0.2F).aimedShot();
        } else {
            DanmakuShoot.create().setWorld(this.level).setThrower(this)
                    .setTarget(target).setRandomColor().setRandomType()
                    .setDamage(distanceFactor + damageBase + 0.5F).setGravity(0)
                    .setVelocity(0.2F * (distanceFactor + 1))
                    .setInaccuracy(0.2F).setYawTotal(Math.PI / 6).setFanNum(3)
                    .fanShapedShot();
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        this.setFairyTypeOrdinal(this.random.nextInt(FairyType.values().length));
        // 5% 概率生成 Rick-rolling 彩蛋（对应 new_maid_fairy/maid_fairy_rick.png）
        if (this.random.nextInt(20) == 0) {
            this.setCustomName(new TextComponent(RICK));
            this.setCustomNameVisible(true);
        }
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt(FAIRY_TYPE_TAG_NAME, getFairyTypeOrdinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(FAIRY_TYPE_TAG_NAME, Tag.TAG_INT)) {
            setFairyTypeOrdinal(compound.getInt(FAIRY_TYPE_TAG_NAME));
        }
    }

    public int getFairyTypeOrdinal() {
        return this.entityData.get(FAIRY_TYPE);
    }

    public void setFairyTypeOrdinal(int ordinal) {
        this.entityData.set(FAIRY_TYPE, ordinal);
    }

    @Override
    public boolean isFlying() {
        return !this.isOnGround();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return InitSounds.FAIRY_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return InitSounds.FAIRY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return InitSounds.FAIRY_DEATH;
    }
}

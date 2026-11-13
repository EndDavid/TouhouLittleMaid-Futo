package com.github.tartaricacid.touhoulittlemaid.entity.favorability;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Maps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;

/**
 * 好感度管理与「每种行为的冷却」（上游 {@code entity/favorability/FavorabilityManager} 的精简版）。
 *
 * <h2>为什么必须有这个类</h2>
 *
 * <p>吃饭 / 睡觉 / 玩棋这些行为都靠 {@code apply(Type, point)} 加好感度，而
 * {@code Type} 自带冷却。如果只有「加」没有冷却，女仆会站在那儿每 2 秒吃一次，
 * 一分钟就顶到上限。上游把冷却表放在 {@code FavorabilityManager} 里，
 * 每次 {@code canAdd(typeName)} 都要求该类型冷却已经走完。</p>
 *
 * <h2>与上游的差异</h2>
 *
 * <ul>
 *     <li>上游用 capability 挂在实体上；Fabric 没有 capability，这里是实体自己的字段
 *         （NBT 子标签 {@code FavorabilityManagerCounter}，与上游同名）。</li>
 *     <li>上游 {@code add()} 里还有成就（advancement trigger）与网络粒子包；
 *         本移植版没有成就系统，粒子改用 {@code ServerLevel#sendParticles}（见
 *         {@code EntityMaid#spawnHeartParticles}）。</li>
 *     <li>好感度数值本身仍存在实体的同步数据 {@code DATA_FAVORABILITY} 里，
 *         这里只负责「改多少、能不能改、等级怎么换」。因此 GUI 读到的永远是同一个值。</li>
 * </ul>
 *
 * <h2>等级与属性</h2>
 *
 * <p>好感度越过 64 / 192 / 384 会升级，每级提升最大生命与攻击力
 * （20→30→40→80 点生命，2→3→4→6 点攻击）。这是上游的原数值，
 * 也是「满好感女仆很能打」的来源。</p>
 */
public class FavorabilityManager {
    public static final Map<String, Type> TYPES = Maps.newHashMap();

    private static final int LEVEL_0 = 0;
    private static final int LEVEL_1 = 1;
    private static final int LEVEL_2 = 2;
    private static final int LEVEL_3 = 3;

    private static final int LEVEL_0_POINT = 0;
    public static final int LEVEL_1_POINT = 64;
    public static final int LEVEL_2_POINT = 192;
    public static final int LEVEL_3_POINT = 384;

    private static final int LEVEL_0_HEALTH = 20;
    private static final int LEVEL_1_HEALTH = 30;
    private static final int LEVEL_2_HEALTH = 40;
    private static final int LEVEL_3_HEALTH = 80;

    private static final int LEVEL_0_ATTACK_DAMAGE = 2;
    private static final int LEVEL_1_ATTACK_DAMAGE = 3;
    private static final int LEVEL_2_ATTACK_DAMAGE = 4;
    private static final int LEVEL_3_ATTACK_DAMAGE = 6;

    private static final String TAG_NAME = "FavorabilityManagerCounter";

    private final Map<String, Time> counter;
    private final EntityMaid maid;

    public FavorabilityManager(EntityMaid maid) {
        this.counter = Maps.newHashMap();
        this.maid = maid;
    }

    /**
     * 每 tick 递减所有冷却。只有服务端会调（见 {@code EntityMaid#tick}）。
     */
    public void tick() {
        if (counter.isEmpty()) {
            return;
        }
        counter.values().forEach(Time::tick);
        counter.entrySet().removeIf(entry -> entry.getValue().isZero());
    }

    public void apply(String type) {
        Type typeInstance = TYPES.get(type);
        if (typeInstance != null) {
            this.apply(typeInstance);
        }
    }

    public void apply(Type type) {
        if (this.canAdd(type.getTypeName())) {
            if (type.isReduce()) {
                this.reduce(type.getPoint());
            } else {
                this.add(type.getPoint());
            }
            this.addCooldown(type.getTypeName(), type.getCooldown());
        }
    }

    public void apply(Type type, int point) {
        if (this.canAdd(type.getTypeName())) {
            if (type.isReduce()) {
                this.reduce(point);
            } else {
                this.add(point);
            }
            this.addCooldown(type.getTypeName(), type.getCooldown());
        }
    }

    private void addCooldown(String type, int tickCount) {
        this.counter.put(type, new Time(tickCount));
    }

    public boolean canAdd(String type) {
        if (this.counter.containsKey(type)) {
            return this.counter.get(type).isZero();
        }
        return true;
    }

    public int getLevel() {
        return this.getLevel(this.maid.getFavorability());
    }

    private int getLevel(int favorability) {
        if (favorability < LEVEL_1_POINT) {
            return LEVEL_0;
        } else if (favorability < LEVEL_2_POINT) {
            return LEVEL_1;
        } else if (favorability < LEVEL_3_POINT) {
            return LEVEL_2;
        } else {
            return LEVEL_3;
        }
    }

    /**
     * 当前等级内的进度（0~1），满级返回 0。
     *
     * <p>GUI 的好感度条应该用这个而不是 {@code favorability / 100}：
     * 好感度上限是 384，除以 100 会让条子早就爆满。</p>
     */
    public double getLevelPercent() {
        int favorability = this.maid.getFavorability();
        if (favorability < LEVEL_1_POINT) {
            return favorability / (double) LEVEL_1_POINT;
        } else if (favorability < LEVEL_2_POINT) {
            return (favorability - LEVEL_1_POINT) / (double) (LEVEL_2_POINT - LEVEL_1_POINT);
        } else if (favorability < LEVEL_3_POINT) {
            return (favorability - LEVEL_2_POINT) / (double) (LEVEL_3_POINT - LEVEL_2_POINT);
        } else {
            return 0;
        }
    }

    /**
     * 距离下一级还差多少点，满级返回 0。
     */
    public int nextLevelPoint() {
        int level = this.getLevel();
        if (level == LEVEL_3) {
            return 0;
        }
        int pointByLevel = getPointByLevel(level + 1);
        return pointByLevel - this.maid.getFavorability();
    }

    public int getHealthByLevel(int level) {
        return switch (level) {
            case LEVEL_1 -> LEVEL_1_HEALTH;
            case LEVEL_2 -> LEVEL_2_HEALTH;
            case LEVEL_3 -> LEVEL_3_HEALTH;
            default -> LEVEL_0_HEALTH;
        };
    }

    public int getAttackByLevel(int level) {
        return switch (level) {
            case LEVEL_1 -> LEVEL_1_ATTACK_DAMAGE;
            case LEVEL_2 -> LEVEL_2_ATTACK_DAMAGE;
            case LEVEL_3 -> LEVEL_3_ATTACK_DAMAGE;
            default -> LEVEL_0_ATTACK_DAMAGE;
        };
    }

    public int getPointByLevel(int level) {
        return switch (level) {
            case LEVEL_1 -> LEVEL_1_POINT;
            case LEVEL_2 -> LEVEL_2_POINT;
            case LEVEL_3 -> LEVEL_3_POINT;
            default -> LEVEL_0_POINT;
        };
    }

    public void add(int addPoint) {
        int favorability = this.maid.getFavorability();
        int levelBefore = getLevel();
        int result = Mth.clamp(favorability + addPoint, 0, LEVEL_3_POINT);
        this.maid.setFavorability(result);
        int levelAfter = getLevel();
        if (levelBefore < levelAfter) {
            AttributeInstance attack = this.maid.getAttribute(Attributes.ATTACK_DAMAGE);
            AttributeInstance health = this.maid.getAttribute(Attributes.MAX_HEALTH);
            if (attack != null) {
                attack.setBaseValue(this.getAttackByLevel(levelAfter));
            }
            if (health != null) {
                // 被雷劈过的女仆（上游彩蛋：闪电苦力怕引雷）额外 +20 生命
                if (this.maid.isStruckByLightning()) {

                    health.setBaseValue(this.getHealthByLevel(levelAfter) + 20);
                } else {
                    health.setBaseValue(this.getHealthByLevel(levelAfter));
                }
                if (this.maid.getHealth() > this.maid.getMaxHealth()) {
                    this.maid.setHealth(this.maid.getMaxHealth());
                }
            }
            this.maid.playSound(SoundEvents.PLAYER_LEVELUP, 0.5F,
                    this.maid.getRandom().nextFloat() * 0.1F + 0.9F);
        }
        this.maid.spawnHeartParticles(1);
    }


    /**
     * 把好感度直接拉满（上游 {@code FavorabilityManager#max}）。
     *
     * <p>上游的实现就是一句 {@code this.add(LEVEL_3_POINT)} —— 借 {@link #add}
     * 的钳制与升阶逻辑自然停在三级上限，并顺带刷新攻击 / 生命属性，
     * 所以这里也照抄，不另写一套赋值。</p>
     */
    public void max() {
        this.add(LEVEL_3_POINT);
    }

    public void reduce(int reducePoint) {
        int favorability = this.maid.getFavorability();
        int pointByLevel = getPointByLevel(getLevel());
        int result = Mth.clamp(favorability - reducePoint, pointByLevel, LEVEL_3_POINT);
        this.maid.setFavorability(result);
    }

    /**
     * 不管等级下限，直接扣（上游用于「死亡惩罚」这类允许掉级的场合）。
     */
    public void reduceWithoutLevel(int reducePoint) {
        int favorability = this.maid.getFavorability();
        int result = Mth.clamp(favorability - reducePoint, 0, LEVEL_3_POINT);
        this.maid.setFavorability(result);
        // 掉级时属性必须跟着回落，否则会出现「好感度 0 但生命 80」
        int level = getLevel();
        AttributeInstance attack = this.maid.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance health = this.maid.getAttribute(Attributes.MAX_HEALTH);
        if (attack != null) {
            attack.setBaseValue(this.getAttackByLevel(level));
        }
        if (health != null) {
            health.setBaseValue(this.getHealthByLevel(level));
            if (this.maid.getHealth() > this.maid.getMaxHealth()) {
                this.maid.setHealth(this.maid.getMaxHealth());
            }
        }
    }

    /**
     * 依据当前好感度重建属性（读档 / 换模型后调用）。
     *
     * <p>上游是在 {@code EntityMaid#readAdditionalSaveData} 之后懒应用的；
     * 这里显式提供入口，避免「读档后好感度 300 但生命还是 20」。</p>
     */
    public void refreshAttributes() {
        int level = getLevel();
        AttributeInstance attack = this.maid.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance health = this.maid.getAttribute(Attributes.MAX_HEALTH);
        if (attack != null) {
            attack.setBaseValue(this.getAttackByLevel(level));
        }
        if (health != null) {
            health.setBaseValue(this.getHealthByLevel(level));
        }
    }

    public void save(CompoundTag tag) {
        ListTag listTag = new ListTag();
        this.counter.forEach((type, time) -> {
            if (time.time > 0) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Type", type);
                entry.putInt("Time", time.time);
                listTag.add(entry);
            }
        });
        tag.put(TAG_NAME, listTag);
    }

    public void load(CompoundTag tag) {
        this.counter.clear();
        if (!tag.contains(TAG_NAME, Tag.TAG_LIST)) {
            return;
        }
        ListTag listTag = tag.getList(TAG_NAME, Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag entry = listTag.getCompound(i);
            String type = entry.getString("Type");
            int time = entry.getInt("Time");
            if (!type.isEmpty() && time > 0) {
                this.counter.put(type, new Time(time));
            }
        }
    }

    /**
     * 一个冷却计时器（上游 {@code FavorabilityManager.Time}）。
     */
    public static class Time {
        private int time;

        public Time(int time) {
            this.time = time;
        }

        public void tick() {
            if (this.time > 0) {
                this.time--;
            }
        }

        public boolean isZero() {
            return this.time <= 0;
        }

        public int getTime() {
            return this.time;
        }
    }
}


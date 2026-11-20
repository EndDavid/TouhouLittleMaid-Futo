package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidHostilesSensor;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidNearestLivingEntitySensor;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidPickupEntitiesSensor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.MaidReflection;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

import java.util.List;
import java.util.Optional;

/**
 * 实体与 Brain 相关注册，移植自上游 {@code init/InitEntities}。
 *
 * <p>1.18.2 里 {@code MemoryModuleType} / {@code SensorType} / {@code Schedule} /
 * {@code Activity} 都是普通静态注册表，Fabric 侧用 {@link Registry#register} 直接注册
 * （等价上游的 {@code DeferredRegister}）。其中三个类型的构造函数在 vanilla 里是 private，
 * 上游靠 Forge AT 打开，这里改由 {@link MaidReflection} 反射构造
 * （原因见该类注释：Loom 与 Loader 的 AW 表头格式要求冲突）。</p>
 */
public final class InitEntities {
    static {
        // 1.18.2 没有 DataSerializer 注册表，序列化器直接登记到 EntityDataSerializers（网络同步用）。
        // 必须先于实体类型注册：EntityMaid 的静态初始化会用到 MaidSchedule.DATA
        net.minecraft.network.syncher.EntityDataSerializers.registerSerializer(MaidSchedule.DATA);
    }

    public static final EntityType<EntityMaid> MAID = Registry.register(Registry.ENTITY_TYPE,
            TouhouLittleMaid.rl("maid"),
            EntityType.Builder.of(EntityMaid::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.5F)
                    .clientTrackingRange(10)
                    .build(TouhouLittleMaid.MOD_ID + ":maid"));

    // ------------------------------------------------------------------ 灭火剂实体

    /**
     * 灭火剂实体（上游 {@code EntityExtinguishingAgent.TYPE}）。
     *
     * <p>注意：这里把注册结果放在 {@code InitEntities} 而不是实体类里,
     * 避免 {@code EntityExtinguishingAgent} 与 {@code InitEntities} 互相静态初始化
     * （实体类若自己持有 {@code TYPE} 静态字段，会在 {@code InitEntities} 尚未完成时被加载）。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.item.EntityExtinguishingAgent>
            EXTINGUISHING_AGENT = Registry.register(Registry.ENTITY_TYPE,
            TouhouLittleMaid.rl("extinguishing_agent"),
            EntityType.Builder.<com.github.tartaricacid.touhoulittlemaid.entity.item.EntityExtinguishingAgent>of(
                            com.github.tartaricacid.touhoulittlemaid.entity.item.EntityExtinguishingAgent::new,
                            MobCategory.MISC)
                    .sized(0.2F, 0.2F)
                    .clientTrackingRange(10)
                    .build(TouhouLittleMaid.MOD_ID + ":extinguishing_agent"));

    public static final Activity RIDE_IDLE = Registry.register(Registry.ACTIVITY,
            TouhouLittleMaid.rl("ride_idle"), MaidReflection.newActivity("tlm_ride_idle"));
    public static final Activity RIDE_WORK = Registry.register(Registry.ACTIVITY,
            TouhouLittleMaid.rl("ride_work"), MaidReflection.newActivity("tlm_ride_work"));
    public static final Activity RIDE_REST = Registry.register(Registry.ACTIVITY,
            TouhouLittleMaid.rl("ride_rest"), MaidReflection.newActivity("tlm_ride_rest"));
    /**
     * 座位实体（上游 {@code EntitySit.TYPE}）。
     *
     * <p>{@code MobCategory.MISC} + 0.5×0.1 的碰撞箱：它不是生物，只是给人「坐」的锚点。
     * {@code clientTrackingRange(10)} 与上游一致 —— 坐下的动画只在近距离可见。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit> SIT =
            Registry.register(Registry.ENTITY_TYPE,
                    TouhouLittleMaid.rl("sit"),
                    EntityType.Builder.<com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit>of(
                                    com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit::new, MobCategory.MISC)
                            .sized(0.5F, 0.1F)
                            .clientTrackingRange(10)
                            .build(TouhouLittleMaid.MOD_ID + ":sit"));

    /**
     * 魔理沙的扫帚（上游 {@code EntityBroom.TYPE}）。
     *
     * <p>1.375×0.5625 的碰撞箱就是它蓝图上的真实体积：横着比一个方块还宽。
     * 放置时 {@code ItemBroom#useOn} 会先按这个体积算包围盒做碰撞检查，
     * 所以尺寸改了就得连那里一起看。</p>
     *
     * <p>它继承 {@code AbstractEntityFromItem}（即 {@code LivingEntity}），
     * 因此必须在 {@link #init()} 里注册属性表 ——
     * 不注册的话 {@code LivingEntity} 构造函数里那句
     * {@code setHealth(getMaxHealth())} 会直接 NPE。坐垫当年就是这么崩的。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom> BROOM =
            Registry.register(Registry.ENTITY_TYPE,
                    TouhouLittleMaid.rl("broom"),
                    com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom.createType());

    /**
     * 女仆鱼钩（上游 {@code MaidFishingHook.TYPE}）。
     *
     * <p>{@code noSave()} 与 {@code noSummon()}：鱼钩不存档、也不能被 /summon，
     * 与上游一致（它是一次性的临时实体）。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook>
            MAID_FISHING_HOOK = Registry.register(Registry.ENTITY_TYPE,
            TouhouLittleMaid.rl("fishing_hook"),
            EntityType.Builder
                    .<com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook>of(
                            com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook::new,
                            MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(5)
                    .build(TouhouLittleMaid.MOD_ID + ":fishing_hook"));

    // ------------------------------------------------------------------ 弹幕

    /**
     * 弹幕实体（上游 {@code EntityDanmaku.TYPE}）。
     *
     * <p>{@code noSave()}：弹幕不写存档（一次性投掷物，读档时不该复现）。
     * {@code noSummon()} 暂不加 —— 上游允许 {@code /summon}，便于调试。
     * {@code updateInterval(10)} 与 {@code clientTrackingRange(6)} 与上游一致：
     * 弹幕很多，降低同步频率能显著减少带宽。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.projectile.EntityDanmaku>
            DANMAKU = Registry.register(Registry.ENTITY_TYPE,
            TouhouLittleMaid.rl("danmaku"),
            EntityType.Builder
                    .<com.github.tartaricacid.touhoulittlemaid.entity.projectile.EntityDanmaku>of(
                            com.github.tartaricacid.touhoulittlemaid.entity.projectile.EntityDanmaku::new,
                            MobCategory.MISC)
                    .noSave()
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .build(TouhouLittleMaid.MOD_ID + ":danmaku"));

    // ------------------------------------------------------------------ P 点

    /**
     * P 点实体（上游 {@code EntityPowerPoint.TYPE}）。
     *
     * <p>与上游一致：{@code sized(0.5, 0.5)}、{@code clientTrackingRange(6)}、
     * {@code updateInterval(20)}。P 点是纯掉落物，可存盘（重进存档后还在）。</p>
     *
     * <p>⚠ 客户端必须注册渲染器（{@code EntityPowerPointRenderer}）：1.18.2 的
     * {@code EntityRenderDispatcher#getRenderer} 在没有注册时返回 <b>null</b>，
     * 而 {@code LevelRenderer} 会直接 NPE —— 这不是"看不见"而是直接崩游戏。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint>
            POWER_POINT = Registry.register(Registry.ENTITY_TYPE,
            TouhouLittleMaid.rl("power_point"),
            EntityType.Builder
                    .<com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint>of(
                            com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint::new,
                            MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build(TouhouLittleMaid.MOD_ID + ":power_point"));

    // ------------------------------------------------------------------ 女仆妖精

    /**
     * 女仆妖精（上游 {@code InitEntities.FAIRY}）。
     *
     * <p>{@code MobCategory.MONSTER} 很关键：{@code NaturalSpawnerMixin} 只在
     * MONSTER 这一档往刷怪表里加她，而且原版的「生物上限 / 生成距离 / 光照」规则
     * 也都按这个类别算。写错类别会出现「白天刷满平原」或「永远刷不出来」。</p>
     *
     * <p>{@code clientTrackingRange(10)} 与上游一致 —— 妖精会远程攻击,
     * 追踪距离比近战怪（8）长一点，玩家才来得及看见她。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy>
            FAIRY = Registry.register(Registry.ENTITY_TYPE,
            TouhouLittleMaid.rl("fairy"),
            EntityType.Builder
                    .<com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy>of(
                            com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy::new,
                            MobCategory.MONSTER)
                    .sized(0.6F, 1.5F)
                    .clientTrackingRange(10)
                    .build(TouhouLittleMaid.MOD_ID + ":fairy"));

    // ------------------------------------------------------------------ 墓碑与箱子

    /**
     * 女仆墓碑（上游 {@code EntityTombstone.TYPE}）。
     *
     * <p>尺寸 {@code 0.8 × 1.2}、追踪距离 10，与上游一致。它不写 {@code noSave()}：
     * 墓碑必须能活过重启，否则女仆的东西会永久丢失。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone>
            TOMBSTONE = Registry.register(Registry.ENTITY_TYPE,
            TouhouLittleMaid.rl("tombstone"),
            com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone.createType());

    /**
     * 祭坛召唤用的「箱子」（上游 {@code EntityBox.TYPE}）。
     *
     * <p>尺寸 {@code 2 × 2}，因为它里面塞着一只直立的女仆。追踪距离 10 是上游的值 ——
     * 盒子开完就消失，不需要更远的同步。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBox>
            BOX = Registry.register(Registry.ENTITY_TYPE,
            TouhouLittleMaid.rl("box"),
            com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBox.createType());

    /**
     * 坐垫（上游 {@code EntityChair.TYPE}，尺寸 {@code 0.875 × 0.5}）。
     *
     * <p>与刷怪蛋那类实体不同，它完全由物品摆放出来，所以没有 {@code noSave()} ——
     * 坐垫必须能存盘，否则玩家摆好的一屋子坐垫会随重启消失。</p>
     */
    public static final EntityType<com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair>
            CHAIR = Registry.register(Registry.ENTITY_TYPE,
            TouhouLittleMaid.rl("chair"),
            com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair.createType());


    // ------------------------------------------------------------------ 自定义记忆

    public static final MemoryModuleType<List<Entity>> VISIBLE_PICKUP_ENTITIES = Registry.register(
            Registry.MEMORY_MODULE_TYPE, TouhouLittleMaid.rl("visible_pickup_entities"),
            MaidReflection.newMemoryModuleType(Optional.empty()));
    public static final MemoryModuleType<PositionTracker> TARGET_POS = Registry.register(
            Registry.MEMORY_MODULE_TYPE, TouhouLittleMaid.rl("target_pos"),
            MaidReflection.newMemoryModuleType(Optional.empty()));

    // ------------------------------------------------------------------ 自定义感知器

    public static final SensorType<MaidNearestLivingEntitySensor> MAID_NEAREST_LIVING_ENTITY_SENSOR =
            Registry.register(Registry.SENSOR_TYPE, TouhouLittleMaid.rl("maid_nearest_living_entity"),
                    MaidReflection.newSensorType(MaidNearestLivingEntitySensor::new));
    public static final SensorType<MaidHostilesSensor> MAID_HOSTILES_SENSOR =
            Registry.register(Registry.SENSOR_TYPE, TouhouLittleMaid.rl("maid_hostiles"),
                    MaidReflection.newSensorType(MaidHostilesSensor::new));
    public static final SensorType<MaidPickupEntitiesSensor> MAID_PICKUP_ENTITIES_SENSOR =
            Registry.register(Registry.SENSOR_TYPE, TouhouLittleMaid.rl("maid_pickup_entities"),
                    MaidReflection.newSensorType(MaidPickupEntitiesSensor::new));

    // ------------------------------------------------------------------ 日程表

    public static final Schedule MAID_DAY_SHIFT_SCHEDULES = Registry.register(Registry.SCHEDULE,
            TouhouLittleMaid.rl("maid_day_shift_schedules"),
            // 06:00 ~ 18:00 工作 / 18:00 ~ 22:00 娱乐 / 22:00 ~ 06:00 睡觉
            new ScheduleBuilder(new Schedule())
                    .changeActivityAt(0, Activity.WORK)
                    .changeActivityAt(12000, Activity.IDLE)
                    .changeActivityAt(16000, Activity.REST)
                    .build());
    public static final Schedule MAID_NIGHT_SHIFT_SCHEDULES = Registry.register(Registry.SCHEDULE,
            TouhouLittleMaid.rl("maid_night_shift_schedules"),
            // 18:00 ~ 06:00 工作 / 06:00 ~ 14:00 睡觉 / 14:00 ~ 18:00 娱乐
            new ScheduleBuilder(new Schedule())
                    .changeActivityAt(0, Activity.REST)
                    .changeActivityAt(8000, Activity.IDLE)
                    .changeActivityAt(12000, Activity.WORK)
                    .build());
    public static final Schedule MAID_ALL_DAY_SCHEDULES = Registry.register(Registry.SCHEDULE,
            TouhouLittleMaid.rl("maid_all_day_schedules"),
            new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.WORK).build());

    private InitEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(MAID, EntityMaid.createAttributes());
        // 坐垫同样继承 LivingEntity，必须注册属性表；漏掉会在「构造实体」时直接 NPE
        // （LivingEntity 的构造函数里就调 setHealth(getMaxHealth())），
        // 表现是「一切到坐垫页签就崩」，因为页签图标会把坐垫物品渲染一次、从而构造一个假实体。
        FabricDefaultAttributeRegistry.register(CHAIR,
                com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair.createAttributes());
        // 扫帚同样继承 LivingEntity，道理与坐垫一模一样。
        // 属性表就用 LivingEntity 的默认那一套：上游也没给扫帚定义任何自定义属性，
        // Forge 会自动套 DefaultAttributes，这里显式交出来即可（与坐垫同一行写法）。
        FabricDefaultAttributeRegistry.register(BROOM,
                net.minecraft.world.entity.LivingEntity.createLivingAttributes());
        // 女仆不自然生成，因此无需注册 SpawnPlacements（1.18.2 中该方法为 private）。
        FabricDefaultAttributeRegistry.register(FAIRY,
                com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy.createFairyAttributes());
        // 妖精是自然生成怪，必须注册刷怪位置规则：
        // 不注册时 SpawnPlacements#checkSpawnRules 恒为 true，稻草人驱散会完全失效，
        // 而且飞行生物按原版规则会被判成「刷在空中」，实际上几乎刷不出来。
        MaidReflection.registerSpawnPlacement(FAIRY,
                net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy::checkFairySpawnRules);
    }

}

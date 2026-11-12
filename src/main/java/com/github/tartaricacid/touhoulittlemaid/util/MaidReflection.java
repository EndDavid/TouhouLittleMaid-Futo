package com.github.tartaricacid.touhoulittlemaid.util;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 少量 vanilla 私有构造函数的反射工厂。
 *
 * <p><b>为什么需要它：</b>1.18.2 里 {@link SensorType}、{@link Activity}、
 * {@link MemoryModuleType} 的构造函数都是 {@code private}。上游是 Forge 模组，
 * 通过 AccessTransformer 打开；本移植版原本用 AccessWidener 打开，但
 * Loom 1.15 与 Fabric Loader 0.14.25 对表头格式的要求不一致
 * （Loom 认 {@code classTweaker}，Loader 认 {@code accessWidener}）——
 * 同一份文件无法同时满足两者，会导致“开发能编译、进游戏就报
 * Failed to read accessWidener file”。</p>
 *
 * <p>因此这里改为运行期反射：Minecraft 与 Fabric 都加载在 unnamed module 中，
 * {@code setAccessible(true)} 正常工作，且不依赖任何打包期工具。</p>
 *
 * <h2>反射必须「按参数类型」找，绝不能「按方法名」找</h2>
 * <p><b>这是只在正式环境才会暴露的坑。</b>开发环境（{@code runClient}）跑在
 * <b>named / Mojang</b> 映射下，`SpawnPlacements#register` 就叫 `register`；
 * 而正式环境里 Fabric Loader 会把模组重映射到 <b>intermediary</b>，同一个方法名变成
 * `method_5684` 之类的编号。</p>
 *
 * <p>关键在于：<b>Loom 只会重映射编译期常量池里的类名/字段名/方法引用，
 * 不会去重映射写死在字符串里的方法名</b>。所以
 * {@code getDeclaredMethod("register", …)} 在开发环境成功、在正式环境抛
 * {@code NoSuchMethodException}，表现为「终端 runClient 正常、装进启动器就崩」：</p>
 * <pre>
 * java.lang.IllegalStateException: Failed to register spawn placement for
 *     touhou_little_maid:fairy (unsupported Minecraft version?)
 *   Caused by: java.lang.NoSuchMethodException:
 *     net.minecraft.class_1317.register(…)
 * </pre>
 *
 * <p>参数类型不受影响：它们是 {@code Class} 字面量，会随常量池一起被重映射。
 * 所以下面改成遍历 {@code getDeclaredMethods()}、<b>只比对参数类型</b>，
 * 开发环境与正式环境都能命中。</p>
 *
 * <p>构造函数（{@link #construct}）没有这个问题 —— 构造器在字节码里没有名字，
 * 只按参数类型定位。</p>
 *
 * <p>一旦工具链统一支持 ClassTweaker 与 AccessWidener 双表头，可换回 AW 并删除本类。</p>
 */
public final class MaidReflection {
    private MaidReflection() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends net.minecraft.world.entity.ai.sensing.Sensor<?>> SensorType<T> newSensorType(Supplier<T> factory) {
        return (SensorType<T>) construct(SensorType.class, new Class<?>[]{Supplier.class}, new Object[]{factory});
    }

    public static Activity newActivity(String name) {
        return (Activity) construct(Activity.class, new Class<?>[]{String.class}, new Object[]{name});
    }

    @SuppressWarnings("unchecked")
    public static <U> MemoryModuleType<U> newMemoryModuleType(Optional<Codec<U>> codec) {
        return (MemoryModuleType<U>) construct(MemoryModuleType.class,
                new Class<?>[]{Optional.class}, new Object[]{codec});
    }

    /**
     * 注册 {@code SpawnPlacements}（1.18.2 里 {@code SpawnPlacements#register} 是 <b>private</b>）。
     *
     * <p><b>为什么非注册不可：</b>{@code SpawnPlacements#checkSpawnRules} 在表里找不到类型时
     * <b>直接返回 true</b>，也就是「没有谓词 = 处处可刷」。女仆妖精的稻草人驱散判定挂在
     * 这个谓词上，不注册就等于驱散完全失效。</p>
     *
     * <p>顺带一提，注册 {@code Type.ON_GROUND} + {@code MOTION_BLOCKING_NO_LEAVES}
     * 也会让 {@code NaturalSpawner#isSpawnPositionOk} 走「地面刷怪」那一支，
     * 否则飞行生物按原版规则会被判成「刷在空中」，几乎刷不出来。</p>
     */
    public static <T extends net.minecraft.world.entity.Mob> void registerSpawnPlacement(
            net.minecraft.world.entity.EntityType<T> type,
            net.minecraft.world.entity.SpawnPlacements.Type placementType,
            net.minecraft.world.level.levelgen.Heightmap.Types heightmapType,
            net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> predicate) {
        try {
            Method method = Arrays.stream(net.minecraft.world.entity.SpawnPlacements.class.getDeclaredMethods())
                    .filter(m -> Modifier.isStatic(m.getModifiers()))
                    .filter(m -> Arrays.equals(m.getParameterTypes(),
                            new Class<?>[]{net.minecraft.world.entity.EntityType.class,
                                    net.minecraft.world.entity.SpawnPlacements.Type.class,
                                    net.minecraft.world.level.levelgen.Heightmap.Types.class,
                                    net.minecraft.world.entity.SpawnPlacements.SpawnPredicate.class}))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException(
                            "net.minecraft.class_1317.register(…)"));
            method.setAccessible(true);
            method.invoke(null, type, placementType, heightmapType, predicate);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register spawn placement for "
                    + net.minecraft.core.Registry.ENTITY_TYPE.getKey(type)
                    + " (unsupported Minecraft version?)", e);
        }
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object[] args) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            // 这里失败意味着 Minecraft 版本与预期不符，直接抛出以便第一时间发现
            throw new IllegalStateException("Failed to create " + type.getSimpleName()
                    + " via reflection (unsupported Minecraft version?)", e);
        }
    }
}

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

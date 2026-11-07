package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把女仆妖精加入自然生成表 —— 上游 {@code init/registry/MobSpawnInfoRegistry} 的替身。
 *
 * <p><b>上游做法：</b>Forge 的 {@code WorldEvent.PotentialSpawns} 允许在「每次刷怪尝试」
 * 时往权重表里插一条。Fabric 没有这个事件，最贴近的等价点是
 * {@code NaturalSpawner#mobsAt} —— 它的参数里恰好有 {@code ServerLevel}、
 * {@code MobCategory} 和 {@code BlockPos}，正好够用来做「维度黑名单」与
 * 「同群系已有僵尸才加」这两条上游规则。</p>
 *
 * <p><b>上游规则逐条对应：</b></p>
 * <ul>
 *     <li>{@code spawnProbability <= 0} 时直接跳过（省掉后续所有判断）；</li>
 *     <li>只在 {@link MobCategory#MONSTER} 上加；</li>
 *     <li>维度在黑名单里就不加（默认下界 / 末地 / 暮色森林）；</li>
 *     <li>该群系原有的刷怪表里**有僵尸**才加 —— 等价于「这个群系能刷普通怪」，
 *         从而自动排除蘑菇岛之类没有敌对生物的群系；</li>
 *     <li>权重 = 配置值，群体规模 2~4（与上游 {@code SpawnerData(..., 2, 4)} 一致）。</li>
 * </ul>
 *
 * <p>真正的「稻草人驱散」不在这里做，而在 {@code EntityFairy#checkFairySpawnRules}
 * 里按 POI 判定 —— 与上游分工一致（前者决定「能不能被抽中」，后者决定「这个位置能不能刷」）。</p>
 */
@Mixin(net.minecraft.world.level.NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    /**
     * 「原刷怪表 → 加入妖精后的刷怪表」缓存。
     *
     * <p><b>为什么需要缓存：</b>{@code mobsAt} 是刷怪循环里的热路径，
     * 每 tick 会被调用几十次。每次都 {@code new ArrayList} + {@code WeightedRandomList.create}
     * 会造成可观的垃圾与 CPU 开销（原版这里返回的是生物群系里预先建好的缓存对象）。</p>
     *
     * <p><b>为什么用 {@code HashMap} 而不是 {@code IdentityHashMap}：</b>
     * {@code WeightedRandomList} 没有重写 {@code equals/hashCode}（已用 javap 确认），
     * 因此 {@code HashMap} 的语义就是按对象身份查找，效果与 {@code IdentityHashMap} 相同，
     * 同时还能用 {@code ConcurrentHashMap} 保证并发安全。</p>
     *
     * <p>生物群系数量是有限且稳定的（几十个），所以这张表不会无限增长。</p>
     */
    private static final Map<WeightedRandomList<?>, WeightedRandomList<?>> RESULT_CACHE =
            new ConcurrentHashMap<>();

    /**
     * 生成 {@link #RESULT_CACHE} 时用的权重。配置改了就必须整体失效。
     */
    private static volatile int cachedWeight = -1;

    /**
     * 复用的 {@code SpawnerData} 实例（上游同样只建一次，避免重复分配）。
     */
    private static MobSpawnSettings.SpawnerData fairySpawnData;

    @SuppressWarnings("unchecked")
    @Inject(method = "mobsAt", at = @At("RETURN"), cancellable = true)
    private static void tlm$addFairyToSpawnList(ServerLevel level,
                                                StructureFeatureManager structures,
                                                ChunkGenerator generator,
                                                MobCategory category,
                                                BlockPos pos,
                                                Holder<Biome> biome,
                                                CallbackInfoReturnable<WeightedRandomList<MobSpawnSettings.SpawnerData>> cir) {
        // 优先级 1：只处理敌对生物这一档
        if (category != MobCategory.MONSTER) {
            return;
        }
        // 优先级 2：配置关掉了（0 = 完全不生成）
        int weight = MiscConfig.maidFairySpawnProbability();
        if (weight <= 0) {
            return;
        }
        // 优先级 3：维度黑名单
        String dimension = level.dimension().location().toString();
        if (MiscConfig.fairyBlacklistDimension().contains(dimension)) {
            return;
        }
        WeightedRandomList<MobSpawnSettings.SpawnerData> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        // 权重变了就整体失效（配置热重载）
        if (cachedWeight != weight) {
            RESULT_CACHE.clear();
            fairySpawnData = null;
            cachedWeight = weight;
        }
        WeightedRandomList<MobSpawnSettings.SpawnerData> cached =
                (WeightedRandomList<MobSpawnSettings.SpawnerData>) RESULT_CACHE.get(original);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }
        List<MobSpawnSettings.SpawnerData> entries = original.unwrap();
        // 优先级 4：该群系得能刷普通怪（用僵尸当探针，与上游一致）。
        // 这一条顺带排除了蘑菇岛之类没有敌对生物的群系。
        boolean canZombieSpawn = entries.stream()
                .anyMatch(data -> data.type == EntityType.ZOMBIE);
        if (!canZombieSpawn) {
            // 负结果也缓存：省掉后续每次的 stream 扫描
            RESULT_CACHE.put(original, original);
            cir.setReturnValue(original);
            return;
        }
        // 已经加过就不重复加（重复会把权重成倍放大）
        for (MobSpawnSettings.SpawnerData data : entries) {
            if (data.type == InitEntities.FAIRY) {
                RESULT_CACHE.put(original, original);
                cir.setReturnValue(original);
                return;
            }
        }
        if (fairySpawnData == null || fairySpawnData.getWeight().asInt() != weight) {
            fairySpawnData = new MobSpawnSettings.SpawnerData(InitEntities.FAIRY, weight, 2, 4);
        }
        List<MobSpawnSettings.SpawnerData> modified = new ArrayList<>(entries.size() + 1);
        modified.addAll(entries);
        modified.add(fairySpawnData);
        WeightedRandomList<MobSpawnSettings.SpawnerData> result = WeightedRandomList.create(modified);
        RESULT_CACHE.put(original, result);
        cir.setReturnValue(result);
    }
}


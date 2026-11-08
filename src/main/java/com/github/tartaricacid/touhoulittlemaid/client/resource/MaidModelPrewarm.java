package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在后台线程预构建全部女仆模型几何。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>上游在资源加载阶段（{@code CustomPackLoader}）就把<b>所有</b>模型的几何烘焙好，
 * 因此换肤界面可以随便切 {@code ModelId}，画网格没有任何构建开销。</p>
 *
 * <p>本移植版不能那么做：旧版（1.10.0）模型有 194 个，其中最大的
 * {@code zhiban.json} 有 449 KB，全部同步预构建会让启动阶段的渲染线程卡住十几秒。
 * 于是 {@code GeckoLibCache#getGeoModel} 改成了「首次用到才构建」。这个妥协本身没问题，
 * 但<b>第一次用到</b>的时机落在了换肤界面里 —— 一屏 55 个模型，渲染线程要当场同步解析
 * 并构建它们，于是「浏览模型时卡死」。</p>
 *
 * <p>解法：把这份工作挪到后台线程。构建是<b>纯数据变换</b>
 * （读 JSON → 改字段 → {@code RawGeometryTree} → {@code GeoBuilder}），
 * 不碰 OpenGL、也不碰 {@code MolangParser}，所以可以安全地离开渲染线程。
 * 生成的 {@code GeoModel} 只是一堆不可变的骨骼 / 立方体数据，渲染时只读。</p>
 *
 * <h2>并发约定</h2>
 *
 * <ul>
 *     <li>{@code GeckoLibCache} 的容器已换成 {@code ConcurrentHashMap}
 *         （{@code failed*} 用 {@code newKeySet()}）；</li>
 *     <li>{@code getGeoModel} / {@code getAnimationFile} 加了 {@code synchronized}，
 *         保证「预构建线程」与「渲染线程」不会重复构建同一个 id，也不会同时写 resolver；</li>
 *     <li>本类只有一个守护线程，按顺序构建，不并发抢 CPU。</li>
 * </ul>
 *
 * <h2>失败处理</h2>
 *
 * <p>构建失败的 id 由 {@code GeckoLibCache} 记进 {@code failedGeoModels}，
 * 之后不会再重试。这里也把它们当作「已处理」推进度，避免整轮卡在坏条目上。</p>
 */
public final class MaidModelPrewarm {
    /** 后台线程是否已经在跑（资源重载会重新触发，这里同一时刻只允许一轮）。 */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    /** 已经处理完的模型数 / 总数，供 GUI 或命令查询进度。 */
    private static final AtomicInteger DONE = new AtomicInteger(0);
    private static final AtomicInteger TOTAL = new AtomicInteger(0);

    private MaidModelPrewarm() {
    }

    /**
     * 启动一轮后台预构建。已有任务在跑时直接返回（资源重载会重复调用）。
     *
     * <p>只构建<b>几何</b>：动画文件需要共享的 {@code MolangParser}，
     * 而那个解析器不是线程安全的，且动画合并本身很轻（多数旧版模型只声明
     * {@code .js} 伪路径，会被 {@code buildAnimations} 直接跳过），
     * 因此留给渲染线程按需处理。</p>
     */
    public static void start() {
        if (!RUNNING.compareAndSet(false, true)) {
            return;
        }
        List<ResourceLocation> ids = new ArrayList<>();
        for (MaidModelInfo info : MaidModelRegistry.all()) {
            if (info.getModel() != null) {
                ids.add(info.getModelId());
            }
        }
        DONE.set(0);
        TOTAL.set(ids.size());
        if (ids.isEmpty()) {
            RUNNING.set(false);
            return;
        }

        Thread worker = new Thread(() -> {
            long startNanos = System.nanoTime();
            try {
                GeckoLibCache cache = GeckoLibCache.getInstance();
                for (ResourceLocation id : ids) {
                    cache.getGeoModel(id);
                    DONE.incrementAndGet();
                }
                long millis = (System.nanoTime() - startNanos) / 1_000_000L;
                TouhouLittleMaid.LOGGER.info(
                        "[Futo] prewarmed {} maid model geometries on a worker thread in {} ms",
                        ids.size(), millis);
            } catch (Throwable e) {
                // 预构建失败绝不能影响游戏：渲染线程仍会在需要时按需构建
                TouhouLittleMaid.LOGGER.error("[Futo] maid model prewarm aborted", e);
            } finally {
                RUNNING.set(false);
            }
        }, "Futo-MaidModelPrewarm");
        // 守护线程：退出游戏时不必等它跑完
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    /** 预构建是否还在进行。 */
    public static boolean isRunning() {
        return RUNNING.get();
    }

    /** 已处理数 / 总数，未开始或已结束时为 0。 */
    public static int[] progress() {
        return new int[]{DONE.get(), TOTAL.get()};
    }
}


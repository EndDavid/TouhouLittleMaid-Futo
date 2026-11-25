package com.github.tartaricacid.touhoulittlemaid.compat.cloth;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.config.GeneralConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.util.version.TComponent;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置界面的<b>自检</b>，只在 JVM 参数 {@code -Dfuto.cloth.selftest=true} 时激活。
 *
 * <h2>为什么需要它</h2>
 * <p>「配置界面能不能打开」是这一轮的核心交付，但它天然要人点一下才知道结果 ——
 * 而「构建这张表」里的每一步都可能在运行时炸：物品下拉框拿到的物品为 {@code null}、
 * TTS 语言下拉框在语言表为空时 NPE、{@code Utils.getConfigFolder()} 在客户端拿不到目录……
 * 这些编译期全都发现不了。所以这里在<b>标题画面出现后</b>（此时物品注册表与语言表都就绪）
 * 依次做三件事，并把结果写进日志：</p>
 * <ol>
 *     <li>调 {@link MenuIntegration#getConfigBuilder()} —— 等于把六个分类里的每条都构建一遍；</li>
 *     <li>检查六个分类都存在，并统计条目总数；</li>
 *     <li>把 {@link ConfigBuilder#build()} 出来的界面设为当前界面（触发它自己的 {@code init()}），
 *         再验证一次「改值 → 保存 → TOML 里能读回」。</li>
 * </ol>
 *
 * <h2>为什么不是单元测试</h2>
 * <p>整条链路依赖 Minecraft 客户端的注册表与语言管理器，脱离游戏起不来；
 * Loom 的 {@code runClient} 是唯一能真实复现的环境。这个类因此被做成「在真实客户端里跑一遍」
 * 的诊断，由 {@code port/cloth-selftest.ps1} 来驱动与判定。</p>
 *
 * <p><b>玩家不会受影响</b>：没有这个 JVM 参数时，调用方在解析 {@code &&} 右侧之前就短路了，
 * 本类连加载都不会发生 —— Cloth Config 未安装时更不会被碰到。</p>
 */
@Environment(EnvType.CLIENT)
public final class ClothConfigSelfTest {
    /** TOML 里用来验证「改值能落盘」的那一项，默认 12。 */
    private static final int PROBE_VALUE = 13;
    private static final int PROBE_ORIGINAL = 12;

    /** 六个分类。键名与 {@code MenuIntegration} / {@code GlobalAIIntegration} 里用的完全一致。 */
    private static final String[] CATEGORY_KEYS = {
            "config.touhou_little_maid.maid",
            "config.touhou_little_maid.chair",
            "config.touhou_little_maid.misc",
            "config.touhou_little_maid.vanilla",
            "config.touhou_little_maid.render",
            "config.touhou_little_maid.global_ai"
    };

    private static int stage = 0;
    private static ConfigBuilder builder;

    private ClothConfigSelfTest() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClothConfigSelfTest::tick);
        TouhouLittleMaid.LOGGER.info("[Futo] cloth selftest: armed, waiting for the title screen");
    }

    private static void tick(Minecraft client) {
        if (stage == 0) {
            // 等到标题画面：此时物品注册表与语言表都已就绪，和玩家点开界面时的状态一致
            if (!(client.screen instanceof TitleScreen)) {
                return;
            }
            stage = 1;
            buildAndOpen(client);
            return;
        }
        if (stage == 1) {
            // 界面已经被设为当前界面；能走到下一个 tick 就说明它的 init() 没抛异常
            stage = 2;
            String current = client.screen == null ? "null" : client.screen.getClass().getSimpleName();
            TouhouLittleMaid.LOGGER.info("[Futo] cloth selftest: screen init OK, current={}", current);
            return;
        }
        if (stage == 2) {
            stage = 3;
            saveRoundTrip();
            TouhouLittleMaid.LOGGER.info("[Futo] cloth selftest: DONE");
        }
    }

    private static void buildAndOpen(Minecraft client) {
        builder = MenuIntegration.getConfigBuilder();

        int total = 0;
        for (String key : CATEGORY_KEYS) {
            Component title = TComponent.translatable(key);
            if (!builder.hasCategory(title)) {
                TouhouLittleMaid.LOGGER.error("[Futo] cloth selftest: MISSING category {}", key);
                continue;
            }
            ConfigCategory category = builder.getOrCreateCategory(title);
            int size = category.getEntries().size();
            total += size;
            TouhouLittleMaid.LOGGER.info("[Futo] cloth selftest: category {} has {} entries", key, size);
        }
        TouhouLittleMaid.LOGGER.info("[Futo] cloth selftest: {} categories, {} entries in total",
                CATEGORY_KEYS.length, total);

        // 真正设为当前界面：等价于「玩家点了那颗齿轮」，会跑一遍 Cloth 自己的 init()
        client.setScreen(builder.build());
    }

    /**
     * 「改值 → 保存 → 从磁盘读回」。
     *
     * <p>这是 {@code setSavingRunnable} 那条链路的替身：界面里点「保存」调的就是
     * {@link GeneralConfig#save()}，所以这里直接调它，再去 TOML 里找探针值。
     * 找不到就说明配置根本落不了盘 —— 那才是「改了不生效」最隐蔽的一种。</p>
     */
    private static void saveRoundTrip() {
        try {
            MaidConfig.MAID_WORK_RANGE.set(PROBE_VALUE);
            GeneralConfig.save();

            Path toml = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                    .resolve("touhou_little_maid-common.toml");
            if (!Files.isRegularFile(toml)) {
                TouhouLittleMaid.LOGGER.error("[Futo] cloth selftest: common toml not found at {}", toml);
                return;
            }
            String text = Files.readString(toml, StandardCharsets.UTF_8);
            String needle = "MaidWorkRange = " + PROBE_VALUE;
            if (text.contains(needle)) {
                TouhouLittleMaid.LOGGER.info("[Futo] cloth selftest: save/flush OK ({})", needle);
            } else {
                TouhouLittleMaid.LOGGER.error("[Futo] cloth selftest: '{}' NOT found in {}", needle, toml);
            }

            // 还原，免得把开发环境的配置留在探针值上
            MaidConfig.MAID_WORK_RANGE.set(PROBE_ORIGINAL);
            GeneralConfig.save();
        } catch (Throwable t) {
            TouhouLittleMaid.LOGGER.error("[Futo] cloth selftest: save round trip threw", t);
        }
    }
}

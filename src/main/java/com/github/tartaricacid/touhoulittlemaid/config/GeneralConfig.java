package com.github.tartaricacid.touhoulittlemaid.config;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.ChairConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.RenderConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.VanillaConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * COMMON 配置（所有玩家共享），移植自上游 {@code config/GeneralConfig}。
 *
 * <p>上游还有 {@code AIConfig}（AI 聊天 / TTS / STT），本轮已移植并接在这里。</p>
 */
public final class GeneralConfig {
    /**
     * 构建好的 COMMON spec。
     *
     * <p>Cloth Config 界面里的 {@code setSaveConsumer} 只改内存中的值，
     * 必须有人调 {@link ForgeConfigSpec#save()} 才会落盘 —— 上游靠 Forge 自己的
     * 配置界面顺手保存，本移植版是自建界面，所以这里留一个引用给它用。</p>
     */
    private static ForgeConfigSpec spec;

    private GeneralConfig() {
    }

    public static ForgeConfigSpec init() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        MaidConfig.init(builder);
        AIConfig.init(builder);
        ChairConfig.init(builder);
        MiscConfig.init(builder);
        VanillaConfig.init(builder);
        RenderConfig.init(builder);
        spec = builder.build();
        return spec;
    }

    /** 配置界面点「保存」时调用：把内存里的值写回 {@code config/touhou_little_maid-common.toml}。 */
    public static void save() {
        if (spec != null && spec.isLoaded()) {
            spec.save();
        }
    }
}

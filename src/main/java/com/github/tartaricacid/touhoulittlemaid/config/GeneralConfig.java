package com.github.tartaricacid.touhoulittlemaid.config;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.ChairConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.RenderConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.VanillaConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * COMMON 配置（所有玩家共享），移植自上游 {@code config/GeneralConfig}。
 *
 * <p>上游还有 {@code AIConfig}（AI 聊天 / TTS / STT），它依赖尚未移植的
 * {@code ai.manager.setting.AvailableSites}，因此暂时不建；等 AI 子系统移植后再补。</p>
 */
public final class GeneralConfig {
    private GeneralConfig() {
    }

    public static ForgeConfigSpec init() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        MaidConfig.init(builder);
        ChairConfig.init(builder);
        MiscConfig.init(builder);
        VanillaConfig.init(builder);
        RenderConfig.init(builder);
        return builder.build();
    }
}


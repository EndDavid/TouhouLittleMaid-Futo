package com.github.tartaricacid.touhoulittlemaid.config;

import com.google.common.collect.Lists;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * SERVER 配置（按世界存档保存），移植自上游 {@code config/ServerConfig}。
 */
public final class ServerConfig {
    /**
     * 客户端需要下载的包（URL 列表）。
     */
    public static ForgeConfigSpec.ConfigValue<List<String>> CLIENT_PACK_DOWNLOAD_URLS;
    /**
     * 开启女仆 AI 耗时检测。
     */
    public static ForgeConfigSpec.BooleanValue MAID_AI_TIME_DEBUG;

    private ServerConfig() {
    }

    public static ForgeConfigSpec init() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("The maid pack that the client player needs to download, needs to fill in the URL and the SHA1 value of the file");
        builder.comment("Example: [\"https://www.dropbox.com/download/apple.zip\", \"https://www.dropbox.com/download/cat.zip\"]");
        CLIENT_PACK_DOWNLOAD_URLS = builder.define("ClientPackDownloadUrls", Lists.newArrayList());

        builder.comment("When turned on, when the maid AI executes more than 50ms, the entity information of the problem will be logged");
        MAID_AI_TIME_DEBUG = builder.define("MaidAITimeDebug", false);

        return builder.build();
    }

    /**
     * 是否记录 AI 耗时（配置未加载时回退 false）。
     */
    public static boolean maidAiTimeDebug() {
        try {
            return MAID_AI_TIME_DEBUG != null && MAID_AI_TIME_DEBUG.get();
        } catch (Exception e) {
            return false;
        }
    }
}


package com.github.tartaricacid.touhoulittlemaid.config;

import com.google.common.collect.Lists;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * SERVER 配置（按世界存档保存），移植自上游 {@code config/ServerConfig}。
 */
public final class ServerConfig {
    /**
     * 构建好的 SERVER spec，供配置界面保存时使用（见 {@link #save()}）。
     */
    private static ForgeConfigSpec spec;
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

        spec = builder.build();
        return spec;
    }

    /** 配置界面点「保存」时调用（SERVER 配置只改当前世界存档里的那份）。 */
    public static void save() {
        if (spec != null && spec.isLoaded()) {
            spec.save();
        }
    }

    /**
     * 是否记录 AI 耗时。
     *
     * <p>配置未加载时 {@code get()} 返回默认值 {@code false}，无需 try/catch
     * （见 {@code MaidConfig} 末尾的说明）。</p>
     */
    public static boolean maidAiTimeDebug() {
        return MAID_AI_TIME_DEBUG != null && MAID_AI_TIME_DEBUG.get();
    }
}

package com.github.tartaricacid.touhoulittlemaid.config.subconfig;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 原版替换相关配置，移植自上游 {@code config/subconfig/VanillaConfig}。
 *
 * <p>这些开关决定「史莱姆换成油库里」「经验球贴图换成点数」等替换，属于渲染 / 实体替换层，
 * 尚未移植；配置项先建好，等替换层就位后直接读取即可。</p>
 */
public final class VanillaConfig {
    public static ForgeConfigSpec.BooleanValue REPLACE_SLIME_MODEL;
    public static ForgeConfigSpec.BooleanValue REPLACE_XP_TEXTURE;
    public static ForgeConfigSpec.BooleanValue REPLACE_TOTEM_TEXTURE;
    public static ForgeConfigSpec.BooleanValue REPLACE_XP_BOTTLE_TEXTURE;

    private VanillaConfig() {
    }

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("vanilla");

        builder.comment("Whether to replace the vanilla slime model with the yukkuri.");
        REPLACE_SLIME_MODEL = builder.define("ReplaceSlimeModel", true);

        builder.comment("Whether to replace the vanilla xp orb texture with the point items.");
        REPLACE_XP_TEXTURE = builder.define("ReplaceXPTexture", true);

        builder.comment("Whether to replace the vanilla totem texture with the life point.");
        REPLACE_TOTEM_TEXTURE = builder.define("ReplaceTotemTexture", true);

        builder.comment("Whether to replace the vanilla bottle of xp texture with the point items.");
        REPLACE_XP_BOTTLE_TEXTURE = builder.define("ReplaceXPBottleTexture", true);

        builder.pop();
    }

    // ------------------------------------------------------------------ 安全取值

    /**
     * 安全读取布尔配置。
     *
     * <p>为什么必须包一层：{@link ForgeConfigSpec.BooleanValue#get()} 在配置<b>尚未加载</b>
     * 时会抛 {@code IllegalStateException}。渲染器是在资源重载 / 首帧就可能被调用的,
     * 那一刻服务端配置可能还没加载完，直接 get 会崩客户端。</p>
     */
    private static boolean boolOr(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * 是否把原版经验球贴图换成点数贴片（上游 {@code ReplaceXPTexture}）。
     */
    public static boolean replaceXpTexture() {
        return boolOr(REPLACE_XP_TEXTURE, true);
    }
    /**
     * 是否把原版附魔之瓶的图标换成模组的「点」（上游 {@code ReplaceXPBottleTexture}）。
     */
    public static boolean replaceXpBottleTexture() {
        return boolOr(REPLACE_XP_BOTTLE_TEXTURE, true);
    }

    /**
     * 是否把原版不死图腾的图标换成模组的「命点」（上游 {@code ReplaceTotemTexture}）。
     */
    public static boolean replaceTotemTexture() {
        return boolOr(REPLACE_TOTEM_TEXTURE, true);
    }

}
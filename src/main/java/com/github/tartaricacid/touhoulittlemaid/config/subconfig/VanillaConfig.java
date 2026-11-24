package com.github.tartaricacid.touhoulittlemaid.config.subconfig;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 原版替换相关配置，移植自上游 {@code config/subconfig/VanillaConfig}。
 *
 * <p>这些开关决定「史莱姆换成油库里」「经验球贴图换成点数」等替换，属于渲染 / 实体替换层。
 * 目前史莱姆（{@code ReplaceSlimeModel}）与经验球（{@code ReplaceXPTexture}）已接上；
 * 图腾 / 附魔之瓶走的是物品模型层的替换。</p>
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

    // ------------------------------------------------------------------ 取值
    //
    // 无需 try/catch：配置未加载时 {@code ConfigValue#get()} 返回默认值（依据见 MaidConfig 文件末尾）。
    //
    // 早先这里写的是「未加载会抛 IllegalStateException，首帧渲染会崩客户端」——前提是错的。
    // 服务端配置在客户端未同步到时读到的就是本地默认值，不会抛。

    private static boolean boolOr(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        return value == null ? fallback : value.get();
    }

    /**
     * 是否把原版经验球贴图换成点数贴片（上游 {@code ReplaceXPTexture}）。
     */
    public static boolean replaceXpTexture() {
        return boolOr(REPLACE_XP_TEXTURE, true);
    }

    /**
     * 是否把原版史莱姆换成油库里模型（上游 {@code ReplaceSlimeModel}）。
     *
     * <p>由 {@code EntityYukkuriSlimeRender} 读取；关闭时该渲染器直接转发给原版
     * {@code SlimeRenderer}。</p>
     */
    public static boolean replaceSlimeModel() {
        return boolOr(REPLACE_SLIME_MODEL, true);
    }

    /**
     * 是否把原版不死图腾的图标换成模组的「命点」（上游 {@code ReplaceTotemTexture}）。
     */
    public static boolean replaceTotemTexture() {
        return boolOr(REPLACE_TOTEM_TEXTURE, true);
    }

    /**
     * 是否把原版附魔之瓶的图标换成模组的「点」（上游 {@code ReplaceXPBottleTexture}）。
     */
    public static boolean replaceXpBottleTexture() {
        return boolOr(REPLACE_XP_BOTTLE_TEXTURE, true);
    }

}
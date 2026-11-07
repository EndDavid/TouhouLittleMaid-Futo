package com.github.tartaricacid.touhoulittlemaid.config.subconfig;

import com.google.common.collect.Lists;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 杂项配置，移植自上游 {@code config/subconfig/MiscConfig}。
 *
 * <p>其中「妖精女仆 / 灵力点 / 神社灯 / 稻草人」等条目属于尚未移植的子系统，
 * 但配置文件结构保持与上游一致，等对应系统移植后可以直接读取，不必改配置格式。</p>
 */
public final class MiscConfig {
    public static ForgeConfigSpec.DoubleValue MAID_FAIRY_POWER_POINT;
    public static ForgeConfigSpec.IntValue MAID_FAIRY_SPAWN_PROBABILITY;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> MAID_FAIRY_BLACKLIST_DIMENSION;
    public static ForgeConfigSpec.DoubleValue PLAYER_DEATH_LOSS_POWER_POINT;
    public static ForgeConfigSpec.BooleanValue GIVE_SMART_SLAB;
    public static ForgeConfigSpec.BooleanValue GIVE_PATCHOULI_BOOK;
    public static ForgeConfigSpec.DoubleValue SHRINE_LAMP_EFFECT_COST;
    public static ForgeConfigSpec.DoubleValue SHRINE_LAMP_MAX_STORAGE;
    public static ForgeConfigSpec.IntValue SHRINE_LAMP_MAX_RANGE;
    public static ForgeConfigSpec.BooleanValue CLOSE_OPTIFINE_WARNING;
    public static ForgeConfigSpec.IntValue SCARECROW_RANGE;
    public static ForgeConfigSpec.BooleanValue USE_NEW_MAID_FAIRY_MODEL;
    public static ForgeConfigSpec.BooleanValue MODEL_ICON_CACHE;

    private MiscConfig() {
    }

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("misc");

        builder.comment("Maid fairy's power point");
        MAID_FAIRY_POWER_POINT = builder.defineInRange("MaidFairyPowerPoint", 0.16, 0, 5);

        builder.comment("Maid fairy's spawn probability (zombie is 100, enderman is 10)");
        MAID_FAIRY_SPAWN_PROBABILITY = builder.defineInRange("MaidFairySpawnProbability", 70, 0, Integer.MAX_VALUE);

        builder.comment("The following dimension do not spawn maid fairy");
        MAID_FAIRY_BLACKLIST_DIMENSION = builder.defineList("MaidFairyBlacklistDimension",
                Lists.newArrayList(Level.NETHER.location().toString(), Level.END.location().toString(),
                        "twilightforest:twilight_forest"), MiscConfig::checkId);

        builder.comment("Loss power point after player death");
        PLAYER_DEATH_LOSS_POWER_POINT = builder.defineInRange("PlayerDeathLossPowerPoint", 1.0, 0, 5);

        builder.comment("Give a soul spell item for player first join");
        GIVE_SMART_SLAB = builder.define("GiveSoulSpell", true);

        builder.comment("Give the Memorizable Gensokyo book item for player first join");
        GIVE_PATCHOULI_BOOK = builder.define("GivePatchouliBook", true);

        builder.comment("Shrine Lamp Effect Cost (Power Point/Per Hour)");
        SHRINE_LAMP_EFFECT_COST = builder.defineInRange("ShrineLampEffectCost", 0.9, 0, Double.MAX_VALUE);

        builder.comment("Shrine Lamp Max Storage Power Point");
        SHRINE_LAMP_MAX_STORAGE = builder.defineInRange("ShrineLampMaxStorage", 100, 0, Double.MAX_VALUE);

        builder.comment("Shrine Lamp Max Range Of Absorb Power Point");
        SHRINE_LAMP_MAX_RANGE = builder.defineInRange("ShrineLampMaxRange", 6, 0, Integer.MAX_VALUE);

        builder.comment("Whether to turn off the Optifine warning");
        CLOSE_OPTIFINE_WARNING = builder.define("CloseOptifineWarning", false);

        builder.comment("The range of the scarecrow to prevent the fairy maid from spawning");
        SCARECROW_RANGE = builder.defineInRange("ScarecrowRange", 16 * 3, 0, Integer.MAX_VALUE);

        builder.comment("Whether to use the new version of the Fairy Maid model");
        USE_NEW_MAID_FAIRY_MODEL = builder.define("UseNewMaidFairyModel", true);

        builder.comment("Whether to enable model icon caching");
        MODEL_ICON_CACHE = builder.define("ModelIconCache", true);

        builder.pop();
    }

    private static boolean checkId(Object o) {
        if (o instanceof String name) {
            return ResourceLocation.isValidResourceLocation(name);
        }
        return false;
    }

    // ------------------------------------------------------------------ 安全取值

    private static boolean boolOr(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * 是否启用模型图标缓存（渲染层用）。
     */
    public static boolean modelIconCache() {
        return boolOr(MODEL_ICON_CACHE, true);
    }

    /**
     * 是否关闭 OptiFine 警告（客户端提示用）。
     */
    public static boolean closeOptifineWarning() {
        return boolOr(CLOSE_OPTIFINE_WARNING, false);
    }

    /**
     * 是否使用新版女仆妖精模型。
     *
     * <p><b>本移植版暂不生效：</b>新版模型是 {@code simplebedrockmodel} 的 JSON 模型，
     * 工作区里只有它的贴图（{@code textures/entity/new_maid_fairy/*.png}），没有 JSON。
     * 配置项保留是为了配置文件结构与上游一致，等 JSON 补齐后由
     * {@code EntityFairyRenderer} 接上即可。</p>
     */
    public static boolean useNewMaidFairyModel() {
        return boolOr(USE_NEW_MAID_FAIRY_MODEL, false);
    }

    /**
     * 妖精女仆不生成的维度 id 列表。
     */
    public static List<String> fairyBlacklistDimension() {
        try {
            if (MAID_FAIRY_BLACKLIST_DIMENSION != null) {
                return new ArrayList<>(MAID_FAIRY_BLACKLIST_DIMENSION.get());
            }
        } catch (Exception ignored) {
            // 配置未加载，回退默认值
        }
        return Lists.newArrayList(Level.NETHER.location().toString(), Level.END.location().toString(),
                "twilightforest:twilight_forest");
    }

    /**
     * 妖精的刷怪权重（僵尸是 100，末影人是 10）。返回 0 表示完全不生成。
     *
     * <p>这个方法会被 {@code NaturalSpawnerMixin} 每 tick 高频调用，
     * 所以不能直接 {@code MAID_FAIRY_SPAWN_PROBABILITY.get()} ——
     * 配置未加载时会抛异常，异常从刷怪循环里冒出去会直接崩服。</p>
     */
    public static int maidFairySpawnProbability() {
        try {
            return MAID_FAIRY_SPAWN_PROBABILITY == null ? 70 : MAID_FAIRY_SPAWN_PROBABILITY.get();
        } catch (Exception e) {
            return 70;
        }
    }

    /**
     * 女仆妖精死亡时掉落的灵力（1.00 灵力 = 100 P 点）。
     */
    public static double maidFairyPowerPoint() {
        try {
            return MAID_FAIRY_POWER_POINT == null ? 0.16 : MAID_FAIRY_POWER_POINT.get();
        } catch (Exception e) {
            return 0.16;
        }
    }

    /**
     * 玩家死亡时损失的灵力。
     *
     * <p>wiki 上「死亡后无论如何都会失去所有 P 点」的说法已经过时：
     * 上游代码扣的是这个配置值（默认 1.00），不是清空。</p>
     */
    public static double playerDeathLossPowerPoint() {
        try {
            return PLAYER_DEATH_LOSS_POWER_POINT == null ? 1.0 : PLAYER_DEATH_LOSS_POWER_POINT.get();
        } catch (Exception e) {
            return 1.0;
        }
    }

    /**
     * 稻草人驱散妖精的半径（正方形边长）。
     */
    public static int scarecrowRange() {
        try {
            return SCARECROW_RANGE == null ? 48 : SCARECROW_RANGE.get();
        } catch (Exception e) {
            return 48;
        }
    }
}

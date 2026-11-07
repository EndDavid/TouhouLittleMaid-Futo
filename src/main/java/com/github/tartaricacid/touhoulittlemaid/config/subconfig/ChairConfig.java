package com.github.tartaricacid.touhoulittlemaid.config.subconfig;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 坐垫（{@code EntityChair}）相关配置，移植自上游 {@code config/subconfig/ChairConfig}。
 *
 * <p>坐垫系统尚未移植，因此只建立配置项占位，等实体接入后直接读取。</p>
 */
public final class ChairConfig {
    public static ForgeConfigSpec.BooleanValue CHAIR_CHANGE_MODEL;
    public static ForgeConfigSpec.BooleanValue CHAIR_CAN_DESTROYED_BY_ANYONE;

    private ChairConfig() {
    }

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("chair");

        builder.comment("Chair can switch models freely");
        CHAIR_CHANGE_MODEL = builder.define("ChairChangeModel", true);

        builder.comment("Chair can be destroyed by anyone");
        CHAIR_CAN_DESTROYED_BY_ANYONE = builder.define("ChairCanDestroyedByAnyone", true);

        builder.pop();
    }
}

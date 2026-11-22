package com.github.tartaricacid.touhoulittlemaid.config.subconfig;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 坐垫（{@code EntityChair}）相关配置，移植自上游 {@code config/subconfig/ChairConfig}。
 *
 * <p>上游这两个配置项：{@code ChairChangeModel} 决定玩家能否换坐垫模型
 * （{@code ChairModelPacket} 里做判定，创造模式玩家不受限）;
 * {@code ChairCanDestroyedByAnyone} 决定谁能在蹲下左键时拆掉别人的坐垫
 * （{@link com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair#canKillEntity}）。</p>
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
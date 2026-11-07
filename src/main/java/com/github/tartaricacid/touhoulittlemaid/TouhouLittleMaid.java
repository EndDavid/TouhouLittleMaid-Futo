package com.github.tartaricacid.touhoulittlemaid;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Touhou Little Maid: Futo —— 常量与公共入口。
 * modid 与上游保持一致（touhou_little_maid），保证数据包 / 模型包 / 附属模组兼容。
 */
public final class TouhouLittleMaid {
    public static final String MOD_ID = "touhou_little_maid";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private TouhouLittleMaid() {
    }

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}


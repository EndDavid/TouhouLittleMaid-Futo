package com.github.tartaricacid.touhoulittlemaid;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
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

    /**
     * 界面上显示给玩家看的版本串，形如 {@code 1.18.2-1.1-futo}。
     *
     * <p>两段都是<b>读出来的</b>，不再手写常量：Minecraft 版本取
     * {@link SharedConstants}，本模组版本取 Fabric 从 {@code fabric.mod.json} 读到的
     * 元数据（那个值由 {@code build.gradle} 从 {@code gradle.properties} 的
     * {@code mod_version} 展开）。以前界面里写死的是 {@code "1.18.2-futo.1"}，
     * 每次发版都得记着改一处，漏掉就会出现「jar 叫 1.1-futo、界面上还是旧号」。</p>
     *
     * <p>拿不到元数据时（理论上只在类被脱离 Loader 单独加载时发生）退回 {@code dev}，
     * 让界面仍能画出一行而不是抛异常。</p>
     */
    public static String versionString() {
        String minecraft = SharedConstants.getCurrentVersion().getName();
        String mod = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        return minecraft + "-" + mod;
    }
}
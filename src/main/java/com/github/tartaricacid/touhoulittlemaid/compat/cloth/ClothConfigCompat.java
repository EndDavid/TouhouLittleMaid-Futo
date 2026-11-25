package com.github.tartaricacid.touhoulittlemaid.compat.cloth;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.Minecraft;

/**
 * 打开 Cloth Config 界面的两个入口，移植自上游 {@code compat/cloth/ClothConfigCompat}。
 *
 * <p>上游还有 {@code PartAIIntegration}（单只女仆的 AI 设定）那一半 ——
 * 这里也有，见 {@link PartAIIntegration}。</p>
 *
 * <p>两个方法都会 {@code setGlobalizedExpanded(true)}：让所有分类一开始就展开。
 * 上游这么写是因为它的分类多、玩家一进来就要能看见全部条目；
 * 默认的「折叠」状态会让界面看起来像空的。</p>
 */
public final class ClothConfigCompat {
    private ClothConfigCompat() {
    }

    public static void openConfigScreen() {
        ConfigBuilder configBuilder = MenuIntegration.getConfigBuilder();
        configBuilder.setGlobalizedExpanded(true);
        Minecraft.getInstance().setScreen(configBuilder.build());
    }

    public static void openPartAiSettingScreen(EntityMaid maid) {
        ConfigBuilder configBuilder = PartAIIntegration.getConfigBuilder(maid);
        configBuilder.setGlobalizedExpanded(true);
        Minecraft.getInstance().setScreen(configBuilder.build());
    }

    /**
     * Cloth Config 是否装上了。
     *
     * <p>所有调用 Cloth 类型的地方都必须先过这道判断：没装时 {@code MenuIntegration}
     * 这类引用了 {@code me.shedaniel.clothconfig2.*} 的类<b>不能</b>被加载，
     * 否则会在第一次触碰时抛 {@code NoClassDefFoundError}。</p>
     */
    public static boolean isAvailable() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cloth-config");
    }
}


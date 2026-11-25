package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.client.gui.mod.OptifineScreen;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * 「装了 OptiFine 就弹一页警告」的客户端事件，移植自上游 1.18.2
 * {@code client/event/ShowOptifineScreen}。
 *
 * <p>它只做两件事：判定 OptiFine 在不在、在第一次标题画面时把
 * {@link OptifineScreen} 顶上去。判据由 {@code MiscConfig#CLOSE_OPTIFINE_WARNING}
 * 控制 —— 这就是那项配置的消费者，之前它定义了却<b>没有任何地方读</b>，
 * 于是「关闭 OptiFine 警告」这个开关在界面上点了也毫无效果。</p>
 *
 * <h2>为什么用 {@code ScreenEvents.AFTER_INIT} 而不是 Forge 的 {@code InitScreenEvent.Post}</h2>
 * <p>两者语义相同：界面已经初始化完、{@code init()} 跑完的那一刻。
 * 区别是 Fabric 的这个回调对<b>每一个</b>界面都会触发，所以这里必须显式过滤
 * {@link TitleScreen}，并且用 {@code firstTitleScreenShown} 保证只顶一次 ——
 * 否则玩家每次从存档退回标题画面都会被警告一次。</p>
 *
 * <h2>为什么用 {@code Class.forName} 而不是 {@code FabricLoader#isModLoaded}</h2>
 * <p>OptiFine 本体不是 Fabric 模组，装它靠的是 OptiFabric 这个中间层；
 * 「OptiFabric 装上了」不等于「OptiFine 真的被加载成功」。上游也是直接摸
 * {@code net.optifine.Config} 这个类，这里保持一致。</p>
 */
@Environment(EnvType.CLIENT)
public final class ShowOptifineScreen {
    private static boolean optifinePresent = false;
    private static boolean firstTitleScreenShown = false;

    private ShowOptifineScreen() {
    }

    public static void init() {
        checkOptifineIsLoaded();
        if (!optifinePresent) {
            // 没装 OptiFine 就什么都不注册：少一个每界面都跑一遍的回调
            return;
        }
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (firstTitleScreenShown || !(screen instanceof TitleScreen)) {
                return;
            }
            firstTitleScreenShown = true;
            if (!MiscConfig.CLOSE_OPTIFINE_WARNING.get()) {
                Minecraft.getInstance().setScreen(new OptifineScreen(screen));
            }
        });
    }

    private static void checkOptifineIsLoaded() {
        try {
            Class.forName("net.optifine.Config");
            optifinePresent = true;
        } catch (ClassNotFoundException | LinkageError e) {
            // LinkageError 一起接住：OptiFabric 存在但 OptiFine 版本对不上时，
            // 类能被找到却初始化失败，抛的是 NoClassDefFoundError 而不是 NotFound。
            optifinePresent = false;
        }
    }
}


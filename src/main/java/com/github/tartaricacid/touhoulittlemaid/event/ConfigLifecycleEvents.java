package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.config.ServerConfig;
import net.minecraftforge.api.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/**
 * 「配置加载 / 重载」的时刻，移植自上游两个监听器共用的那个时机。
 */
public final class ConfigLifecycleEvents {
    /**
     * 只提示一次：这个回调在「配置文件首次加载」和「玩家进服同步配置」时都会触发。
     */
    private static boolean warned = false;

    private ConfigLifecycleEvents() {
    }

    public static void init() {
        ModConfigEvent.LOADING.register(ConfigLifecycleEvents::onLoading);
        ModConfigEvent.RELOADING.register(ConfigLifecycleEvents::onReloading);
    }

    private static void onLoading(ModConfig config) {
        handle(config, "load");
    }

    private static void onReloading(ModConfig config) {
        handle(config, "reload");
    }

    private static void handle(ModConfig config, String phase) {
        // 只关心自己的 SERVER 配置：FCAP 是公共 API，别的模组也会注册，
        // 每个事件里都可能混着别人的 ModConfig。
        if (config == null || config.getType() != ModConfig.Type.SERVER) {
            return;
        }
        if (!TouhouLittleMaid.MOD_ID.equals(config.getModId())) {
            return;
        }
        if (warned) {
            return;
        }
        List<String> urls = ServerConfig.CLIENT_PACK_DOWNLOAD_URLS == null
                ? List.of() : ServerConfig.CLIENT_PACK_DOWNLOAD_URLS.get();
        if (!urls.isEmpty()) {
            warned = true;
            TouhouLittleMaid.LOGGER.warn(
                    "ClientPackDownloadUrls is set ({} entr{}, {} phase) but the client pack download "
                            + "has not been ported yet; nothing will be downloaded.",
                    urls.size(), urls.size() == 1 ? "y" : "ies", phase);
        }
    }
}


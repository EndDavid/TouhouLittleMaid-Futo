package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.config.ServerConfig;
import net.minecraftforge.api.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/**
 * 「配置加载 / 重载」的时刻，移植自上游两个监听器共用的那个时机。
 *
 * <h2>为什么这个类必须存在</h2>
 * <p>Forge 侧「配置加载完成」是个<b>事件</b>（{@code ModConfigEvent.Loading}），
 * Forge Config API Port 把它原样搬到了 Fabric —— 只是换成了 Fabric 的事件总线：</p>
 * <pre>
 * ModConfigEvent.LOADING   : Event&lt;Loading&gt;    → void onModConfigLoading(ModConfig)
 * ModConfigEvent.RELOADING : Event&lt;Reloading&gt;  → void onModConfigReloading(ModConfig)
 * </pre>
 * <p>也就是说「Forge 有、Fabric 没有的事件」这句话在<b>配置</b>这一块并不成立 ——
 * FCAP 提供了同名同语义的钩子。本移植版早先没有用它，而是假设
 * 「{@code ConfigValue#get()} 在未加载时会抛异常」并在每个取值点包 try/catch
 * （那个假设是错的，见 {@code MaidConfig} 文件末尾）。</p>
 *
 * <h2>现在用它做什么</h2>
 * <p>上游在这里启动两件事：</p>
 * <ol>
 *     <li>{@code MaidMealRegConfigEvent}：把「正则餐食黑名单」展开成物品 id 表。
 *         <b>本移植版不需要</b> —— {@code MaidMealManager#isRegexBlockList} 改成在查询时
 *         直接匹配，效果等价且省掉一次「遍历全部注册物品」。</li>
 *     <li>{@code ClientPackDownloadEvent}：按 {@code ClientPackDownloadUrls} 下载客户端资源包。
 *         <b>尚未移植</b>（{@code ClientPackDownloadManager} 涉及 HTTP、解压、进度界面，
 *         与声音包同属「等全部移植完再补」的那一类）。</li>
 * </ol>
 *
 * <p>所以这里做的是<b>把「未移植」这件事说出来</b>：玩家在 {@code ServerConfig} 里填了 url
 * 却什么也没发生时，日志里至少有一行字告诉他为什么。沉默是这里最糟的选择 ——
 * 上游那份配置项在本移植版里同样存在（存档/配置可互换），一个「填了没反应」的配置项
 * 比没有这个配置项更难排查。</p>
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


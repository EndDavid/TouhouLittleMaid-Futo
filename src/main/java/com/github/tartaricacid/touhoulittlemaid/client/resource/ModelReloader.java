package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.ai.IconTextureSizes;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleScripts;

/**
 * 客户端「模型 + 贴图缓存」整套重载，把原来写在
 * {@code TouhouLittleMaidFutoClient} 里那个资源重载监听器的方法体抽出来。
 *
 * <h2>为什么抽出来</h2>
 * <p>同一个动作现在有<b>两个</b>触发源：</p>
 * <ol>
 *     <li>原版的资源重载（F3+T / 进世界 / 换资源包）——
 *         {@code ResourceManagerHelper} 的监听器；</li>
 *     <li>在线下载 / 删除 / 更新模型包之后 —— {@link CustomPackLoader#reloadPacks()}。</li>
 * </ol>
 * <p>后者<b>不能</b>只重新读一遍 {@code tlm_custom_pack}：{@code GeckoLibCache} 里
 * 还留着上一轮的几何与动画，{@code ConditionManager} 里还留着旧模型的动画条件，
 * 不整体清空会表现为「换包之后仍是旧模型」或者「新模型站着不动」。
 * 所以两条路走同一份代码，而不是各写一半。</p>
 */
public final class ModelReloader {
    private ModelReloader() {
    }

    /**
     * 清空并重建全部客户端模型缓存。
     *
     * <p>顺序是硬约束，不要调换：</p>
     * <ol>
     *     <li>{@link GeckoModelLoader#reload()} —— 内部先 {@code GeckoLibCache.clearAll()}，
     *         再加载内置女仆包，最后加载 {@code tlm_custom_pack} 里的外部包；</li>
     *     <li>{@link ChairModelLoader#reload()} —— <b>必须在其后</b>，
     *         否则刚建好的坐垫几何会被上一步的 {@code clearAll()} 清掉；</li>
     *     <li>气泡台本与图标尺寸 —— 都是按贴图缓存过的，换包后要丢；</li>
     *     <li>方块 / 实体基岩模型 —— 同样按资源路径缓存，换资源包后要丢。</li>
     * </ol>
     */
    public static void reloadAll() {
        // 先把 tlm_custom_pack 扫一遍：下面 GeckoModelLoader / ChairModelLoader
        // 各自要用「外部包列表」，扫一次比两边各扫一次省一半磁盘遍历。
        CustomPackLoader.scan();
        GeckoModelLoader.reload();
        // 气泡台本与图标尺寸都是按贴图缓存过的：换资源包 / 改模型后必须丢一次，
        // 否则台本里的旧贴图路径与旧的帧数会一直生效。
        ChatBubbleScripts.clear();
        IconTextureSizes.clear();
        // 方块基岩模型同样要丢缓存，否则资源包换了模型还在用旧的
        BedrockBlockModels.clear();
        // 实体基岩模型（岩浆怪的油库里）同理：几何与贴图是一起换的
        BedrockEntityModels.clear();

        // 预热两个方块模型：首次渲染时再加载会卡一帧，而且模型写错
        // 只会在「方块看不见」这种很难定位的现象里暴露，这里提前解析一遍，
        // 失败会打 error 日志（BedrockBlockModels#load 里）。
        BedrockBlockModels.get("altar");
        BedrockBlockModels.get("shrine");
        // 三张棋盘也一起预热：它们的贴图与几何比祭坛还大，
        // 而且「看不见棋子」这种症状最怕拖到运行时才暴露。
        BedrockBlockModels.get("cchess");
        BedrockBlockModels.get("cchess_pieces");
        BedrockBlockModels.get("gomoku");
        BedrockBlockModels.get("gomoku_piece");
        BedrockBlockModels.get("wchess");
        BedrockBlockModels.get("wchess_pieces");
        // 坐垫模型必须【在女仆模型之后】加载：GeckoLibCache 在上面那行被整体清空过
        ChairModelLoader.reload();
        // 岩浆怪的油库里模型也预热：JSON 写错只会在「岩浆怪看不见」这种
        // 很难定位的现象里暴露，这里提前解析一遍，失败会打 error 日志。
        BedrockEntityModels.get("marisa_yukkuri");
    }
}

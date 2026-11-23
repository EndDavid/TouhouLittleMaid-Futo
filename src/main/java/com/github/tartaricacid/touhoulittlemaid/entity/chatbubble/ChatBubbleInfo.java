package com.github.tartaricacid.touhoulittlemaid.entity.chatbubble;

import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

/**
 * 一份聊天气泡台本，移植自上游 {@code client/resource/pojo/ChatBubbleInfo}。
 *
 * <p>数据来源有两个：</p>
 * <ol>
 *     <li>模组自带的 {@code assets/touhou_little_maid/tlm_custom_pack/default_chat_bubble.json}
 *         —— 所有女仆的默认台词；</li>
 *     <li>模型包 {@code maid_model.json} 里某个模型的 {@code chat_bubble} 字段
 *         —— 该模型专属的台词，按 {@link #decorate()} 与默认合并。</li>
 * </ol>
 *
 * <h2>三段结构（与上游同名同义）</h2>
 * <ul>
 *     <li>{@code main}：按作息挑 —— {@code idle} / {@code sleep} / {@code work}，
 *         工作状态下还会按任务 id 再挑一次（{@code fishing}、{@code farmer} …）；</li>
 *     <li>{@code special}：按环境挑 —— {@code morning} / {@code night} / {@code rain} /
 *         {@code snow} / {@code cold} / {@code hot} / {@code hurt} / {@code beg}；</li>
 *     <li>{@code other}：饰品自定义 —— 键由 {@code IMaidBauble#getChatBubbleId()} 给出
 *         （例如静音饰品是 {@code mute}）。</li>
 * </ul>
 *
 * <p>每一条台词既可以是纯文本 / {@code {翻译键}}，也可以是
 * {@code %命名空间:贴图路径%} 形式的图标（解析见 {@link ChatText.Serializer}），
 * 所以台本里会混着表情图片与颜文字。</p>
 *
 * <p><b>为什么这个 pojo 放在 {@code entity} 包而不是上游的 {@code client.resource.pojo}</b>：
 * 服务端也要读它（随机气泡是服务端算的、再经同步数据发给客户端），
 * 放在客户端包会让专用服务端加载时多一次无谓的类依赖。</p>
 */
public class ChatBubbleInfo {
    @SerializedName("bg")
    @Nullable
    private ResourceLocation bg = null;

    @SerializedName("text")
    @Nullable
    private Text text = null;

    @Nullable
    public ResourceLocation getBg() {
        return bg;
    }

    @Nullable
    public Text getText() {
        return text;
    }

    /**
     * 与默认台本合并。
     *
     * <p>{@code replace = true} 时整段替换（模型作者想完全自定义时用）；
     * 否则<b>按 key 追加</b> —— 模型自己写了 {@code idle} 就把自己的几句加在默认那几句后面，
     * 没写的 key 沿用默认。与上游 {@code ChatBubbleInfo#decorate()} 逐行对应。</p>
     */
    public ChatBubbleInfo decorate(ChatBubbleInfo defaultInfo) {
        if (bg == null) {
            bg = defaultInfo.bg;
        }
        if (text == null) {
            text = defaultInfo.text;
        } else if (!text.isReplace()) {
            if (text.main.isEmpty()) {
                text.main = defaultInfo.getOrEmptyMain();
            } else {
                mergeChatText(text.main, defaultInfo.getOrEmptyMain());
            }
            if (text.special.isEmpty()) {
                text.special = defaultInfo.getOrEmptySpecial();
            } else {
                mergeChatText(text.special, defaultInfo.getOrEmptySpecial());
            }
            if (text.other.isEmpty()) {
                text.other = defaultInfo.getOrEmptyOther();
            } else {
                mergeChatText(text.other, defaultInfo.getOrEmptyOther());
            }
        }
        return this;
    }

    private static void mergeChatText(HashMap<String, List<ChatText>> to, HashMap<String, List<ChatText>> from) {
        for (String key : from.keySet()) {
            List<ChatText> chatTexts = from.get(key);
            if (chatTexts == null || chatTexts.isEmpty()) {
                continue;
            }
            List<ChatText> existing = to.get(key);
            if (existing == null) {
                // 拷贝一份：默认台本是全局单例，直接塞引用会让后续 merge 反向污染它
                to.put(key, com.google.common.collect.Lists.newArrayList(chatTexts));
            } else {
                existing.addAll(chatTexts);
            }
        }
    }

    private HashMap<String, List<ChatText>> getOrEmptyMain() {
        return text == null || text.main == null ? Maps.newHashMap() : text.main;
    }

    private HashMap<String, List<ChatText>> getOrEmptySpecial() {
        return text == null || text.special == null ? Maps.newHashMap() : text.special;
    }

    private HashMap<String, List<ChatText>> getOrEmptyOther() {
        return text == null || text.other == null ? Maps.newHashMap() : text.other;
    }

    /**
     * 三段台词表本体。
     */
    public static class Text {
        /**
         * {@code true} = 完全替换默认台本，不与默认合并。
         */
        @SerializedName("replace")
        private boolean replace = false;

        @SerializedName("main")
        private HashMap<String, List<ChatText>> main = Maps.newHashMap();

        @SerializedName("special")
        private HashMap<String, List<ChatText>> special = Maps.newHashMap();

        @SerializedName("other")
        private HashMap<String, List<ChatText>> other = Maps.newHashMap();

        public boolean isReplace() {
            return replace;
        }

        public HashMap<String, List<ChatText>> getMain() {
            return main;
        }

        public HashMap<String, List<ChatText>> getSpecial() {
            return special;
        }

        public HashMap<String, List<ChatText>> getOther() {
            return other;
        }
    }
}


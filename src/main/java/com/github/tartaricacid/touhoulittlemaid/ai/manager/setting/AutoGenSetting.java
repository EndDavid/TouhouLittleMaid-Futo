package com.github.tartaricacid.touhoulittlemaid.ai.manager.setting;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.Service;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.ResponseFormat;
import org.apache.commons.lang3.StringUtils;

/**
 * 「角色设定为空时让模型自己写一份」的 prompt 构造器，对应 1.20.1 TLMO 的
 * {@code MaidAIChatManager#autoGenSetting} + {@code AutoGenSettingCallback}。
 *
 * <p><b>为什么 1.18.2 上游没有这个功能：</b>1.18.2 的 {@code AIConfig} 只有九项，
 * 没有 {@code AutoGenSettingEnabled}；自动生成人设是后来才加的。本移植版按「行为贴近
 * TLMO 1.20.1」的要求把它补上，配置项同名同默认值（默认开启）。</p>
 *
 * <h2>和普通聊天共用同一套契约</h2>
 * <p>上游在这里也要求 {@code response_format: json_object}，然后从
 * {@code choices[0].message.content} 里取 {@code chat_text} 当 YAML 正文 ——
 * 也就是说「生成人设」只是一次特殊 prompt 的聊天请求，不需要第二套解析逻辑。</p>
 */
public final class AutoGenSetting {
    /**
     * 生成人设时的输出上限。上游没有显式设置（沿用 256 的默认值），
     * 但那会把 JSON 截断，所以这里放宽；温度不变。
     */
    private static final int AUTO_GEN_MAX_TOKENS = 1024;
    private AutoGenSetting() {
    }

    /**
     * 构造生成人设的请求。
     *
     * @param manager  这只女仆的聊天管理器（提供模型名、温度与自定义设定）
     * @param language 玩家当前语言的显示名，要求模型用这种语言写人设
     */
    public static ChatCompletion build(MaidAIChatManager manager, String language) {
        String modelId = StringUtils.defaultString(manager.getMaid().getModelId());
        String modelName = modelId.contains(":")
                ? modelId.substring(modelId.indexOf(':') + 1) : modelId;
        String name = manager.getMaid().getName().getString();
        String description = manager.getCustomSetting();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a character profile for a Minecraft maid companion based on the given name. Include:\n")
                .append("- Character setting and role\n")
                .append("- Personality traits\n")
                .append("- Language style and speech patterns\n\n")
                .append("Name: ").append(name).append('\n')
                .append("Model: ").append(modelName).append('\n')
                .append("Chat language: ").append(language).append('\n');
        if (StringUtils.isNotBlank(description)) {
            prompt.append("Character Description Section: ").append(description).append('\n');
        }
        prompt.append("\nReturn exactly one JSON object: ")
                .append("{\"chat_text\": \"<setting body>\", \"tts_text\": \"<same text>\"}.\n")
                .append("chat_text must be the plain setting body only: several lines describing who she is, ")
                .append("her personality and how she speaks. Write it in the chat language above. ")
                .append("Do not wrap it in a code fence and do not add meta / model_id keys.\n")
                // 生成出来的设定会被当作后续对话的 system prompt，所以要求它自带
                // 「回复必须是 JSON」这一句 —— 见 Service#getChatCompletion 里关于
                // DeepSeek 强制的 json 关键字的说明。
                .append("End the body with one short line stating that every reply must be a JSON object ")
                .append("containing the chat_text and tts_text fields.\n");

        return ChatCompletion.create()
                .model(manager.getChatModel())
                .temperature(manager.getChatTemperature())
                .setResponseFormat(ResponseFormat.json())
                // ChatCompletion#maxTokens 的默认值是 256 —— 那是给「一句话回复」用的。
                // 生成人设要几百字，256 个 token 会把 JSON 从中间切断，
                // 表现就是 settings/<model>.yml 里存进半句话。
                .maxTokens(AUTO_GEN_MAX_TOKENS)
                .userChat(prompt.toString());
    }

    /**
     * 从自动生成的响应里取出「角色设定正文」。
     *
     * <p>上游 1.20.1 的 {@code AutoGenSettingCallback#onSuccess(ResponseChat)} 直接取
     * {@link ResponseChat#getChatText()} —— 也就是说请求里的 {@code chat_text} 才是正文，
     * 整个 JSON 只是传输壳。本移植版原先漏了这一步，把整段 JSON 当成 YAML 正文写进了
     * {@code settings/<model>.yml}，后果有两个：</p>
     * <ul>
     *     <li>设定正文是 `{"chat_text":"…` 这种残片，模型读不懂自己在演谁；</li>
     *     <li>那段正文里没有 {@code json} 字样，DeepSeek 会拒绝带
     *         {@code response_format: json_object} 的后续请求（HTTP 400）。</li>
     * </ul>
     *
     * <p>解析失败时退回「去掉代码围栏的原文」：宁可写进去一份奇怪但完整的设定，
     * 也不要因为一次 JSON 解析失败就让玩家永远没有设定。</p>
     */
    public static String extractSettingBody(String rawMessage) {
        // 归一换行：模型有时会返回 CRLF，而
        // SettingReader#saveGeneratedSetting 只按 '\n' 切行。
        String raw = StringUtils.defaultString(rawMessage).replace("\r\n", "\n").replace('\r', '\n').trim();
        if (raw.isEmpty()) {
            return StringUtils.EMPTY;
        }
        try {
            ResponseChat responseChat = Service.GSON.fromJson(raw, ResponseChat.class);
            if (responseChat != null) {
                if (StringUtils.isNotBlank(responseChat.getChatText())) {
                    return responseChat.getChatText().trim();
                }
                if (StringUtils.isNotBlank(responseChat.getTtsText())) {
                    return responseChat.getTtsText().trim();
                }
            }
        } catch (Exception ignored) {
            // 不是合法 JSON：继续往下兜底
        }
        // 输出被 max_tokens 截断时 JSON 是不完整的，GSON 会失败；
        // 这里手工找 chat_text 的值，能救多少算多少。
        String partial = extractJsonString(raw, "chat_text");
        if (StringUtils.isNotBlank(partial)) {
            return partial;
        }
        return stripCodeFence(raw);
    }
    /**
     * 从（可能被截断的）JSON 文本里手工取出某个字符串字段的值。
     *
     * <p>只处理两种转义：{@code \\n} 与 {@code \\"} 。够用即可 ——
     * 它的定位是「完整 JSON 解析失败时的兜底」，不是通用 JSON 解析器。</p>
     *
     * @return 取不到时返回 {@link StringUtils#EMPTY}
     */
    private static String extractJsonString(String raw, String key) {
        String quotedKey = "\"" + key + "\"";
        int keyIndex = raw.indexOf(quotedKey);
        if (keyIndex < 0) {
            return StringUtils.EMPTY;
        }
        int colon = raw.indexOf(':', keyIndex + quotedKey.length());
        if (colon < 0) {
            return StringUtils.EMPTY;
        }
        int start = raw.indexOf('"', colon + 1);
        if (start < 0) {
            return StringUtils.EMPTY;
        }
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                builder.append(c == 'n' ? '\n' : c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                builder.append(c);
            }
        }
        return builder.toString().trim();
    }
    /**
     * 去掉模型偶尔仍然会加上的 Markdown 代码围栏。
     *
     * <p>上游 {@code AutoGenSettingCallback} 里也有一步 {@code replaceAll}，作用相同：
     * 即使提示里明确说了「不要围栏」，一部分模型还是会把 YAML 包进 ```yaml … ```。</p>
     */
    public static String stripCodeFence(String raw) {
        String text = StringUtils.defaultString(raw).replace("\r\n", "\n").trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstBreak = text.indexOf('\n');
        if (firstBreak < 0) {
            return StringUtils.EMPTY;
        }
        text = text.substring(firstBreak + 1);
        int closing = text.lastIndexOf("```");
        if (closing >= 0) {
            text = text.substring(0, closing);
        }
        return text.trim();
    }
}
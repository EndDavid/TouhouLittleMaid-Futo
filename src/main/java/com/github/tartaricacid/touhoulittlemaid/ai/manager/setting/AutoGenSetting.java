package com.github.tartaricacid.touhoulittlemaid.ai.manager.setting;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
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
                .append("Do not wrap it in a code fence and do not add meta / model_id keys.\n");

        return ChatCompletion.create()
                .model(manager.getChatModel())
                .temperature(manager.getChatTemperature())
                .setResponseFormat(ResponseFormat.json())
                .userChat(prompt.toString());
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
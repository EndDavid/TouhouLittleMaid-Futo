package com.github.tartaricacid.touhoulittlemaid.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 翻译键解析，对应上游 {@code util/ParseI18n}。
 *
 * <p>TLM 的模型包 JSON 里，凡是「可能被翻译」的字段（包名、模型名、描述）都允许两种写法：
 * 纯文本，或者 {@code {some.translation.key}}。带花括号的按翻译键处理，
 * 其余原样显示。上游的做法就是这样，不能用 {@code I18n.get} 一刀切 ——
 * 那样未翻译的纯文本会被当成键名丢掉。</p>
 */
public final class ParseI18n {
    private ParseI18n() {
    }

    /**
     * 把 JSON 里取到的一个字符串转成 {@link Component}。
     */
    public static MutableComponent parse(String text) {
        if (text == null || text.isEmpty()) {
            return new TextComponent("");
        }
        // 形如 {ns.path} 才当翻译键；注意长度至少 3（"{}"）
        if (text.length() > 2 && text.charAt(0) == '{' && text.charAt(text.length() - 1) == '}') {
            return new TranslatableComponent(text.substring(1, text.length() - 1));
        }
        return new TextComponent(text);
    }

    /**
     * 批量版本，适用于「描述」这种字符串列表。
     */
    public static List<Component> parse(List<String> texts) {
        List<Component> list = new ArrayList<>();
        if (texts == null) {
            return list;
        }
        for (String text : texts) {
            list.add(parse(text));
        }
        return list;
    }

    /**
     * 取出翻译键本身（去掉花括号）；不是翻译键时返回 {@code null}。
     *
     * <p>用于「需要 key 而不是 Component」的场合，例如补全 {@code model.*.name}。</p>
     */
    public static String getKey(String text) {
        if (text == null || text.length() <= 2) {
            return null;
        }
        if (text.charAt(0) == '{' && text.charAt(text.length() - 1) == '}') {
            return text.substring(1, text.length() - 1);
        }
        return null;
    }
}


package com.github.tartaricacid.touhoulittlemaid.entity.chatbubble;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;

/**
 * 一条气泡内容，移植自上游 {@code entity/chatbubble/ChatText}。
 *
 * <p>它必须能被序列化：{@link MaidChatBubbles} 是同步数据，每次写入都要走
 * {@link #toBuff} / {@link #fromBuff}。所以这里刻意做成不可变值对象，
 * 并且 {@code equals} 逐字段比较 —— 同步数据的脏检查靠的就是它。</p>
 *
 * <p>{@link Serializer} 解析模型包 {@code default_chat_bubble.json} 里那种
 * 「{@code %命名空间:贴图路径%} 表示贴图，其余是文本」的写法。本轮 AI 聊天只用
 * {@link ChatTextType#TEXT}，但反序列化器一并移植：它很短，且将来气泡子系统
 * 读模型包时不用再改一遍数据结构。</p>
 */
public final class ChatText {
    public static final ResourceLocation EMPTY_ICON_PATH = new ResourceLocation(TouhouLittleMaid.MOD_ID, "empty");
    public static final ChatText EMPTY_CHAT_TEXT = new ChatText(ChatTextType.EMPTY, EMPTY_ICON_PATH, StringUtils.EMPTY);
    private static final String ICON_IDENTIFIER_CHAR = "%";

    private final ChatTextType type;
    private final ResourceLocation iconPath;
    private final String text;

    public ChatText(ChatTextType type, ResourceLocation iconPath, String text) {
        this.type = type;
        this.iconPath = iconPath;
        this.text = text;
    }

    public static void toBuff(ChatText chatText, FriendlyByteBuf buf) {
        buf.writeEnum(chatText.type);
        buf.writeResourceLocation(chatText.iconPath);
        buf.writeUtf(chatText.text);
    }

    public static ChatText fromBuff(FriendlyByteBuf buf) {
        ChatTextType type = buf.readEnum(ChatTextType.class);
        ResourceLocation iconPath = buf.readResourceLocation();
        String text = buf.readUtf();
        return new ChatText(type, iconPath, text);
    }

    public boolean isText() {
        return type == ChatTextType.TEXT;
    }

    public boolean isIcon() {
        return type == ChatTextType.ICON;
    }

    public ResourceLocation getIconPath() {
        return iconPath;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ChatText chatText)) {
            return false;
        } else {
            return type.equals(chatText.type) && iconPath.equals(chatText.iconPath) && text.equals(chatText.text);
        }
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, iconPath, text);
    }

    /**
     * 把 {@code chat_text} 字段解析成 {@link ChatText}。
     */
    public static class Serializer implements JsonDeserializer<ChatText> {
        @Override
        public ChatText deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            String text = GsonHelper.convertToString(json, "chat_text");
            if (StringUtils.isEmpty(text)) {
                return EMPTY_CHAT_TEXT;
            }

            if (text.startsWith(ICON_IDENTIFIER_CHAR) && text.endsWith(ICON_IDENTIFIER_CHAR)) {
                String substring = text.substring(1, text.length() - 1);
                if (ResourceLocation.isValidResourceLocation(substring)) {
                    return new ChatText(ChatTextType.ICON, new ResourceLocation(substring), StringUtils.EMPTY);
                }
                return EMPTY_CHAT_TEXT;
            }

            return new ChatText(ChatTextType.TEXT, EMPTY_ICON_PATH, text);
        }
    }
}


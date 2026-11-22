package com.github.tartaricacid.touhoulittlemaid.entity.chatbubble;

/**
 * 气泡内容类型，移植自上游 {@code entity/chatbubble/ChatTextType}。
 *
 * <p>本移植版只用到 {@link #TEXT}：AI 聊天回复是纯文本。
 * {@link #ICON} 留给「聊天气泡子系统」补完（上游用它显示模型包里的颜文字贴图），
 * 但枚举值必须原样保留 —— 它是同步数据的一部分，少一个值新旧存档就对不上。</p>
 */
public enum ChatTextType {
    // 图标
    ICON,
    // 普通文本
    TEXT,
    // 空
    EMPTY
}


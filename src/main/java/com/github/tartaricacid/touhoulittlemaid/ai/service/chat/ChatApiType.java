package com.github.tartaricacid.touhoulittlemaid.ai.service.chat;
/**
 * 聊天 API 类型，移植自上游 {@code ai/service/chat/ChatApiType}。
 *
 * <p>目前只有 OpenAI 兼容协议一种；{@code available_sites.yml} 里所有聊天站点
 * （openai / deepseek / aliyun / player2 …）都填 {@code api_type: openai}，
 * 所以一个实现就够。</p>
 */
public enum ChatApiType {
    OPENAI("openai");
    private final String name;
    ChatApiType(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}

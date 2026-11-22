package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request;
/**
 * 对话角色，移植自上游 {@code ai/service/chat/openai/request/Role}。
 *
 * <p>{@code TOOL} / {@code FUNCTION} 目前用不到，保留是为了让 {@code HistoryChat}
 * 的 role 取值与上游一一对应（将来接工具调用时不用再改 DTO）。</p>
 */
public enum Role {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
    FUNCTION("function");
    private final String id;
    Role(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }
}

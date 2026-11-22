package com.github.tartaricacid.touhoulittlemaid.ai.manager.entity;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.Role;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
/**
 * 一条历史对话，移植自上游 {@code ai/manager/entity/HistoryChat}。
 *
 * <p>{@code gameTime} 记录说话时世界的游戏刻 —— 目前只用于将来做「太久以前的话
 * 就不要塞进 prompt」这类清理，上游也没用它做别的。用 record 是因为它就是个不可变三元组。</p>
 */
public record HistoryChat(Role role, String message, long gameTime) {
    public static HistoryChat userChat(EntityMaid maid, String message) {
        long time = maid.level.getGameTime();
        return new HistoryChat(Role.USER, message, time);
    }
    public static HistoryChat assistantChat(EntityMaid maid, String message) {
        long time = maid.level.getGameTime();
        return new HistoryChat(Role.ASSISTANT, message, time);
    }
}

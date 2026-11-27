package com.github.tartaricacid.touhoulittlemaid.entity.ai.edible;

/**
 * 女仆面对「方块类食物」时打算干什么，移植自上游同名枚举。
 *
 * <p>存进 {@code InitEntities.MAID_EDIBLE_BLOCK_ACTION} 这个记忆模块，
 * 由 {@code MaidStealEdibleMoveBlockTask} 写入、{@code MaidStealEdibleUseTask} 读取 ——
 * 两个任务必须是同一个值，否则「找目标」和「动手」会各干各的。</p>
 */
public enum MaidEdibleBlockAction {
    /**
     * 走到世界里的方块食物旁边偷吃。
     */
    TRY_STEAL,
    /**
     * 把背包里的食物摆到零食架上再吃。
     */
    TRY_PLACE
}


package com.github.tartaricacid.touhoulittlemaid.api.task.meal;

/**
 * 女仆的用餐类型（上游 {@code api/task/meal/MaidMealType}）。
 *
 * <p>三类餐的触发条件、收益与冷却各不相同，由各自的行为负责判断：</p>
 *
 * <table border="1">
 *     <caption>三类餐的对应关系</caption>
 *     <tr><th>类型</th><th>触发行为</th><th>触发场景</th></tr>
 *     <tr><td>{@link #WORK_MEAL}</td><td>{@code MaidWorkMealTask}</td>
 *         <td>工作时间，手上有 / 背包里有能吃的东西</td></tr>
 *     <tr><td>{@link #HEAL_MEAL}</td><td>{@code MaidHealSelfTask}</td>
 *         <td>缺血 ≥ 2 点时自己找东西吃</td></tr>
 *     <tr><td>{@link #HOME_MEAL}</td><td>{@code MaidHomeMealTask}</td>
 *         <td>坐在野餐垫上吃</td></tr>
 * </table>
 *
 * <p>三类餐都已实现：{@code WORK_MEAL} / {@code HEAL_MEAL} 在 {@code MaidMealManager#init}
 * 里注册，{@code HOME_MEAL} 随野餐垫一起落地（{@code DefaultMaidHomeMeal}）。</p>
*/
public enum MaidMealType {
    /**
     * 工作餐
     */
    WORK_MEAL,
    /**
     * 家庭餐
     */
    HOME_MEAL,
    /**
     * 回血餐
     */
    HEAL_MEAL
}


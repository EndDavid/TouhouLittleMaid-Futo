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
 * <p><b>本移植版只实现了前两类。</b>{@code HOME_MEAL} 依赖野餐垫方块
 * （{@code BlockPicnicMat} + {@code TileEntityPicnicMat} + 对应的 POI 与
 * {@code EntitySit} 娱乐坐骑），那块子系统还没搬，所以枚举值保留、
 * 但没有对应的行为去触发它。</p>
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


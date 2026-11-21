package com.github.tartaricacid.touhoulittlemaid.entity.task.meal;

import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 女仆用餐注册表（上游 {@code entity/task/meal/MaidMealManager}）。
 *
 * <p>与上游的差异：</p>
 * <ul>
 *     <li>上游在 {@code init()} 里还会遍历 {@code TouhouLittleMaid.EXTENSIONS} 让附属包
 *         追加餐类型；Fabric 侧没有那套扩展点，{@link #addMaidMeal} 保留下来供以后使用。</li>
 *     <li>{@code HOME_MEAL} 现在也注册了：野餐垫移植完成后由 {@code DefaultMaidHomeMeal}
 *         负责，见 {@link DefaultMaidHomeMeal}。</li>
 *     <li>上游在 {@code ModConfigEvent.Loading} 里把所有「正则黑名单」展开成具体的物品 id 表；
 *         这里改成<b>按需匹配</b>（见 {@link #isRegexBlockList}），结果等价，
 *         而且省掉一次「遍历全部注册物品」的开销。</li>
 * </ul>
 */
public final class MaidMealManager {
    private static final Map<MaidMealType, List<IMaidMeal>> ALL_MEAL_TYPES = Maps.newEnumMap(MaidMealType.class);

    /**
     * 已编译的正则缓存：key 是正则原文，value 是编译结果。
     *
     * <p>配置里的条目是玩家手写的正则，编译一次就够；用 {@code ConcurrentHashMap}
     * 是因为客户端 / 服务端线程都可能读到（女仆 AI 在服务端跑，但配置同步会写）。</p>
     */
    private static final Map<String, Pattern> PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private MaidMealManager() {
    }

    public static void init() {
        for (MaidMealType type : MaidMealType.values()) {
            ALL_MEAL_TYPES.put(type, Lists.newArrayList());
        }
        addMaidMeal(MaidMealType.WORK_MEAL, new DefaultMaidWorkMeal());
        addMaidMeal(MaidMealType.HEAL_MEAL, new DefaultMaidHealSelfMeal());
        addMaidMeal(MaidMealType.HOME_MEAL, new DefaultMaidHomeMeal());
    }

    public static void addMaidMeal(MaidMealType type, IMaidMeal maidMeal) {
        ALL_MEAL_TYPES.computeIfAbsent(type, k -> Lists.newArrayList()).add(maidMeal);
    }

    public static List<IMaidMeal> getMaidMeals(MaidMealType type) {
        List<IMaidMeal> maidMeals = ALL_MEAL_TYPES.get(type);
        if (maidMeals == null) {
            return Collections.emptyList();
        }
        return maidMeals;
    }

    /**
     * 物品是否命中「正则黑名单」（对应上游 {@code MaidMealRegConfigEvent} 展开后的结果表）。
     *
     * <p>上游的做法是把每个正则去匹配<b>全部</b>注册物品、把命中项展开成一张 id 表，
     * 之后查询就是一次 {@code List#contains}。这里改成直接拿当前物品的注册名去匹配正则 ——
     * 配置条目本来就只有个位数，等价且更省。</p>
     *
     * <p>正则写错（编译失败）时<b>跳过该条</b>而不是抛异常：配置是玩家手改的，
     * 一条写坏不该让女仆彻底不吃饭。</p>
     */
    public static boolean isRegexBlockList(ItemStack stack, List<String> regexList) {
        if (regexList == null || regexList.isEmpty()) {
            return false;
        }
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        if (key == null) {
            return true;
        }
        String id = key.toString();
        for (String regex : regexList) {
            if (regex == null || regex.isEmpty()) {
                continue;
            }
            Pattern pattern = PATTERN_CACHE.computeIfAbsent(regex, r -> {
                try {
                    return Pattern.compile(r);
                } catch (Exception e) {
                    return null;
                }
            });
            if (pattern != null && pattern.matcher(id).matches()) {
                return true;
            }
        }
        return false;
    }
}

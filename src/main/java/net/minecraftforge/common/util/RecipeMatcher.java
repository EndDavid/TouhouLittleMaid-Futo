package net.minecraftforge.common.util;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Futo 兼容层：等价 Forge {@code net.minecraftforge.common.util.RecipeMatcher}。
 *
 * <p>Forge 用它判断「一堆无序材料能否满足一组无序原料」。祭坛合成是 6 个贡品位、
 * 合成表里最多写 6 条原料，两边都无序 —— 这正是「二分图完美匹配」问题。</p>
 *
 * <h2>语义（照 Forge 的行为）</h2>
 * <ul>
 *     <li>原料侧的 {@link Ingredient#EMPTY} 是<b>补位</b>，不构成要求。
 *         上游 {@code AltarRecipe} 会把材料数组补齐到 6 条，缺的那几条就是 {@code EMPTY}，
 *         所以这里必须跳过它们，否则「只写 3 条原料的配方」永远匹配不上。</li>
 *     <li>材料侧的空 {@link ItemStack} 不参与匹配（空槽不能顶替任何原料）。</li>
 *     <li>每条原料都必须被一个<b>不同</b>的材料满足；多余的材料忽略。</li>
 * </ul>
 *
 * <p>算法用 Kuhn 增广路（匈牙利算法）。规模只有 6×6，复杂度完全不值一提，
 * 而且它比贪心正确 —— 贪心会在「原料 A 能接受 x/y、原料 B 只能接受 x」时算错。</p>
 */
public final class RecipeMatcher {
    private RecipeMatcher() {
    }

    /**
     * @param inputs      材料（可含空栈）
     * @param ingredients 原料（可含 {@link Ingredient#EMPTY} 补位）
     * @return 长度为 {@code inputs.size()} 的数组，下标为材料序号、值为它满足的原料序号；
     * 不匹配时返回 {@code null}。语义与 Forge 一致（只用来判「非 null」）。
     */
    @Nullable
    public static int[] findMatches(List<ItemStack> inputs, NonNullList<Ingredient> ingredients) {
        // 1) 收集真正构成要求的原料
        List<Integer> required = new ArrayList<>(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient == Ingredient.EMPTY || ingredient.getItems().length == 0) {
                continue;
            }
            required.add(i);
        }
        if (required.isEmpty()) {
            // 没有任何原料要求：上游不会走到这里（合成表至少一条），但保持行为确定
            return new int[inputs.size()];
        }

        // 2) 建二分图：原料 k（压缩后下标）→ 可接受的材料的原始下标
        int n = required.size();
        List<List<Integer>> edges = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            Ingredient ingredient = ingredients.get(required.get(k));
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                ItemStack stack = inputs.get(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    candidates.add(i);
                }
            }
            if (candidates.isEmpty()) {
                // 这条原料没有任何材料能顶替，直接失败，省掉后面的搜索
                return null;
            }
            edges.add(candidates);
        }

        // 3) Kuhn 增广：为每条原料找一条互不冲突的边
        int[] inputOwner = new int[inputs.size()];
        Arrays.fill(inputOwner, -1);
        for (int k = 0; k < n; k++) {
            boolean[] visited = new boolean[inputs.size()];
            if (!augment(k, edges, inputOwner, visited)) {
                return null;
            }
        }

        // 4) 反过来输出：材料 → 原料
        int[] result = new int[inputs.size()];
        Arrays.fill(result, -1);
        for (int i = 0; i < inputOwner.length; i++) {
            if (inputOwner[i] >= 0) {
                result[i] = required.get(inputOwner[i]);
            }
        }
        return result;
    }

    private static boolean augment(int k, List<List<Integer>> edges, int[] inputOwner, boolean[] visited) {
        for (int input : edges.get(k)) {
            if (visited[input]) {
                continue;
            }
            visited[input] = true;
            // 该材料还没被占用，或者占用它的原料能挪到别处
            if (inputOwner[input] < 0 || augment(inputOwner[input], edges, inputOwner, visited)) {
                inputOwner[input] = k;
                return true;
            }
        }
        return false;
    }
}


package com.github.tartaricacid.touhoulittlemaid.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

/**
 * 「女仆手上哪只是鱼竿」的统一判定。
 *
 * <h2>为什么需要它</h2>
 * <p>上游 1.20.1 全部只看<b>主手</b>（{@code getMainHandItem()}）。但把鱼竿放副手是玩家的
 * 自然习惯 —— 尤其在主手要留武器 / 工具的时候。只看主手的后果是：女仆坐在船里、
 * 手里明明有鱼竿，却完全不动，而且**没有任何提示**（{@code TaskFishing} 的
 * {@code has_fishing_rod} 条件只影响界面上的说明文字，不会弹东西）。</p>
 *
 * <p>这个坑很隐蔽：本移植版最初排查「她不钓鱼」时就是被它误导了好几个来回。</p>
 *
 * <h2>本移植版的取舍</h2>
 * <p>主手优先、副手兜底。主手有鱼竿时行为与上游<b>逐字一致</b>，因此不影响任何既有存档 / 玩法；
 * 只有当主手不是鱼竿、而副手是时才启用副手 —— 这是纯粹的放宽，不会让任何原本可用的组合失效。</p>
 *
 * <p>注意：这里只做<b>判定</b>，扣耐久时必须用同一个 {@link InteractionHand}，
 * 否则会出现「用副手的竿钓鱼、扣主手物品的耐久」。</p>
 */
public final class FishingRodUtil {
    private FishingRodUtil() {
    }

    /**
     * 女仆手上用来钓鱼的那只手：主手优先，主手不是鱼竿时看副手。
     *
     * @return 拿鱼竿的手；两只手都不是鱼竿时返回 {@code null}
     */
    public static InteractionHand getRodHand(EntityMaid maid) {
        if (isRod(maid.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (isRod(maid.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /**
     * {@link #getRodHand} 对应的物品栈；两只手都没有鱼竿时返回 {@link ItemStack#EMPTY}。
     */
    public static ItemStack getRod(EntityMaid maid) {
        InteractionHand hand = getRodHand(maid);
        return hand == null ? ItemStack.EMPTY : maid.getItemInHand(hand);
    }

    /**
     * 手上是否有可用的鱼竿（主手或副手）。
     */
    public static boolean hasRod(EntityMaid maid) {
        return getRodHand(maid) != null;
    }

    private static boolean isRod(ItemStack stack) {
        return stack.getItem() instanceof FishingRodItem;
    }
}


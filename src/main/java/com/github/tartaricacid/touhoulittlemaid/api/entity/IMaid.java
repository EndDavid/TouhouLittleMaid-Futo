package com.github.tartaricacid.touhoulittlemaid.api.entity;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆部分方法的接口化（上游同名接口的精简版）。
 *
 * <p>上游通过 Forge 的 {@code ConvertMaidEvent} 允许其他模组把任意 {@link Mob}
 * 伪装成女仆；Fabric 侧暂无该事件，因此这里只做 {@code instanceof} 判断，
 * 后续接入事件系统时在此补回。</p>
 */
public interface IMaid {
    @Nullable
    static IMaid convert(Mob mob) {
        if (mob instanceof IMaid maid) {
            return maid;
        }
        return null;
    }

    /**
     * 转换回实体对象。
     */
    Mob asEntity();

    /**
     * 是否处于坐下姿态（驱动 {@code sit} 动画）。
     */
    default boolean isMaidInSittingPose() {
        return false;
    }

    /**
     * 当前工作模式。
     *
     * <p>上游 {@code IMaid} 同样声明了它：动画层、音效与 Brain 都要知道女仆在做什么工作。</p>
     */
    IMaidTask getTask();

    /**
     * 是否处于「乞求」姿态（驱动 {@code beg} 动画）。
     */
    default boolean isBegging() {
        return false;
    }

    /**
     * 女仆手臂是否举起（对应上游 {@code IMaid#isSwingingArms}）。
     *
     * <p>上游只在旧版（非 Gecko）模型上用得到：{@code MaidBaseAnimation#getArmSwing}
     * 在它为真时把双臂摆成「举械」姿态。由 {@code MaidShootTargetTask} / {@code MaidTridentTargetTask} / {@code MaidAttackStrafingTask}
     * 等远程攻击行为写入。</p>
     */
    default boolean isSwingingArms() {
        return false;
    }

    /**
     * 是否装备了背包。
     */
    default boolean hasBackpack() {
        return false;
    }

    /**
     * 是否攀爬中（驱动 {@code ladder_*} 动画）。
     */
    default boolean isOnClimbable() {
        return false;
    }

    /**
     * 是否持有钓鱼钩（驱动 {@code hold_mainhand:fishing}）。
     */
    default boolean hasFishingHook() {
        return false;
    }

    /**
     * 上次渲染时双手物品的快照，供持物动画检测物品变化。
     * 必须是稳定数组（实现方需要持有一个字段）。
     */
    default ItemStack[] getHandItemsForAnimation() {
        return new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY};
    }
}

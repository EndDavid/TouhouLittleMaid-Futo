package com.github.tartaricacid.touhoulittlemaid.client.animation.gecko;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.function.BiPredicate;

/**
 * 女仆主控制器的动画状态表（与上游同名类一致）。
 *
 * <p>状态按 {@link Priority} 从高到低匹配，命中即停。其中：
 * <ul>
 *     <li>{@code sit} 位于 HIGH 优先级，由 {@link IMaid#isMaidInSittingPose()} 驱动
 *         —— 这是「驯服后潜行右键能不能坐下」的关键</li>
 *     <li>{@code walk} / {@code run} 位于 LOW，用 {@code limbSwingAmount > 0.05} 判断，
 *         不能依赖 {@code AnimationEvent#isMoving()}（该字段在本版本不可靠）</li>
 *     <li>{@code idle} 位于 LOWEST 且恒为真，作为兜底</li>
 * </ul>
 *
 * <p>棋类 / 书架 / 电脑等「在坐垫上玩」的状态依赖尚未移植的 {@code EntitySit}，
 * 暂时省略。</p>
 */
public final class AnimationRegister {
    private static final double MIN_SPEED = 0.05;

    private AnimationRegister() {
    }

    public static void registerAnimationState() {
        register("death", ILoopType.EDefaultLoopTypes.PLAY_ONCE, Priority.HIGHEST,
                (maid, event) -> maid.asEntity().isDeadOrDying());
        register("sleep", Priority.HIGHEST,
                (maid, event) -> maid.asEntity().getPose() == Pose.SLEEPING);
        register("swim", Priority.HIGHEST,
                (maid, event) -> maid.asEntity().isVisuallySwimming());

        register("ladder_up", Priority.HIGHEST,
                (maid, event) -> maid.isOnClimbable() && getVerticalSpeed(maid) > 0);
        register("ladder_stillness", Priority.HIGHEST,
                (maid, event) -> maid.isOnClimbable() && getVerticalSpeed(maid) == 0);
        register("ladder_down", Priority.HIGHEST,
                (maid, event) -> maid.isOnClimbable() && getVerticalSpeed(maid) < 0);

        register("boat", Priority.HIGH,
                (maid, event) -> maid.asEntity().getVehicle() instanceof Boat);
        register("chair", Priority.HIGH,
                (maid, event) -> maid.asEntity().isPassenger()
                        || (maid.asEntity() instanceof EntityMaid entityMaid && entityMaid.isCarried()));

        register("sit", Priority.HIGH,
                (maid, event) -> maid.isMaidInSittingPose());

        register("swim_stand", Priority.NORMAL,
                (maid, event) -> maid.asEntity().isInWater());
        register("attacked", ILoopType.EDefaultLoopTypes.PLAY_ONCE, Priority.NORMAL,
                (maid, event) -> maid.asEntity().hurtTime > 0);
        register("jump", Priority.NORMAL,
                (maid, event) -> !maid.asEntity().isOnGround() && !maid.asEntity().isInWater());

        register("run", Priority.LOW,
                (maid, event) -> maid.asEntity().isOnGround() && maid.asEntity().isSprinting());
        register("walk", Priority.LOW,
                (maid, event) -> maid.asEntity().isOnGround() && event.getLimbSwingAmount() > MIN_SPEED);

        register("idle", Priority.LOWEST, (maid, event) -> true);
    }

    private static void register(String animationName, ILoopType loopType, int priority,
                                 BiPredicate<IMaid, AnimationEvent<?>> predicate) {
        AnimationManager.getInstance().register(new AnimationState(animationName, loopType, priority, predicate));
    }

    private static void register(String animationName, int priority,
                                 BiPredicate<IMaid, AnimationEvent<?>> predicate) {
        register(animationName, ILoopType.EDefaultLoopTypes.LOOP, priority, predicate);
    }

    private static float getVerticalSpeed(IMaid maid) {
        Mob entity = maid.asEntity();
        return 20 * (float) (entity.position().y - entity.yo);
    }
}

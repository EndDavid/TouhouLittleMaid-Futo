package com.github.tartaricacid.touhoulittlemaid.client.animation.gecko;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
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
        // 旧版模型必须跳过 sleep —— 与上游一致。
        //
        // 理由：躺平这个姿态已经由 LivingEntityRenderer#setupRotations 完成
        //     Y(sleepDirectionToRotation(bedFacing)) → Z(getFlipDegrees() = 90) → Y(270)
        // 而 animation/maid.animation.json 里 sleep 的 Root 还有
        //     [-13.26, -77.80, 23.76] 度
        // 在「已经躺平」的模型空间里，那个 -77.8 度的 Y 轴旋转不再是把人转个方向，
        // 而是绕身体长轴翻滚 —— 于是整个人侧过去了，而且绕 Root 原点转，
        // 顺带把身体整体推离床心（这就是位置偏的来源）。
        //
        // 上游对旧版模型根本不走 JSON 动画，睡觉用的是 MaidBaseAnimation 里的硬编码逻辑
        // （只把 head 抬头 15 度、隐藏帽子/尾巴/翅膀、切换睡觉贴图），
        // 所以它看起来是平的。本移植版的等价物在 InnerAnimations 里，
        // 已经逐字照抄了那几条，因此这里让旧版模型落回它即可。
        register("sleep", Priority.HIGHEST,
                (maid, event) -> !isLegacyModel(event)
                        && maid.asEntity().getPose() == Pose.SLEEPING);
        register("swim", Priority.HIGHEST,
                (maid, event) -> maid.asEntity().isVisuallySwimming());

        register("ladder_up", Priority.HIGHEST,
                (maid, event) -> maid.isOnClimbable() && getVerticalSpeed(maid) > 0);
        register("ladder_stillness", Priority.HIGHEST,
                (maid, event) -> maid.isOnClimbable() && getVerticalSpeed(maid) == 0);
        register("ladder_down", Priority.HIGHEST,
                (maid, event) -> maid.isOnClimbable() && getVerticalSpeed(maid) < 0);

        register("boat", Priority.HIGH,
                (maid, event) -> !isLegacyModel(event) && maid.asEntity().getVehicle() instanceof Boat);
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

    /**
     * 事件对应的模型是否为「旧版」模型。
     *
     * <p><b>为什么 {@code boat} 这一支要按它做开关：</b>上游对旧版模型走的是
     * {@code MaidBaseAnimation} 那套硬编码 Java 动画，其中「坐在载具里」只有一条规则：
     * {@code ridingPosture}（双腿前屈 65 度、左右各外撇 15 度，外加一个 {@code translate(0, 0.3, 0)}）。
     * 上游<b>从不</b>把 {@code animation/maid.animation.json} 里的 {@code boat} 套到旧版骨架上。</p>
     *
     * <p>本移植版为了复用默认动画集，把旧版骨骼改名成 Gecko 命名（{@code GeckoModelLoader#LEGACY_BONE_ALIASES}），
     * 于是 {@code boat} 也会命中旧版模型。但那支动画是靠 {@code Root} 旋转 -90 度（绕 X 轴）
     * 加 {@code AllBody} / {@code DownBody} 这些中间层做出「半躺在船里」的；
     * 旧版骨架的 {@code Root} 是加载时合成出来的、没有任何骨盆枢轴补偿，也没有
     * {@code DownBody}/{@code AllBody}，所以整条链算下来就是<b>把整个人翻过去</b>。</p>
     *
     * <p>因此这里让旧版模型跳过 {@code boat}，落回下面的 {@code chair}
     * （即已按 {@code ridingPosture} 数值移植的旧版动画）—— 与上游行为一致。</p>
     */
    private static boolean isLegacyModel(AnimationEvent<?> event) {
        return event.getAnimatableEntity() instanceof GeckoMaidEntity<?> gecko && gecko.isLegacyModel();
    }
}

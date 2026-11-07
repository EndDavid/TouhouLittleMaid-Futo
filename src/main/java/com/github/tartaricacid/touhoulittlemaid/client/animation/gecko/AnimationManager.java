package com.github.tartaricacid.touhoulittlemaid.client.animation.gecko;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionArmor;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionalHold;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionalPassenger;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionalSwing;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionalUse;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionalVehicle;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 女仆动画管理器（上游同名类的精简版）。
 *
 * <p>主控制器 {@link #predicateMain} 按 {@link Priority} 从高到低遍历 {@link AnimationState}，
 * 第一个命中的状态即为播放动画；状态表由 {@link AnimationRegister} 注册。
 * 另外还提供持物 / 挥击 / 使用 / 护甲 / 乘客 / 并行层等辅助控制器。</p>
 *
 * <p>与上游的差异：TACZ 枪械、棋类坐垫（{@code EntitySit}）、椅子（{@code EntityChair}）
 * 这些依赖尚未移植内容的判定被省略，其余逻辑保持一致。</p>
 */
public final class AnimationManager {
    private static AnimationManager MANAGER;

    /**
     * 旧版（非 Gecko）模型使用的「举械」常量姿态名。
     *
     * <p>等价于上游 {@code MaidBaseAnimation#getArmSwing}：</p>
     * <pre>
     * if (!mainHandItem.isEmpty() &amp;&amp; maid.isSwingingArms()) {
     *     armLeft .setRotateAngleX(-1.396f);  armLeft .setRotateAngleY( 0.785f);
     *     armRight.setRotateAngleX(-1.396f);  armRight.setRotateAngleY(-0.174f);
     * }
     * </pre>
     * <p>-1.396 rad = -80°，0.785 rad = +45°，-0.174 rad = -10°。躯干与头部完全不动。</p>
     *
     * <p><b>为什么由 {@code use} 控制器一直播放它，而不是靠 {@code hold_*} 动画：</b>
     * 上游只在 {@code isSwingingArms()} 为真时抬臂，而这个标志由远程攻击行为的
     * {@code start} / {@code stop} 写入，覆盖「蓄力 → 松手 → 冷却 → 下一次蓄力」整段。
     * 若改由 {@code hold_*} 在 {@code use} 结束后接手，则会掉进默认动画集里
     * {@code hold_mainhand:bow} 那段 2 秒的花式关键帧（同 {@code :spear}、{@code :crossbow}
     * 的单臂姿态），表现为「每射一次手臂甩一下／放下来再抬起来」。</p>
     */
    private static final String LEGACY_ARMED_POSE = "armed_pose";

    /**
     * 旧版（非 Gecko）骨架上有「专属替代版本」的 {@code use_*} 动画名。
     *
     * <p>这些名字在 {@code animation/maid_legacy.animation.json} 里被重写成只含
     * {@code LeftArm} / {@code RightArm} 的常量姿态，数值同 {@link #LEGACY_ARMED_POSE}。</p>
     *
     * <p>之所以要单独列一张表，而不是直接放行所有 {@code use_*}：
     * 吃 / 喝 / 望远镜 / 盾这几支动画带 {@code UpBody}、{@code Head}、{@code UpperBody}
     * 关键帧，在平铺的旧版骨架上会把头拧过去，仍旧要走「持物」降级。</p>
     */
    private static final Set<String> LEGACY_USE_ANIMATIONS = Set.of(
            "use_mainhand:bow", "use_offhand:bow",
            "use_mainhand:crossbow", "use_offhand:crossbow",
            "use_mainhand:gohei", "use_offhand:gohei",
            "use_mainhand:spear", "use_offhand:spear"
    );

    /**
     * <p>旧版模型必须<b>拦掉</b>的 {@code hold_*} 动画名。
     *
     * <p>默认动画集里这几支都不是静态姿势：{@code hold_*:bow} 手臂 0° → -94° → +20.7°
     * 走满 2.08 秒，{@code hold_*:spear} 同理，{@code hold_*:crossbow} 是单臂姿态。
     * 旧版骨架没有 {@code AllBody} / {@code UpperBody} / {@code AllHead} 这些补偿层，
     * 播出来只会是「放下再抬起 / 单臂挥舞」。上游对旧版模型根本没有这套基岩动画
     * （见 {@link #LEGACY_ARMED_POSE} 的说明），所以这里直接拦掉，让手臂回落到
     * {@code main} 控制器的待机姿态。</p>
     */
    private static final Set<String> LEGACY_ARMED_HOLD_ANIMATIONS = Set.of(
            "hold_mainhand:bow", "hold_offhand:bow",
            "hold_mainhand:crossbow", "hold_offhand:crossbow",
            "hold_mainhand:charged_crossbow", "hold_offhand:charged_crossbow",
            "hold_mainhand:spear", "hold_offhand:spear"
    );

    /**
     * 旧版模型「正在使用某个物品」时的单臂抬起姿态名。
     *
     * <p>数值直接来自上游 {@code MaidBaseAnimation#getArmDefault} 的「使用动画」分支
     * （1.18.2 第 317~320 行左手 / 第 342~345 行右手）：</p>
     * <pre>
     * if (mob.isUsingItem() &amp;&amp; mob.getUsedItemHand() == OFF_HAND) {
     *     armLeft .setRotateAngleX(initX - 80°);  armLeft .setRotateAngleY(initY + 25°);
     * }
     * if (mob.isUsingItem() &amp;&amp; mob.getUsedItemHand() == MAIN_HAND) {
     *     armRight.setRotateAngleX(initX - 80°);  armRight.setRotateAngleY(initY - 20°);
     * }
     * </pre>
     * <p>旧版骨架的臂骨没有初始旋转（{@code armLeft} / {@code armRight} 只有 pivot），
     * 所以 {@code initX = initY = 0}，落地值就是 {@code LeftArm [-80, 25, 0]} /
     * {@code RightArm [-80, -20, 0]}，见 {@code maid_legacy.animation.json} 里的
     * {@code use_offhand} / {@code use_mainhand}。</p>
     *
     * <p><b>它与 {@link #LEGACY_ARMED_POSE} 的区别：</b>举械（弓 / 弩 / 御币 / 三叉戟）
     * 上游走 {@code getArmSwing}，<b>双臂</b>一起抬；其余物品走 {@code getArmDefault}，
     * 只抬<b>正在使用的那一只</b>，另一只手保持待机 / 走路摆动。盾牌就属于后者。</p>
     */
    private static final String LEGACY_USE_MAINHAND = "use_mainhand";
    private static final String LEGACY_USE_OFFHAND = "use_offhand";

    @SuppressWarnings("unchecked")
    private final ReferenceArrayList<AnimationState>[] data = new ReferenceArrayList[Priority.LOWEST + 1];

    private AnimationManager() {
        for (int i = 0; i < data.length; i++) {
            data[i] = new ReferenceArrayList<>(6);
        }
    }

    public static AnimationManager getInstance() {
        if (MANAGER == null) {
            MANAGER = new AnimationManager();
        }
        return MANAGER;
    }

    @NotNull
    public static PlayState playLoopAnimation(AnimationEvent<?> event, String animationName) {
        return playAnimation(event, animationName, ILoopType.EDefaultLoopTypes.LOOP);
    }

    @NotNull
    private static PlayState playAnimation(AnimationEvent<?> event, String animationName, ILoopType loopType) {
        logMainAnimation(event, animationName);
        event.getController().setAnimation(new AnimationBuilder().addAnimation(animationName, loopType));
        return PlayState.CONTINUE;
    }

    @NotNull
    private static PlayState playAnimation(AnimationEvent<?> event, String animationName) {
        logMainAnimation(event, animationName);
        event.getController().setAnimation(new AnimationBuilder().addAnimation(animationName));
        return PlayState.CONTINUE;
    }

    public void register(AnimationState state) {
        data[state.getPriority()].add(state);
    }

    // ------------------------------------------------------------------ 主控制器

    public PlayState predicateParallel(AnimationEvent<?> event, String animationName) {
        if (Minecraft.getInstance().isPaused()) {
            return PlayState.STOP;
        }
        return playLoopAnimation(event, animationName);
    }

    @NotNull
    public PlayState predicateMain(AnimationEvent<GeckoMaidEntity<?>> event) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return PlayState.STOP;
        }
        // Sitting has the highest priority: the sit animation lowers and offsets Root
        // (e.g. winefox Root position [0, -14.25, 2]), which is what makes the maid
        // actually sit on her built-in fox mount.
        if (maid.isMaidInSittingPose() && !maid.asEntity().isPassenger()) {
            return playAnimation(event, "sit", ILoopType.EDefaultLoopTypes.LOOP);
        }
        for (int i = Priority.HIGHEST; i <= Priority.LOWEST; i++) {
            // 载具动画单独检查
            if (i == Priority.HIGH) {
                PlayState vehicleAnimation = getVehicleAnimation(event);
                if (vehicleAnimation != null) {
                    return vehicleAnimation;
                }
            }
            for (AnimationState state : data[i]) {
                if (state.getPredicate().test(maid, event)) {
                    return playAnimation(event, state.getAnimationName(), state.getLoopType());
                }
            }
        }
        return PlayState.STOP;
    }

    /**
     * 主控制器「被请求播放的动画」发生变化时打印一行。
     *
     * <p>这条日志是「模型不切换」类问题的判据：坐姿开关（{@code sitting}）与服务端行为
     * 同源，因此只要这里出现了 {@code sit -> idle} 而画面仍坐在原地，问题就出在动画/模型层，
     * 而不是状态层。</p>
     *
     * <p>「上一次的动画名」存在动画实例自己身上，而不是按实体做 key 的 Map：
     * {@code Entity#equals/hashCode} 是按实体 id 的，区块重载后新实体实例会命中旧记录。</p>
     */
    private static void logMainAnimation(AnimationEvent<?> event, String animationName) {
        AnimationController<?> controller = event.getController();
        if (controller == null || !"main".equals(controller.getName())) {
            return;
        }
        if (!(event.getAnimatableEntity() instanceof GeckoMaidEntity<?> gecko)) {
            return;
        }
        IMaid maid = gecko.getMaid();
        if (maid == null) {
            return;
        }
        String last = gecko.getLastMainAnimation();
        gecko.setLastMainAnimation(animationName);
        if (animationName.equals(last)) {
            return;
        }
        Mob mob = maid.asEntity();
        TouhouLittleMaid.LOGGER.info("[Futo] anim {} -> {} (sitting={}, moving={}, y={})",
                last, animationName, maid.isMaidInSittingPose(),
                mob.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4,
                String.format(java.util.Locale.ROOT, "%.3f", mob.getY()));
    }

    // ------------------------------------------------------------------ 持物

    public PlayState predicateOffhandHold(AnimationEvent<GeckoMaidEntity<?>> event) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return PlayState.STOP;
        }
        // 手为空时不要去播 hold_offhand:empty：多数模型包没有这段动画，
        // 会刷日志并让控制器停在旧状态。
        if (maid.asEntity().getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
            return PlayState.STOP;
        }
        if (!maid.asEntity().swinging && !maid.asEntity().isUsingItem()) {
            ItemStack offhandItem = maid.asEntity().getItemInHand(InteractionHand.OFF_HAND);
            if (offhandItem.is(Items.CROSSBOW) && CrossbowItem.isCharged(offhandItem)
                    && !isSuppressedLegacyArmedHold(event, "hold_offhand:charged_crossbow")) {
                return playAnimation(event, "hold_offhand:charged_crossbow", ILoopType.EDefaultLoopTypes.LOOP);
            }
        }
        if (checkSwingAndUse(maid, InteractionHand.OFF_HAND)) {
            ItemStack offhandItem = maid.asEntity().getItemInHand(InteractionHand.OFF_HAND);
            if (!isSameItem(maid, offhandItem, InteractionHand.OFF_HAND)) {
                maid.getHandItemsForAnimation()[InteractionHand.OFF_HAND.ordinal()] = offhandItem;
                playAnimation(event, "empty", ILoopType.EDefaultLoopTypes.LOOP);
            }
            ResourceLocation id = event.getAnimatableEntity().getAnimationFileLocation();
            ConditionalHold conditionalHold = ConditionManager.getHoldOffhand(id);
            if (conditionalHold != null) {
                String name = conditionalHold.doTest(maid, InteractionHand.OFF_HAND);
                if (StringUtils.isNoneBlank(name) && !isSuppressedLegacyArmedHold(event, name)) {
                    return playAnimation(event, name, ILoopType.EDefaultLoopTypes.LOOP);
                }
            }
        }
        return PlayState.STOP;
    }

    public PlayState predicateMainhandHold(AnimationEvent<GeckoMaidEntity<?>> event) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return PlayState.STOP;
        }
        if (maid.asEntity().getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return PlayState.STOP;
        }
        if (!maid.asEntity().swinging && !maid.asEntity().isUsingItem()) {
            ItemStack mainHandItem = maid.asEntity().getItemInHand(InteractionHand.MAIN_HAND);
            if (mainHandItem.is(Items.CROSSBOW) && CrossbowItem.isCharged(mainHandItem)
                    && !isSuppressedLegacyArmedHold(event, "hold_mainhand:charged_crossbow")) {
                return playAnimation(event, "hold_mainhand:charged_crossbow", ILoopType.EDefaultLoopTypes.LOOP);
            }
            if (maid.hasFishingHook()) {
                return playAnimation(event, "hold_mainhand:fishing", ILoopType.EDefaultLoopTypes.LOOP);
            }
        }
        if (checkSwingAndUse(maid, InteractionHand.MAIN_HAND)) {
            ItemStack mainHandItem = maid.asEntity().getItemInHand(InteractionHand.MAIN_HAND);
            if (!isSameItem(maid, mainHandItem, InteractionHand.MAIN_HAND)) {
                maid.getHandItemsForAnimation()[InteractionHand.MAIN_HAND.ordinal()] = mainHandItem;
                playAnimation(event, "empty", ILoopType.EDefaultLoopTypes.LOOP);
            }
            ResourceLocation id = event.getAnimatableEntity().getAnimationFileLocation();
            ConditionalHold conditionalHold = ConditionManager.getHoldMainhand(id);
            if (conditionalHold != null) {
                String name = conditionalHold.doTest(maid, InteractionHand.MAIN_HAND);
                if (StringUtils.isNoneBlank(name) && !isSuppressedLegacyArmedHold(event, name)) {
                    return playAnimation(event, name, ILoopType.EDefaultLoopTypes.LOOP);
                }
            }
        }
        return PlayState.STOP;
    }

    private boolean isSameItem(IMaid maid, ItemStack maidItem, InteractionHand hand) {
        ItemStack preItem = maid.getHandItemsForAnimation()[hand.ordinal()];
        if (preItem.isDamaged()) {
            return ItemStack.isSame(maidItem, preItem);
        }
        return ItemStack.matches(maidItem, preItem);
    }

    private boolean checkSwingAndUse(IMaid maid, InteractionHand hand) {
        if (maid.asEntity().swinging && maid.asEntity().swingingArm == hand) {
            return false;
        }
        return !maid.asEntity().isUsingItem() || maid.asEntity().getUsedItemHand() != hand;
    }

    /**
     * 旧版模型是否要拦掉这支 {@code hold_*} 动画。
     *
     * <p>只有「旧版模型 + 举械类持物名」会被拦；Gecko 模型与其余持物（剑 / 镐 / 火把…）
     * 完全不受影响。</p>
     */
    private static boolean isSuppressedLegacyArmedHold(AnimationEvent<GeckoMaidEntity<?>> event, String name) {
        return event.getAnimatableEntity().isLegacyModel()
                && LEGACY_ARMED_HOLD_ANIMATIONS.contains(name);
    }

    // ------------------------------------------------------------------ 挥击 / 使用 / 杂项

    public PlayState predicateSwing(AnimationEvent<GeckoMaidEntity<?>> event) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return PlayState.STOP;
        }
        if (maid.asEntity().swinging && !maid.asEntity().isSleeping()) {
            if (maid.asEntity().swingTime == 0) {
                // 空动画用于重置 PLAY_ONCE 动画
                playAnimation(event, "empty", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
            }
            ResourceLocation id = event.getAnimatableEntity().getAnimationFileLocation();
            boolean mainHand = maid.asEntity().swingingArm == InteractionHand.MAIN_HAND;
            ConditionalSwing conditionalSwing = mainHand
                    ? ConditionManager.getSwingMainhand(id) : ConditionManager.getSwingOffhand(id);
            if (conditionalSwing != null) {
                String name = conditionalSwing.doTest(maid, maid.asEntity().swingingArm);
                if (StringUtils.isNoneBlank(name)) {
                    return playAnimation(event, name, ILoopType.EDefaultLoopTypes.PLAY_ONCE);
                }
            }
            String defaultSwing = mainHand ? "swing_hand" : "swing_offhand";
            return playAnimation(event, defaultSwing, ILoopType.EDefaultLoopTypes.PLAY_ONCE);
        }
        return PlayState.CONTINUE;
    }

    public PlayState predicateUse(AnimationEvent<GeckoMaidEntity<?>> event) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return PlayState.STOP;
        }

        // 旧版模型 + 举械：与上游 MaidBaseAnimation#getArmSwing 一一对应。
        // 它在 isSwingingArms() 为真的整段远程攻击期间生效（蓄力、松手、冷却全都在内），
        // 因此这里必须放在 isUsingItem() 之前 —— 否则射击之后的冷却期会掉到 hold_*，
        // 出现「每射一次手放下再抬起」。
        if (event.getAnimatableEntity().isLegacyModel()
                && maid.isSwingingArms()
                && !maid.asEntity().isSleeping()
                && !maid.asEntity().getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return playAnimation(event, LEGACY_ARMED_POSE, ILoopType.EDefaultLoopTypes.LOOP);
        }

        if (maid.asEntity().isUsingItem() && !maid.asEntity().isSleeping()) {
            if (maid.asEntity().getTicksUsingItem() == 1) {
                playAnimation(event, "empty", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
            }
            boolean mainHand = maid.asEntity().getUsedItemHand() == InteractionHand.MAIN_HAND;
            InteractionHand hand = mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ResourceLocation id = event.getAnimatableEntity().getAnimationFileLocation();

            // 旧版（非 Gecko）模型：上游用 MaidBaseAnimation 那套硬编码 Java 动画
            // （本移植版未搬），其中「使用中」只有两条规则：
            //   * MaidBaseAnimation#getArmSwing —— 握弓 / 握弩 / 持御币 / 举三叉戟
            //     （由 MaidShootTargetTask / MaidTridentTargetTask / MaidAttackStrafingTask
            //     调 setSwingingArms 触发）时双臂前举，躯干与头部完全不动；
            //   * MaidBaseAnimation#getArmDefault —— 其余物品只把右手抬到 -80°/-20°。
            // 前者已经在 maid_legacy.animation.json 里按上述数值重写过（只保留双臂关键帧），
            // 所以这里可以直接放行同名的 use_* 动画。
            if (event.getAnimatableEntity().isLegacyModel()) {
                ConditionalUse use = mainHand
                        ? ConditionManager.getUseMainhand(id) : ConditionManager.getUseOffhand(id);
                if (use != null) {
                    String name = use.doTest(maid, hand);
                    if (StringUtils.isNoneBlank(name) && LEGACY_USE_ANIMATIONS.contains(name)) {
                        return playAnimation(event, name, ILoopType.EDefaultLoopTypes.LOOP);
                    }
                }
                // 其余物品（盾 / 食物 / 药水 / 牛奶 / 望远镜……）：
                // 上游 MaidBaseAnimation#getArmDefault 只抬「正在使用的那只手」
                // （左手 -80°/+25°，右手 -80°/-20°，见 LEGACY_USE_MAINHAND 的说明）。
                //
                // 这里**不能**再退回 hold_*：hold_offhand:shield 这类动画是给 Gecko 骨架
                // 写的 1 秒挥臂（0~1 秒 X 从 +38.8° 摆到 +10°），旧版骨架没有 UpperBody /
                // AllBody 补偿层，播出来就是「盾先往身后甩，再挂在大腿外侧」——
                // 也就是「举盾格挡时盾扭到身后」的直接成因。
                return playAnimation(event,
                        mainHand ? LEGACY_USE_MAINHAND : LEGACY_USE_OFFHAND,
                        ILoopType.EDefaultLoopTypes.LOOP);
            }

            ConditionalUse conditionalUse = mainHand
                    ? ConditionManager.getUseMainhand(id) : ConditionManager.getUseOffhand(id);
            if (conditionalUse != null) {
                String name = conditionalUse.doTest(maid, hand);
                if (StringUtils.isNoneBlank(name)) {
                    return playAnimation(event, name, ILoopType.EDefaultLoopTypes.LOOP);
                }
            }
            return playAnimation(event, mainHand ? "use_mainhand" : "use_offhand",
                    ILoopType.EDefaultLoopTypes.LOOP);
        }
        return PlayState.STOP;
    }

    public PlayState predicateMisc(AnimationEvent<GeckoMaidEntity<?>> event) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return PlayState.STOP;
        }
        // 祈求动画
        if (maid.isBegging()) {
            return playAnimation(event, "beg", ILoopType.EDefaultLoopTypes.LOOP);
        }
        return PlayState.STOP;
    }

    // ------------------------------------------------------------------ 护甲

    public <T extends Mob> PlayState predicateArmor(AnimationEvent<GeckoMaidEntity<T>> event, EquipmentSlot slot) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return PlayState.STOP;
        }
        if (maid.asEntity().getItemBySlot(slot).isEmpty()) {
            return PlayState.STOP;
        }
        ResourceLocation id = event.getAnimatableEntity().getAnimationFileLocation();
        ConditionArmor conditionArmor = ConditionManager.getArmor(id);
        if (conditionArmor != null) {
            String name = conditionArmor.doTest(maid, slot);
            if (StringUtils.isNoneBlank(name)) {
                return playAnimation(event, name, ILoopType.EDefaultLoopTypes.LOOP);
            }
        }
        String defaultName = slot.getName() + ":default";
        // 动画集合是惰性构建的（旧版模型在首次用到时才解析），这里必须走 getAnimationFile
        var animationFile = GeckoLibCache.getInstance().getAnimationFile(id);
        if (animationFile != null && animationFile.animations().containsKey(defaultName)) {
            return playAnimation(event, defaultName, ILoopType.EDefaultLoopTypes.LOOP);
        }
        return PlayState.STOP;
    }

    // ------------------------------------------------------------------ 载具 / 乘客

    @Nullable
    public PlayState getVehicleAnimation(AnimationEvent<GeckoMaidEntity<?>> event) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return null;
        }
        Mob mob = maid.asEntity();
        Entity vehicle = mob.getVehicle();
        if (vehicle == null || !vehicle.isAlive()) {
            return null;
        }
        ResourceLocation id = event.getAnimatableEntity().getAnimationFileLocation();
        ConditionalVehicle vehicleCondition = ConditionManager.getVehicle(id);
        if (vehicleCondition != null) {
            String name = vehicleCondition.doTest(mob);
            if (StringUtils.isNoneBlank(name)) {
                return playAnimation(event, name, ILoopType.EDefaultLoopTypes.LOOP);
            }
        }
        return null;
    }

    public PlayState predicatePassengerAnimation(AnimationEvent<GeckoMaidEntity<?>> event) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return PlayState.STOP;
        }
        Mob mob = maid.asEntity();
        Entity passenger = mob.getFirstPassenger();
        if (passenger == null || !passenger.isAlive()) {
            return PlayState.STOP;
        }
        ResourceLocation id = event.getAnimatableEntity().getAnimationFileLocation();
        ConditionalPassenger conditionalPassenger = ConditionManager.getPassenger(id);
        if (conditionalPassenger != null) {
            String name = conditionalPassenger.doTest(mob);
            if (StringUtils.isNoneBlank(name)) {
                return playAnimation(event, name, ILoopType.EDefaultLoopTypes.LOOP);
            }
        }
        return PlayState.STOP;
    }
}


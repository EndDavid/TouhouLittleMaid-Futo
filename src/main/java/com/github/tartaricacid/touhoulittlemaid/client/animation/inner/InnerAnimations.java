package com.github.tartaricacid.touhoulittlemaid.client.animation.inner;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内建动画注册表，对应上游 {@code client/animation/inner/InnerAnimation}。
 *
 * <h2>这是什么东西</h2>
 *
 * <p>TLM 的旧版（1.10 / 1.12）模型不在 JSON 里写关键帧动画，而是在
 * {@code maid_model.json} 的 {@code animation} 字段里列一串<b>伪路径</b>，例如：</p>
 *
 * <pre>
 * "animation": [
 *     "touhou_little_maid:animation/maid/default/head/default.js",
 *     "touhou_little_maid:animation/maid/default/head/blink.js",
 *     "touhou_little_maid:animation/maid/default/leg/default.js",
 *     "touhou_little_maid:animation/maid/default/arm/default.js"
 * ]
 * </pre>
 *
 * <p>这些 {@code .js} 文件<b>在任何一个发布 jar 里都不存在</b>。上游把它们注册进
 * {@code InnerAnimation.INNER_ANIMATION} 这张表，用<b>同名 Java 匿名类顶替脚本</b>
 * （{@code CustomJsAnimationManger} 只处理玩家自定义包里的真 JS）。所以正确处理方式
 * 不是「当成资源去读」，而是「按路径查 Java 实现」。</p>
 *
 * <h2>本类与上游的对应关系</h2>
 *
 * <table border="1">
 *     <caption>已移植的内建动画</caption>
 *     <tr><th>上游来源</th><th>路径</th></tr>
 *     <tr><td>{@code MaidBaseAnimation}</td><td>head/default, head/blink, head/beg,
 *         head/music_shake, head/ear_shake, leg/default, arm/default, arm/swing,
 *         sit/default, sit/skirt_hidden, sit/skirt_rotation, sit/skirt_rotation_swing,
 *         sleep/default, tail/default, wing/default</td></tr>
 *     <tr><td>{@code MaidExtraAnimation}</td><td>head/hurt, health/less_show</td></tr>
 *     <tr><td>{@code MaidTaskAnimation}</td><td>task/attack, task/danmaku_attack,
 *         task/farm, task/extinguishing, task/ranged_attack</td></tr>
 *     <tr><td>{@code EntityBaseAnimation}</td><td>base/float/default,
 *         base/rotation/reciprocate</td></tr>
 *     <tr><td>{@code SpecialAnimation}</td><td>special/hecatia_dimension,
 *         special/wakasagihime_sit</td></tr>
 *     <tr><td>{@code MaidArmorAnimation}</td><td>armor/default（由
 *         {@code MaidRenderer#updateArmorBones} 承担，见那里的说明）</td></tr>
 * </table>
 *
 * <h2>两个 PoseStack 级别的位移没有移植</h2>
 *
 * <p>上游 {@code ridingPosture} 与 farm 分支还会调 {@code GlWrapper.translate} /
 * {@code GlWrapper.rotate}，那是<b>改 PoseStack</b>、不是改骨骼，而本移植版的动画求值
 * （{@code GeckoMaidEntity#setCustomAnimations}）拿不到 PoseStack。其中：</p>
 * <ul>
 *     <li>{@code ridingPosture} 的 {@code translate(0, 0.3, 0)} 已经在
 *         {@code MaidRenderer#setupRotations} 里按上游矩阵链实现（含符号推导）；</li>
 *     <li>farm 分支的 {@code translate(0, 0.0713625, -0.35876875)} +
 *         {@code rotate(22.5, 1, 0, 0)} 暂缺 —— 影响的是「种田任务时整体前倾下蹲」，
 *         腿部角度与其余动作仍然生效。</li>
 * </ul>
 */
public final class InnerAnimations {
    /** 路径 → 实现。键是全路径（含 {@code .js}）。 */
    private static final Map<ResourceLocation, MaidInnerAnimation> REGISTRY = new HashMap<>();
    /** 路径 → 是否已解析（避免每次渲染都拼字符串）。 */
    private static final Map<ResourceLocation, Boolean> USABLE = new HashMap<>();

    private InnerAnimations() {
    }

    static {
        register("animation/maid/default/head/default.js", InnerAnimations::headDefault);
        register("animation/maid/default/head/blink.js", InnerAnimations::headBlink);
        register("animation/maid/default/head/beg.js", InnerAnimations::headBeg);
        register("animation/maid/default/head/music_shake.js", InnerAnimations::headMusicShake);
        register("animation/maid/default/head/ear_shake.js", InnerAnimations::earShake);
        register("animation/maid/default/head/hurt.js", InnerAnimations::headHurt);
        register("animation/maid/default/leg/default.js", InnerAnimations::legDefault);
        register("animation/maid/default/arm/default.js", InnerAnimations::armDefault);
        register("animation/maid/default/arm/swing.js", InnerAnimations::armSwing);
        register("animation/maid/default/sit/default.js", InnerAnimations::sitDefault);
        register("animation/maid/default/sit/skirt_hidden.js", InnerAnimations::sitSkirtHidden);
        register("animation/maid/default/sit/skirt_rotation.js", InnerAnimations::sitSkirtRotation);
        register("animation/maid/default/sit/skirt_rotation_swing.js", InnerAnimations::sitSkirtRotationSwing);
        register("animation/maid/default/sleep/default.js", InnerAnimations::sleepDefault);
        register("animation/maid/default/tail/default.js", InnerAnimations::tailDefault);
        register("animation/maid/default/wing/default.js", InnerAnimations::wingDefault);
        register("animation/maid/default/health/less_show.js", InnerAnimations::healthLessShow);
        register("animation/maid/default/task/attack.js", InnerAnimations::taskAttack);
        register("animation/maid/default/task/danmaku_attack.js", InnerAnimations::taskDanmakuAttack);
        register("animation/maid/default/task/farm.js", InnerAnimations::taskFarm);
        register("animation/maid/default/task/extinguishing.js", InnerAnimations::taskExtinguishing);
        register("animation/maid/default/task/ranged_attack.js", InnerAnimations::taskRangedAttack);
        register("animation/base/float/default.js", InnerAnimations::baseFloatDefault);
        register("animation/base/rotation/reciprocate.js", InnerAnimations::baseRotationReciprocate);
        register("animation/special/hecatia_dimension.js", InnerAnimations::specialHecatia);
        register("animation/special/wakasagihime_sit.js", InnerAnimations::specialWakasagihime);
    }

    private static void register(String path, MaidInnerAnimation animation) {
        REGISTRY.put(new ResourceLocation("touhou_little_maid", path), animation);
    }

    /** 该路径是否有内建实现。 */
    public static boolean contains(@Nullable ResourceLocation id) {
        return id != null && REGISTRY.containsKey(id);
    }

    /**
     * 把模型声明的动画列表拆成「内建动画」按声明顺序排好。
     *
     * <p>顺序必须保留：上游就是按 {@code animation} 数组的顺序依次求值，
     * 后写的覆盖先写的（例如 {@code sit/default.js} 要在 {@code leg/default.js} 之后，
     * 否则坐姿会被走路摆腿顶掉）。</p>
     */
    public static List<MaidInnerAnimation> resolve(@Nullable List<ResourceLocation> declared) {
        List<MaidInnerAnimation> list = new ArrayList<>();
        if (declared == null) {
            return list;
        }
        for (ResourceLocation id : declared) {
            Boolean usable = USABLE.get(id);
            if (usable == null) {
                usable = REGISTRY.containsKey(id);
                USABLE.put(id, usable);
            }
            if (usable) {
                MaidInnerAnimation animation = REGISTRY.get(id);
                if (animation != null) {
                    list.add(animation);
                }
            }
        }
        return list;
    }

    // ================================================================== 头部

    /** {@code MaidBaseAnimation#getHeadDefault}。 */
    private static void headDefault(MaidInnerContext ctx) {
        AnimatedGeoBone head = ctx.bone("head");
        if (head != null) {
            // 上游：headPitch / netHeadYaw 已经是角度，乘 0.017453292 转成度
            head.setRotationX(ctx.headPitch() * 0.017453292F);
            head.setRotationY(ctx.netHeadYaw() * 0.017453292F);
            if (ctx.mob().isSleeping()) {
                head.setRotationX(15 * 0.017453292F);
            }
        }
        AnimatedGeoBone hat = ctx.bone("hat");
        if (hat != null) {
            hat.setHidden(ctx.mob().isSleeping());
        }
    }

    /** {@code MaidBaseAnimation#getHeadBlink}。 */
    private static void headBlink(MaidInnerContext ctx) {
        boolean sleeping = ctx.mob().isSleeping();
        float remainder = blinkRemainder(ctx);
        boolean visible = sleeping || (remainder > 55 && remainder < 60);
        hide(ctx.bone("blink"), !visible);
        hide(ctx.bone("blink2"), !visible);
    }

    private static float blinkRemainder(MaidInnerContext ctx) {
        long bits = ctx.mob().getUUID().getLeastSignificantBits();
        return (ctx.ageInTicks() + Math.abs(bits) % 10) % 60;
    }

    /** {@code MaidBaseAnimation#getHeadBeg}。 */
    private static void headBeg(MaidInnerContext ctx) {
        AnimatedGeoBone head = ctx.bone("head");
        AnimatedGeoBone ahoge = ctx.bone("ahoge");
        AnimatedGeoBone begShow = ctx.bone("begShow");
        if (ctx.maid().isBegging()) {
            if (head != null) {
                head.setRotationZ(0.139F);
            }
            if (ahoge != null) {
                ahoge.setRotationX((float) (Math.cos(ctx.ageInTicks()) * 0.05) + MaidInnerContext.initX(ahoge));
                ahoge.setRotationZ((float) (Math.sin(ctx.ageInTicks()) * 0.05) + MaidInnerContext.initZ(ahoge));
            }
            hide(begShow, false);
        } else {
            if (head != null) {
                head.setRotationZ(MaidInnerContext.initZ(head));
            }
            if (ahoge != null) {
                ahoge.setRotationZ(MaidInnerContext.initZ(ahoge));
            }
            hide(begShow, true);
        }
    }

    /**
     * {@code MaidBaseAnimation#getHeadMusicShake}。
     *
     * <p>上游条件是 {@code isPortableAudioPlay()}，而那个方法在 1.18.2 里已经是
     * {@code @Deprecated} 且<b>恒为 false</b>（随身音频物品没移植），所以这段实际不生效 ——
     * 保留实现只是为了与上游结构一致。</p>
     */
    private static void headMusicShake(MaidInnerContext ctx) {
        // isPortableAudioPlay() == false（上游已废弃），这里保持恒不生效
    }

    /** {@code MaidBaseAnimation#getEarShake}。 */
    private static void earShake(MaidInnerContext ctx) {
        AnimatedGeoBone left = ctx.bone("earLeftShake");
        AnimatedGeoBone right = ctx.bone("earRightShake");
        long bits = ctx.mob().getUUID().getLeastSignificantBits();
        float time = (ctx.ageInTicks() + Math.abs(bits) % 10) % 40;
        if (time < Math.PI * 4) {
            float rotationZ = (float) Math.abs(Math.sin(time * 0.25)) * 0.4F;
            if (left != null) {
                left.setRotationZ(MaidInnerContext.initZ(left) + rotationZ);
            }
            if (right != null) {
                right.setRotationZ(MaidInnerContext.initZ(right) - rotationZ);
            }
        } else {
            if (left != null) {
                left.setRotationZ(MaidInnerContext.initZ(left));
            }
            if (right != null) {
                right.setRotationZ(MaidInnerContext.initZ(right));
            }
        }
    }

    /** {@code MaidExtraAnimation#getHeadHurt}。 */
    private static void headHurt(MaidInnerContext ctx) {
        hide(ctx.bone("hurtBlink"), ctx.mob().hurtTime <= 0);
    }

    // ================================================================== 四肢

    /**
     * {@code MaidBaseAnimation#getLegDefault}。
     *
     * <p>farm 分支的 PoseStack 位移 / 整体前倾没有移植，见类注释；腿部角度仍然生效。</p>
     */
    private static void legDefault(MaidInnerContext ctx) {
        AnimatedGeoBone legLeft = ctx.bone("legLeft");
        AnimatedGeoBone legRight = ctx.bone("legRight");
        boolean isFarm = "farm".equals(ctx.maid().getTask().getUid().getPath())
                         && ctx.mob().swingTime > 0;
        if (legLeft != null) {
            double leftRad = Math.cos(ctx.limbSwing() * 0.67) * 0.3 * ctx.limbSwingAmount();
            if (isFarm) {
                leftRad -= 0.3927;
            }
            legLeft.setRotationX((float) leftRad);
            legLeft.setRotationY(MaidInnerContext.initY(legLeft));
            legLeft.setRotationZ(MaidInnerContext.initZ(legLeft));
        }
        if (legRight != null) {
            double rightRad = -Math.cos(ctx.limbSwing() * 0.67) * 0.3 * ctx.limbSwingAmount();
            if (isFarm) {
                rightRad -= 0.3927;
            }
            legRight.setRotationX((float) rightRad);
            legRight.setRotationY(MaidInnerContext.initY(legRight));
            legRight.setRotationZ(MaidInnerContext.initZ(legRight));
        }
    }

    /** {@code MaidBaseAnimation#getArmDefault}。 */
    private static void armDefault(MaidInnerContext ctx) {
        AnimatedGeoBone armLeft = ctx.bone("armLeft");
        AnimatedGeoBone armRight = ctx.bone("armRight");
        float attackAnim = ctx.mob().attackAnim;
        double f1 = 1.0 - Math.pow(1.0 - attackAnim, 4);
        double f2 = Math.sin(f1 * Math.PI);
        double f3 = Math.sin(attackAnim * Math.PI) * -0.7 * 0.75;
        boolean swingLeft = ctx.mob().swingingArm == net.minecraft.world.InteractionHand.OFF_HAND;

        if (armLeft != null) {
            if (ctx.maid().isMaidInSittingPose()) {
                armLeft.setRotationX(-1.3F);
            } else {
                armLeft.setRotationX((float) (-Math.cos(ctx.limbSwing() * 0.67) * 0.7 * ctx.limbSwingAmount()));
                armLeft.setRotationY(MaidInnerContext.initY(armLeft));
                armLeft.setRotationZ((float) (Math.cos(ctx.ageInTicks() * 0.05) * 0.05
                                              + MaidInnerContext.initZ(armLeft)));
                if (attackAnim > 0.0F && swingLeft) {
                    armLeft.setRotationX((float) (armLeft.getRotationX() - (f2 * 1.2 + f3)));
                    armLeft.setRotationZ((float) (armLeft.getRotationZ() + Math.sin(attackAnim * Math.PI) * -0.4));
                }
                if (ctx.mob().isUsingItem()
                    && ctx.mob().getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND) {
                    armLeft.setRotationX((float) (MaidInnerContext.initX(armLeft) - Math.PI * 80 / 180.0));
                    armLeft.setRotationY((float) (MaidInnerContext.initY(armLeft) + Math.PI * 25 / 180.0));
                }
            }
        }

        if (armRight != null) {
            if (ctx.maid().isMaidInSittingPose()) {
                armRight.setRotationX(-1.3F);
            } else {
                armRight.setRotationX((float) (Math.cos(ctx.limbSwing() * 0.67) * 0.7 * ctx.limbSwingAmount()));
                armRight.setRotationY(MaidInnerContext.initY(armRight));
                armRight.setRotationZ((float) (-Math.cos(ctx.ageInTicks() * 0.05) * 0.05
                                               + MaidInnerContext.initZ(armRight)));
                if (attackAnim > 0.0F && !swingLeft) {
                    armRight.setRotationX((float) (armRight.getRotationX() - (f2 * 1.2 + f3)));
                    armRight.setRotationZ((float) (armRight.getRotationZ() + Math.sin(attackAnim * Math.PI) * -0.4));
                }
                if (ctx.mob().isUsingItem()
                    && ctx.mob().getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                    armRight.setRotationX((float) (MaidInnerContext.initX(armRight) - Math.PI * 80 / 180.0));
                    armRight.setRotationY((float) (MaidInnerContext.initY(armRight) - Math.PI * 20 / 180.0));
                }
            }
        }
    }

    /** {@code MaidBaseAnimation#getArmSwing}。 */
    private static void armSwing(MaidInnerContext ctx) {
        AnimatedGeoBone armLeft = ctx.bone("armLeft");
        AnimatedGeoBone armRight = ctx.bone("armRight");
        if (!ctx.mob().getMainHandItem().isEmpty() && ctx.maid().isSwingingArms()) {
            if (armLeft != null) {
                armLeft.setRotationX(-1.396F);
                armLeft.setRotationY(0.785F);
            }
            if (armRight != null) {
                armRight.setRotationX(-1.396F);
                armRight.setRotationY(-0.174F);
            }
        }
    }

    // ================================================================== 坐姿

    /** {@code MaidBaseAnimation#getSitDefault}。 */
    private static void sitDefault(MaidInnerContext ctx) {
        AnimatedGeoBone head = ctx.bone("head");
        if (head != null) {
            MaidInnerContext.setOffsetY(head, 0);
        }
        if (ctx.mob().isPassenger()) {
            ridingPosture(ctx);
        } else if (ctx.maid().isMaidInSittingPose()) {
            sittingPosture(ctx);
        }
    }

    /** {@code MaidBaseAnimation#ridingPosture} 的骨骼部分。 */
    private static void ridingPosture(MaidInnerContext ctx) {
        AnimatedGeoBone legLeft = ctx.bone("legLeft");
        AnimatedGeoBone legRight = ctx.bone("legRight");
        if (legLeft != null) {
            legLeft.setRotationX(-1.134F);
            legLeft.setRotationZ(-0.262F);
        }
        if (legRight != null) {
            legRight.setRotationX(-1.134F);
            legRight.setRotationZ(0.262F);
        }
        // GlWrapper.translate(0, 0.3, 0) 由 MaidRenderer#setupRotations 负责
    }

    /** {@code MaidBaseAnimation#sittingPosture}。 */
    private static void sittingPosture(MaidInnerContext ctx) {
        AnimatedGeoBone armLeft = ctx.bone("armLeft");
        AnimatedGeoBone armRight = ctx.bone("armRight");
        if (armLeft != null) {
            armLeft.setRotationX(-0.798F);
            armLeft.setRotationZ(0.274F);
        }
        if (armRight != null) {
            armRight.setRotationX(-0.798F);
            armRight.setRotationZ(-0.274F);
        }
        ridingPosture(ctx);
    }

    /** {@code MaidBaseAnimation#getSitSkirtHidden}。 */
    private static void sitSkirtHidden(MaidInnerContext ctx) {
        boolean sitting = ctx.mob().isPassenger() || ctx.maid().isMaidInSittingPose();
        hide(ctx.bone("sittingHiddenSkirt"), sitting);
        hide(ctx.bone("_sittingHiddenSkirt"), !sitting);
    }

    /** {@code MaidBaseAnimation#getSitSkirtRotation}。 */
    private static void sitSkirtRotation(MaidInnerContext ctx) {
        AnimatedGeoBone skirt = ctx.bone("sittingRotationSkirt");
        if (skirt == null) {
            return;
        }
        if (ctx.mob().isPassenger() || ctx.maid().isMaidInSittingPose()) {
            skirt.setRotationX(-0.567F);
        } else {
            skirt.setRotationX(MaidInnerContext.initX(skirt));
        }
    }

    /** {@code MaidBaseAnimation#getSitSkirtRotationSwing}。 */
    private static void sitSkirtRotationSwing(MaidInnerContext ctx) {
        AnimatedGeoBone skirt = ctx.bone("sittingRotationSwingSkirt");
        if (skirt == null) {
            return;
        }
        if (ctx.mob().isPassenger() || ctx.maid().isMaidInSittingPose()) {
            skirt.setRotationX(-0.567F);
            skirt.setRotationZ(MaidInnerContext.initZ(skirt));
        } else {
            skirt.setRotationX(MaidInnerContext.initX(skirt));
            float rotationZ = (float) Math.sin(ctx.ageInTicks() * 0.05) * 0.03F;
            skirt.setRotationZ(MaidInnerContext.initZ(skirt) + rotationZ);
        }
    }

    // ================================================================== 其它部件

    /** {@code MaidBaseAnimation#getSleepDefault}。 */
    private static void sleepDefault(MaidInnerContext ctx) {
        boolean sleeping = ctx.mob().isSleeping();
        hide(ctx.bone("sleepHide"), sleeping);
        hide(ctx.bone("sleepShow"), !sleeping);
    }

    /** {@code MaidBaseAnimation#getTailDefault}。 */
    private static void tailDefault(MaidInnerContext ctx) {
        AnimatedGeoBone tail = ctx.bone("tail");
        if (tail == null) {
            return;
        }
        tail.setRotationX((float) (Math.sin(ctx.ageInTicks() * 0.2) * 0.05) + MaidInnerContext.initX(tail));
        tail.setRotationZ((float) (Math.cos(ctx.ageInTicks() * 0.2) * 0.1) + MaidInnerContext.initZ(tail));
        tail.setHidden(ctx.mob().isSleeping());
    }

    /** {@code MaidBaseAnimation#getWingDefault}。 */
    private static void wingDefault(MaidInnerContext ctx) {
        AnimatedGeoBone wingLeft = ctx.bone("wingLeft");
        AnimatedGeoBone wingRight = ctx.bone("wingRight");
        if (wingLeft != null) {
            wingLeft.setRotationY((float) (-Math.cos(ctx.ageInTicks() * 0.3) * 0.2
                                           + MaidInnerContext.initY(wingLeft)));
            wingLeft.setHidden(ctx.mob().isSleeping());
        }
        if (wingRight != null) {
            wingRight.setRotationY((float) (Math.cos(ctx.ageInTicks() * 0.3) * 0.2
                                            + MaidInnerContext.initY(wingRight)));
            wingRight.setHidden(ctx.mob().isSleeping());
        }
    }

    /** {@code MaidExtraAnimation#getHealthLessShow}。 */
    private static void healthLessShow(MaidInnerContext ctx) {
        double ratio = ctx.mob().getHealth() / ctx.mob().getMaxHealth();
        hide(ctx.bone("healthLessQuarterShow"), ratio > 0.25);
        hide(ctx.bone("healthLessHalfShow"), ratio > 0.5);
        hide(ctx.bone("healthLessThreeQuartersShow"), ratio > 0.75);
    }

    // ================================================================== 任务

    /** {@code MaidTaskAnimation} 的 {@code xxxHidden / xxxShow} 成对逻辑。 */
    private static void taskShowHide(MaidInnerContext ctx, String task, String hiddenBone, String showBone) {
        boolean isTask = task.equals(ctx.maid().getTask().getUid().getPath());
        hide(ctx.bone(hiddenBone), isTask);
        hide(ctx.bone(showBone), !isTask);
    }

    private static void taskAttack(MaidInnerContext ctx) {
        taskShowHide(ctx, "attack", "attackHidden", "attackShow");
    }

    private static void taskDanmakuAttack(MaidInnerContext ctx) {
        taskShowHide(ctx, "danmaku_attack", "danmakuAttackHidden", "danmakuAttackShow");
    }

    private static void taskFarm(MaidInnerContext ctx) {
        taskShowHide(ctx, "farm", "farmHidden", "farmShow");
    }

    private static void taskExtinguishing(MaidInnerContext ctx) {
        taskShowHide(ctx, "extinguishing", "extinguishingHidden", "extinguishingShow");
    }

    private static void taskRangedAttack(MaidInnerContext ctx) {
        taskShowHide(ctx, "ranged_attack", "rangedAttackHidden", "rangedAttackShow");
    }

    // ================================================================== 基础

    /** {@code EntityBaseAnimation#getBaseFloatDefault}。 */
    private static void baseFloatDefault(MaidInnerContext ctx) {
        float sin = (float) (Math.sin(ctx.ageInTicks() * 0.1) * 0.05);
        float cos = (float) (Math.cos(ctx.ageInTicks() * 0.1) * 0.05);
        offsetY(ctx, "sinFloat", sin);
        offsetY(ctx, "cosFloat", cos);
        offsetY(ctx, "_sinFloat", -sin);
        offsetY(ctx, "_cosFloat", -cos);
    }

    private static void offsetY(MaidInnerContext ctx, String name, float offset) {
        AnimatedGeoBone bone = ctx.bone(name);
        if (bone != null) {
            MaidInnerContext.setOffsetY(bone, offset);
        }
    }

    /** {@code EntityBaseAnimation#getBaseRotationReciprocate}。 */
    private static void baseRotationReciprocate(MaidInnerContext ctx) {
        float value = (float) (Math.cos(ctx.ageInTicks() * 0.3) * 0.2);
        AnimatedGeoBone x = ctx.bone("xReciprocate");
        if (x != null) {
            x.setRotationX(value);
        }
        AnimatedGeoBone y = ctx.bone("yReciprocate");
        if (y != null) {
            y.setRotationY(value);
        }
        AnimatedGeoBone z = ctx.bone("zReciprocate");
        if (z != null) {
            z.setRotationZ(value);
        }
    }

    // ================================================================== 特殊模型

    /** {@code SpecialAnimation#getSpecialHecatia}：按维度切换三套头发 / 地球月球标志。 */
    private static void specialHecatia(MaidInnerContext ctx) {
        int dim = ctx.mob().level.dimension() == net.minecraft.world.level.Level.OVERWORLD ? 0
                : ctx.mob().level.dimension() == net.minecraft.world.level.Level.NETHER ? 1 : 2;
        boolean earthVisible = dim == 0;
        boolean moonVisible = dim == 1;
        boolean otherVisible = dim == 2;

        showHecatiaGroup(ctx, earthVisible, "earthHair", "logoEarth", "earthTop");
        showHecatiaGroup(ctx, moonVisible, "moonHair", "logoMoon", "moonTop");
        showHecatiaGroup(ctx, otherVisible, "otherHair", "logoOther", "otherTop");

        // 戴头盔时隐藏头顶那圈标志
        boolean hasHelmet = !ctx.mob().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
        hide(ctx.bone("earthTop"), hasHelmet || !earthVisible);
        hide(ctx.bone("moonTop"), hasHelmet || !moonVisible);
        hide(ctx.bone("otherTop"), hasHelmet || !otherVisible);
    }

    private static void showHecatiaGroup(MaidInnerContext ctx, boolean visible,
                                         String hair, String logo, String top) {
        hide(ctx.bone(hair), !visible);
        hide(ctx.bone(logo), !visible);
        hide(ctx.bone(top), !visible);
    }

    /** {@code SpecialAnimation#getSpecialWakasagihime}：坐下时手臂内收。 */
    private static void specialWakasagihime(MaidInnerContext ctx) {
        if (!ctx.maid().isMaidInSittingPose()) {
            return;
        }
        AnimatedGeoBone armLeft = ctx.bone("armLeft");
        if (armLeft != null) {
            armLeft.setRotationX(-0.798F);
            armLeft.setRotationZ(0.274F);
        }
        AnimatedGeoBone armRight = ctx.bone("armRight");
        if (armRight != null) {
            armRight.setRotationX(-0.798F);
            armRight.setRotationZ(-0.274F);
        }
    }

    // ================================================================== 工具

    /** {@code ModelRendererWrapper#setHidden} 的语义：null 直接跳过。 */
    private static void hide(@Nullable AnimatedGeoBone bone, boolean hidden) {
        if (bone != null) {
            bone.setHidden(hidden);
        }
    }
}


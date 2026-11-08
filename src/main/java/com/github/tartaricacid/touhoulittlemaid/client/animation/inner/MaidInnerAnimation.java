package com.github.tartaricacid.touhoulittlemaid.client.animation.inner;

/**
 * 一条内建动画，对应上游 {@code client/animation/inner/IAnimation}。
 *
 * <p>与上游的区别只有签名：上游那条是
 * {@code setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, mob, modelMap)}
 * 八个参数，这里把公共部分收进 {@link MaidInnerContext}，
 * {@code scaleFactor} 在 1.18.2 的原版渲染链里也只用于缩放、内建动画从不读它，故省略。</p>
 */
@FunctionalInterface
public interface MaidInnerAnimation {
    /**
     * 按当前状态改写骨骼。
     *
     * <p>约定与上游一致：<b>绝对写值</b>，不累加；骨骼缺失一律跳过
     * （不同模型带的骨头差别很大，缺骨是常态）。</p>
     */
    void apply(MaidInnerContext ctx);
}


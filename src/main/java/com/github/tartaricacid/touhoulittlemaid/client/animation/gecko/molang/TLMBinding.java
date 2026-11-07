package com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.binding.ContextBinding;

/**
 * 女仆专属的 Molang 绑定，通过 {@code tlm_*} 前缀在动画里访问。
 *
 * <p>与上游保持一致，只注册女仆状态量；上游此处依赖 {@code EntityMaid} 的
 * {@code isBegging()} / {@code isMaidInSittingPose()} / {@code hasBackpack()}。</p>
 */
public class TLMBinding extends ContextBinding {
    public static final TLMBinding INSTANCE = new TLMBinding();

    private TLMBinding() {
        maidEntityVar("is_begging", ctx -> ctx.entity().isBegging());
        maidEntityVar("is_sitting", ctx -> ctx.entity().isMaidInSittingPose());
        maidEntityVar("has_backpack", ctx -> ctx.entity().hasBackpack());
    }
}


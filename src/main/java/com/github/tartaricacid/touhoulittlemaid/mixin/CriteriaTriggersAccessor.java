package com.github.tartaricacid.touhoulittlemaid.mixin;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把原版私有的 {@code CriteriaTriggers#register} 暴露出来。
 *
 * <p><b>为什么需要它：</b>自定义判据触发器（本移植版里是
 * {@code give_patchouli_book_config}）必须注册进 {@code CriteriaTriggers.CRITERIA}，
 * 否则进度 JSON 解析时会报「Unknown criteria trigger」，那条隐藏进度直接不加载。</p>
 *
 * <p>Forge 把这个方法改成了 public，所以上游可以直接
 * {@code CriteriaTriggers.register(...)}；Fabric 不修改原版可见性。Fabric API 在
 * 1.18.2 也没有提供注册判据的入口，因此这里用一个 {@code @Invoker} 访问器代替 ——
 * 不改变任何行为，只是绕开访问修饰符。</p>
 */
@Mixin(CriteriaTriggers.class)
public interface CriteriaTriggersAccessor {
    @Invoker("register")
    static <T extends CriterionTrigger<?>> T invokeRegister(T trigger) {
        throw new AssertionError("Mixin invoker was not applied: CriteriaTriggers#register");
    }
}


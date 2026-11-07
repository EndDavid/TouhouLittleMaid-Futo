package com.github.tartaricacid.touhoulittlemaid.capability;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.storage.IForeignVariableStorage;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Forge {@code Capability} 在 Fabric 侧的替身。
 *
 * <p>上游用一个 capability 在实体上挂载 {@code GeckoMaidEntity}，作用是两件事：
 * <ol>
 *     <li><b>per-entity 缓存</b>：渲染器每帧都会调用 {@code getAnimatableEntity}，
 *         如果每次都 {@code new} 一个，动画的 {@code seekTime} 永远从 0 开始，
 *         表现为「模型不播放动画，移动时像在平移」。上游靠 capability 拿到同一个实例，
 *         这里改用 {@link GeckoMaidEntity} 内部的弱引用缓存。</li>
 *     <li><b>跨模型共享的 Molang 变量存储</b>：让同一实体的多个模型看到同一份
 *         {@code variable.x}。</li>
 * </ol>
 */
public final class GeckoMaidEntityCapabilityProvider {
    private GeckoMaidEntityCapabilityProvider() {
    }

    /**
     * 取该实体已创建的动画实例共享的 Molang 变量存储，没有则返回 {@code null}
     * （等同上游 capability 缺失时的行为）。
     */
    @Nullable
    public static IForeignVariableStorage getForeignVariableStorage(Mob mob) {
        GeckoMaidEntity<?> animatable = GeckoMaidEntity.peek(mob);
        if (animatable == null) {
            return null;
        }
        return animatable.getAnimationProcessor().getPublicVariableStorage();
    }
}

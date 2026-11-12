package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 让「女仆床」在原版的睡觉判定里算作一张床。
 *
 * <h2>为什么必须打这个补丁</h2>
 * <p>原版 {@code LivingEntity#tick()} 里有这么一段：</p>
 * <pre>
 * if (this.isSleeping() &amp;&amp; !this.checkBedExists()) {
 *     this.stopSleeping();
 * }
 * </pre>
 * <p>而 {@code checkBedExists()} 的实现是 {@code getBlockState(pos).getBlock() instanceof BedBlock}。
 * 女仆床 {@link BlockMaidBed} <b>不是</b> {@code BedBlock} 的子类（床的形状、双格语义、占用标记
 * 全都自己重写了，继承 {@code BedBlock} 反而要绕开它的一堆原版行为），
 * 于是女仆刚躺下，下一 tick 就被原版判定成「床不见了」，立刻 {@code stopSleeping()}；
 * {@code MaidBedTask} 又把她按回去 —— 表现就是玩家看到的<b>反复仰卧起坐</b>。</p>
 *
 * <h2>为什么上游没这个问题</h2>
 * <p>Forge 把 {@code checkBedExists()} 连同 {@code startSleeping} 一起打了补丁，
 * 判定改成 {@code Block#isBed(state, level, pos, entity)}，默认实现是
 * {@code this instanceof BedBlock}，第三方床覆写它就能参与睡觉。
 * {@link BlockMaidBed#isMaidBed} 就是那个钩子的等价物，只是 Fabric 侧没有
 * 可以覆写的方法，只能靠 Mixin 把这段判定接回来。</p>
 *
 * <p>{@code checkBedExists} 在 1.18.2 里是 <b>private</b>，无法通过继承覆盖，
 * 所以只能注入。注入点在 HEAD 且 {@code cancellable}：
 * 是女仆床就直接返回 true，否则完全交回原版逻辑（玩家睡原版床不受影响）。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMaidBedMixin {
    @Inject(method = "checkBedExists", at = @At("HEAD"), cancellable = true)
    private void futo$checkBedExists(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        Optional<BlockPos> sleepingPos = self.getSleepingPos();
        if (sleepingPos.isPresent() && BlockMaidBed.isMaidBed(self.level.getBlockState(sleepingPos.get()))) {
            cir.setReturnValue(true);
        }
    }
}


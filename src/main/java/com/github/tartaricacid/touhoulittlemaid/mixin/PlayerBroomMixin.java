package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 骑扫帚时，潜行键是<b>下降</b>，不是<b>下车</b>。
 *
 * <h2>不加这一条会发生什么</h2>
 * <p>原版的下车判定在 {@code Player#rideTick()} 里：</p>
 * <pre>{@code
 * public void rideTick() {
 *     if (!this.level.isClientSide && this.wantsToStopRiding() && this.isPassenger()) {
 *         this.stopRiding();
 *         this.setShiftKeyDown(false);
 *         return;
 *     }
 *     ...
 * }
 * }</pre>
 * <p>而 {@code Player#wantsToStopRiding()} 的实现只有一句 {@code return isShiftKeyDown();}
 * （1.18.2 的字节码：{@code aload_0; invokevirtual isShiftKeyDown; ireturn}）。
 * 也就是说<b>按住潜行键骑任何东西都会被原版踢下来</b> —— 骑马、坐船、坐矿车都是这个规则。</p>
 *
 * <p>扫帚把「潜行」占用成了「下降」（见 {@code EntityBroom#rideControl}，
 * {@code keySneak ? vertical = -0.2f}），于是这条原版规则直接顶掉了下降：
 * 按一下左 Shift，人从扫帚上掉下去，垂直下降一次也用不上。
 * 这也是上游为什么要给扫帚单独配一个 C 键下车 —— 潜行键已经被占用了。</p>
 *
 * <h2>为什么是 Mixin 而不是别的写法</h2>
 * <ul>
 *     <li>{@code Player#wantsToStopRiding()} 是 {@code protected}，实体自己改不了别人身上的判定；</li>
 *     <li>在 {@code EntityBroom#tickRidden} 里每 tick 调 {@code player.setShiftKeyDown(false)}
 *         也不行：服务端每 tick 都会用 {@code ServerboundPlayerInputPacket} 里的真实按键
 *         （{@code ServerPlayer#setPlayerInput} → {@code setShiftKeyDown}）把它写回来，
 *         而且那样下降逻辑也就读不到潜行了；</li>
 *     <li>改 {@code EntityBroom#canRide} 没用 —— 那是上车判定，不是下车判定。</li>
 * </ul>
 *
 * <p>上游 1.20.1 用的就是这条注入（{@code mixin/PlayerMixin#tlmWantsToStopRiding}），
 * 这里逐字对齐。{@code isShiftKeyDown()} 那个前置判断其实是冗余的
 * （被注入的方法本身就是它），保留是为了与上游逐字一致、也为了让读代码的人一眼看出
 * 「只在按着潜行键时才吞掉这次下车」。</p>
 */
@Mixin(Player.class)
public class PlayerBroomMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void tlmWantsToStopRiding(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if (player.isShiftKeyDown() && player.getVehicle() instanceof EntityBroom) {
            // 返回 false：这次潜行不作为「下车」的理由，交给 C 键（DismountBroomPacket）
            cir.setReturnValue(false);
        }
    }
}


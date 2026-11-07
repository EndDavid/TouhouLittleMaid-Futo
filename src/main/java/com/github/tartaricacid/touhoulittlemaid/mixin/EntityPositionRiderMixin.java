package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让「被抱着的女仆」挂在玩家身前 —— <b>逐字照抄上游</b>
 * {@code mixin/EntityMixin}（1.18.2 与 1.20.1 两个上游完全一致）。
 *
 * <p><b>为什么必须是这个注入点：</b>{@link Entity#rideTick()} 的最后一步是
 * {@code getVehicle().positionRider(this)}，而 {@code rideTick()} 由
 * {@code Level#tickPassenger(载具, 乘客)} 调用。<b>乘客的坐标不由服务端同步</b>
 * （{@code ServerEntity} 只同步「谁骑谁」这个关系），客户端是靠自己调
 * {@code positionRider} 算出坐标的。所以只要改写这个方法，两端算出的结果天然一致，
 * 不需要任何额外网络包。</p>
 *
 * <p>之前这个文件压根没建（配置里也没注册），于是走了 vanilla 的
 * {@code positionRider} —— 把女仆摆到玩家脚下，而且客户端乘客那条 tick 路径
 * 一旦没走到，画面就停在抱起那一刻，放下后才被位置包拽回来（瞬移）。</p>
 */
@Mixin(Entity.class)
public class EntityPositionRiderMixin {
    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"), cancellable = true)
    protected void futo$positionMaidRider(Entity passenger, Entity.MoveFunction callback, CallbackInfo ci) {
        if (passenger instanceof EntityMaid maid && maid.getVehicle() instanceof Player player) {
            Vec3 position = player.position();
            // yBodyRot 是顺时针角度，Vec3#yRot 用逆时针，所以取负
            float radians = (float) -Math.toRadians(player.yBodyRot);
            Vec3 offset = position.add(new Vec3(0, 0, 0.75).yRot(radians));
            double yOffset = 0.15D;
            if (player.isDescending()) {
                yOffset = yOffset - 0.3D;
            }
            callback.accept(passenger, offset.x(), offset.y() + yOffset, offset.z());
            ci.cancel();
        }
    }
}


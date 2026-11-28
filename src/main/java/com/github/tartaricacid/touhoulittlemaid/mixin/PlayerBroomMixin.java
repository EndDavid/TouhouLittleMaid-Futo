package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 骑扫帚时，潜行键是<b>下降</b>，不是<b>下车</b>。
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


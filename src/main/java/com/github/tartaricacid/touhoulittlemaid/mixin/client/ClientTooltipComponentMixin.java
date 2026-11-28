package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.client.tooltip.ClientMaidTooltip;
import com.github.tartaricacid.touhoulittlemaid.inventory.tooltip.ItemMaidTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把自定义的 {@link TooltipComponent} 接到原版的工厂方法上。
 */
@Mixin(ClientTooltipComponent.class)
public interface ClientTooltipComponentMixin {
    @Inject(
            method = "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void futo$createMaidTooltip(TooltipComponent component,
                                               CallbackInfoReturnable<ClientTooltipComponent> cir) {
        if (component instanceof ItemMaidTooltip maidTooltip) {
            cir.setReturnValue(new ClientMaidTooltip(maidTooltip));
        }
    }
}


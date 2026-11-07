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
 *
 * <h2>原版到底缺什么</h2>
 *
 * <p>「{@code Item#getTooltipImage} 返回 {@code TooltipComponent}」这一半 1.18.2 是有的；
 * 缺的是<b>把它换成客户端实现的那张注册表</b>。反编译原版可以看到
 * {@code ClientTooltipComponent#create(TooltipComponent)} <b>就是一个静态方法 + 硬编码 if</b>：</p>
 *
 * <pre>
 * public static ClientTooltipComponent create(TooltipComponent component) {
 *     if (component instanceof BundleTooltip bundle) {
 *         return new ClientBundleTooltip(bundle);
 *     }
 *     throw new IllegalArgumentException("Unknown TooltipComponent");
 * }
 * </pre>
 *
 * <p>整张「注册表」就是这一个 {@code if}。Forge 把它改造成了
 * {@code MinecraftForgeClient#registerTooltipComponentFactory}（一个真正的注册表），
 * 所以上游只要在客户端初始化时注册一次就行。Fabric API 没有提供等价物 ——
 * <b>但这件事本身不需要 API</b>：方法是静态且可注入的，
 * 在 HEAD 处判一下类型、给出返回值并 cancel 即可，等价于「往那张表里加一行」。</p>
 *
 * <h2>为什么不是「替换成另一张表」</h2>
 *
 * <p>也可以整个重写这个方法，但那等于把原版对 {@code BundleTooltip} 的处理也复制一份；
 * 一旦原版后续版本新增了别的组件，就会漏掉。HEAD 注入 + 只处理自己的类型，
 * 未命中时直接放行给原逻辑，最稳。</p>
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


package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.client.input.DismountBroomKey;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把「骑上扫帚」时原版弹出的那句提示里的按键换成下扫帚键 ——
 * 逐条对齐上游 1.20.1 的 {@code mixin/client/ClientPacketListenerMixin}。
 *
 * <h2>为什么要动这个</h2>
 * <p>原版 {@code ClientPacketListener#handleSetEntityPassengersPacket} 里，玩家刚成为乘客时会
 * {@code gui.setOverlayMessage(new TranslatableComponent("mount.onboard", options.keyShift.getTranslatedKeyMessage()), false)}。
 * 那句 {@code mount.onboard} 就是屏幕上居中显示的 <b>「Press Left Shift to Dismount」</b>。</p>
 *
 * <p>对扫帚来说这条提示是<b>错的</b>：扫帚上潜行键被占用成了「下降」
 * （{@code PlayerBroomMixin} 让潜行不再触发下车，{@code EntityBroom#rideControl} 拿它当下降键），
 * 真正能下车的是 C（{@link DismountBroomKey}）。玩家照着
 * 「按左 Shift 下车」去按，只会原地下降，然后发现下不来。</p>
 *
 * <p>上游的改法就是整段接管这个方法：原版逻辑<b>逐句重写一遍</b>，只把
 * {@code options.keyShift} 换成 {@code DismountBroomKey} 那个键。</p>
 *
 * <h2>与上游的差异（都是 1.18.2 的框架差异）</h2>
 * <ul>
 *     <li>1.18.2 没有 {@code Component.translatable(...)}（1.19+ 才有），用
 *         {@code new TranslatableComponent(...)} —— 与原版 1.18.2 自己的写法一致。</li>
 *     <li>1.18.2 的 {@code Minecraft} 没有 {@code getNarrator()}（旁白改由
 *         {@code NarratorChatListener} 走聊天监听器），上游那句
 *         {@code getNarrator().sayNow(...)} 无法照搬，故省略。</li>
 *     <li>原版里「若是船则把玩家朝向对齐船头」那段与扫帚无关，不复制。</li>
 * </ul>
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private ClientLevel level;

    @Inject(method = "handleSetEntityPassengersPacket", at = @At("HEAD"), cancellable = true)
    private void futo$handleSetEntityPassengersPacket(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
        // 与注入点后方那句原版代码等价：确保在主线程上跑，否则后面的 level/实体操作都不安全
        PacketUtils.ensureRunningOnSameThread(packet, (ClientPacketListener) (Object) this, this.minecraft);
        Entity vehicle = this.level.getEntity(packet.getVehicle());
        LocalPlayer player = this.minecraft.player;
        KeyMapping dismountKey = DismountBroomKey.getDismountKey();
        // 不是扫帚（或状态还没齐）就交回原版：那才是「按潜行键下车」
        if (!(vehicle instanceof EntityBroom) || player == null || dismountKey == null) {
            return;
        }
        boolean playerWasPassenger = vehicle.hasIndirectPassenger(player);
        vehicle.ejectPassengers();
        for (int passengerId : packet.getPassengers()) {
            Entity passenger = this.level.getEntity(passengerId);
            if (passenger == null) {
                continue;
            }
            passenger.startRiding(vehicle, true);
            if (passenger != player || playerWasPassenger) {
                continue;
            }
            Component mountMessage = new TranslatableComponent("mount.onboard", dismountKey.getTranslatedKeyMessage());
            this.minecraft.gui.setOverlayMessage(mountMessage, false);
        }
        // 上面已经完整复刻了原版行为，不能再让原版跑一遍（否则提示又会被 keyShift 覆盖回去）
        ci.cancel();
    }
}


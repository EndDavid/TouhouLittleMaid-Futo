package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家抱着女仆时，把双臂摆成「环抱」姿势。
 *
 * <p><b>照抄上游</b> {@code mixin/client/HumanoidModelMixin}（1.18.2 与 1.20.1 一致）。
 * 反编译发布 jar 得到的分支是：</p>
 *
 * <pre>
 * if (entity instanceof Player player &amp;&amp; player.getFirstPassenger() instanceof EntityMaid) {
 *     leftArm.xRot  = -65°;  leftArm.yRot  = +10°;
 *     rightArm.xRot = -65°;  rightArm.yRot = -10°;
 * }
 * </pre>
 *
 * <p>也就是两臂前举 65°、各向外张开 10°（<b>绕 Y 轴</b>张开，不是绕 Z 轴）——正好托住怀里的女仆。
 * 少了这一步，女仆会像是「悬浮在胸前」而不是被抱着。</p>
 *
 * <p><b>为什么注入在 TAIL：</b>{@code setupAnim} 里原版会按 {@code ArmPose} 重写
 * {@code xRot}/{@code yRot}（拉弓、挥击、使用物品……），必须在它算完之后覆盖，
 * 否则玩家一挥手我们的姿势就被冲掉了。</p>
 *
 * <p><b>为什么判定不照抄上游的 {@code getFirstPassenger()}：</b>上游确实只判
 * {@code player.getFirstPassenger() instanceof EntityMaid}。但乘客关系是<b>两端各自复现</b>的
 * （服务端 {@code startRiding}，客户端再靠 {@code ClientboundSetPassengersPacket} 复现一次），
 * 只要客户端没复现成功，这条判据就永远为假 —— 表现正是「女仆是被抱着的，
 * 玩家手臂却还在下垂摆动」。所以这里改用
 * {@link EntityMaid#isCarryingMaid(net.minecraft.world.entity.player.Player)}：
 * 先看乘客关系，再退一步按同步字段 {@code carrierId} 找。</p>
 */
@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {
    @Shadow
    @Final
    public ModelPart leftArm;
    @Shadow
    @Final
    public ModelPart rightArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void futo$carryMaidArmPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                       float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        // 判定交给 EntityMaid#isCarryingMaid：
        // 上游只写 getFirstPassenger()，但乘客关系是两端各自复现的，
        // 客户端一旦没复现成功（我们的抱持并不强依赖乘客关系），
        // 这里就永远不成立 —— 表现正是「抱着女仆，玩家手臂还在下垂摆动」。
        if (entity instanceof Player player && EntityMaid.isCarryingMaid(player)) {
            this.leftArm.xRot = (float) Math.toRadians(-65.0D);
            this.leftArm.yRot = (float) Math.toRadians(10.0D);
            this.rightArm.xRot = (float) Math.toRadians(-65.0D);
            this.rightArm.yRot = (float) Math.toRadians(-10.0D);
        }
    }
}

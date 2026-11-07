package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 让「女仆击杀」算作「主人击杀」。
 *
 * <p><b>为什么必须有这一段：</b>原版 {@code LivingEntity#dropExperience()} 的条件是</p>
 * <pre>
 * if (!isAlwaysExperienceDropper()
 *     &amp;&amp; lastHurtByPlayerTime &lt;= 0
 *     &amp;&amp; shouldDropExperience()) return;      // 不掉经验
 * </pre>
 * <p>而 {@code lastHurtByPlayerTime} 只在 {@code LivingEntity#hurt} 里、且来源实体是
 * <b>玩家</b>或「已驯服的狼」时才被写入。女仆既不是玩家也不是狼，于是被女仆打死的怪物
 * <b>一个经验球都不掉</b> —— 这就是玩家反馈「女仆杀怪不给经验」的根因。</p>
 *
 * <p>这里在 {@code hurt} 的最前面补上狼的那条分支：来源是女仆且已认主时，把
 * {@code lastHurtByPlayer} 记到主人头上。这样原版后续流程会<b>原样</b>执行：</p>
 * <ul>
 *     <li>经验数量走 {@code getExperienceReward(lastHurtByPlayer)}，不用我们瞎猜；</li>
 *     <li>{@code looting} 按主人的附魔等级计算；</li>
 *     <li>战利品表里的 {@code killed_by_player} 条件也会照常成立。</li>
 * </ul>
 *
 * <p><b>为什么不自己 spawn 经验球：</b>那需要读取 {@code getExperienceReward}，
 * 它在 1.18.2 是 protected，反射又因为生产环境类名被重映射到 intermediary 而不可靠。
 * 走原版的「宠物击杀」通道是唯一既准确又稳的做法。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityKillCreditMixin {
    @Inject(method = "hurt", at = @At("HEAD"))
    private void tlm$maidKillCredit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof EntityMaid maid)) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        // 女仆之间互殴不算击杀（攻击任务本来就会拦，这里再兜一层）
        if (self instanceof IMaid) {
            return;
        }
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            return;
        }
        Player owner = maid.level.getPlayerByUUID(ownerId);
        if (owner != null) {
            self.setLastHurtByPlayer(owner);
        }
    }
}


package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;

import java.util.function.Predicate;

/**
 * 御币，移植自上游 {@code item/ItemHakureiGohei}。
 *
 * <p>{@code hakurei_gohei} 与 {@code sanae_gohei} 都是这个类的实例（与上游一致），
 * 差异只体现在物品模型 / 贴图上 —— 两者的资源本移植版已经具备。</p>
 *
 * <p>女仆主手持御币、且工作模式为 {@code danmaku_attack} 时会发射弹幕
 * （见 {@code TaskDanmakuAttack}）。玩家自己使用只是一个普通的近战武器：
 * 上游的「右键搭建祭坛」依赖尚未移植的多方块结构系统，因此这里走
 * {@link ProjectileWeaponItem#useOn} 的默认行为。</p>
 */
public class ItemHakureiGohei extends ProjectileWeaponItem {
    /**
     * 主手攻击力 +4、攻速 -2，与上游一致。
     */
    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public ItemHakureiGohei() {
        super(new FabricItemSettings().tab(InitItems.MAIN_TAB).durability(300));
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE_UUID, "Tool modifier", 4.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED_UUID, "Tool modifier", -2.0D, AttributeModifier.Operation.ADDITION));
        this.defaultModifiers = builder.build();
    }

    public static boolean isGohei(ItemStack stack) {
        return stack.getItem() instanceof ItemHakureiGohei;
    }

    /**
     * 御币不消耗弹药，因此接受任何物品（弹幕是「凭空」生成的）。
     */
    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return Predicates.alwaysTrue();
    }

    @Override
    public int getDefaultProjectileRange() {
        return 15;
    }

    /**
     * 拉弓时长上限（500 tick）。{@code TaskDanmakuAttack} 复用了
     * {@code MaidShootTargetTask}，它按「使用物品的 tick 数」决定蓄力，
     * 因此这个值要足够大，不能像弓那样 72000（原版弓是 72000，这里上游给的是 500）。
     */
    @Override
    public int getUseDuration(ItemStack stack) {
        return 500;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(2, attacker, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.defaultModifiers : super.getDefaultAttributeModifiers(slot);
    }
}


package com.github.tartaricacid.touhoulittlemaid.entity.projectile;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 弹幕伤害类型，移植自上游 {@code entity/projectile/EntityDamageSourceDanmaku}。
 *
 * <p>三个标记与上游一致：</p>
 * <ul>
 *     <li>{@code bypassArmor()} —— 弹幕无视护甲；</li>
 *     <li>{@code setMagic()} —— 归为魔法伤害（女仆的护甲保护不生效，也不会被弹反）；</li>
 *     <li>{@code setProjectile()} —— 死亡消息用「被射杀」的说法。</li>
 * </ul>
 *
 * <p>死亡消息 key 是 {@code death.touhou_little_maid.attack.danmaku.1..3}，
 * 三句随机，语言文件里本来就有。</p>
 */
public class EntityDamageSourceDanmaku extends IndirectEntityDamageSource {
    private static final String DAMAGE_TYPE = "magic";

    public EntityDamageSourceDanmaku(Entity source, @Nullable Entity indirectEntity) {
        super(DAMAGE_TYPE, source, indirectEntity);
        this.bypassArmor();
        this.setMagic();
        this.setProjectile();
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity victim) {
        if (this.getEntity() != null) {
            int index = victim.level.random.nextInt(3) + 1;
            return new TranslatableComponent(
                    String.format("death.touhou_little_maid.attack.danmaku.%d", index),
                    victim.getDisplayName(), this.getEntity().getDisplayName());
        }
        return TextComponent.EMPTY;
    }
}


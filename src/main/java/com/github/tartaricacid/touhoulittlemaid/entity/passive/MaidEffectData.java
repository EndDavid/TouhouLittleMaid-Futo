package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 一条「给 GUI 看」的状态效果快照，对应上游 {@code SendEffectMessage.EffectData}。
 *
 * <p><b>为什么不直接把 {@link MobEffectInstance} 同步过去：</b></p>
 *
 * <p>{@code MobEffectInstance} 里真正需要同步的只有四个标量，但它本身还挂着
 * {@code MobEffect}（注册表对象）、可见性 / 图标 / 淡入淡出等一堆标志位，
 * 而且它的 {@code update()} 会按 tick 递减时长。GUI 只需要「名字 + 等级 + 剩余时间 +
 * 正负面」这四样，所以上游只发这四个值，传输体积最小，也不会因为两端 tick 不同步而对不上。</p>
 *
 * <p><b>为什么放在这里而不是嵌在网络包里：</b>上游是嵌在 {@code SendEffectMessage}
 * 里的，但那样 {@code EntityMaid} 就要反过来 import 网络包，两个类形成引用环。
 * 单独抽一个数据类，{@code EntityMaid} 与网络包都只依赖它，方向是单向的。</p>
 *
 * @param descriptionId 效果的翻译键（{@code effect.minecraft.speed}）
 * @param amplifier     <b>0 起算</b>的等级，与 {@link MobEffectInstance#getAmplifier()} 一致
 * @param duration      剩余 tick，{@code -1} 表示无限
 * @param category      {@code MobEffectCategory} 的 ordinal（0 正面 / 1 负面 / 2 中立），
 *                      只用来决定文字颜色，所以不需要发注册名
 */
public record MaidEffectData(String descriptionId, int amplifier, int duration, int category) {
    /**
     * 从服务端的实际效果生成快照。
     */
    public static MaidEffectData of(MobEffectInstance effect) {
        return new MaidEffectData(effect.getDescriptionId(), effect.getAmplifier(), effect.getDuration(),
                effect.getEffect().getCategory().ordinal());
    }

    public static MaidEffectData fromBuf(FriendlyByteBuf buf) {
        return new MaidEffectData(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeUtf(this.descriptionId);
        buf.writeInt(this.amplifier);
        buf.writeInt(this.duration);
        buf.writeInt(this.category);
    }
}


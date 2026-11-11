package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把原版私有的 {@code LivingEntity#breakItem} 暴露出来。
 *
 * <p><b>为什么需要它：</b>装备耐久归零时，原版会调 {@code breakItem} 播放碎裂音并甩出碎屑粒子。
 * 上游 TLM 在女仆饰品碎掉时直接调它（{@code ((LivingEntity) e).breakItem(message.item)}）——
 * Forge 用 AccessTransformer 把这个方法改成了 public，所以上游能这么写；
 * Fabric 不修改原版可见性，1.18.2 的 Fabric API 也没有对应入口。</p>
 *
 * <p><b>两处细节，都是踩过的坑：</b></p>
 * <ol>
 *     <li><b>必须是实例 invoker，不能写成 {@code static}。</b>
 *         {@code breakItem} 是实例方法；如果写成
 *         {@code static void invokeBreakItem(LivingEntity, ItemStack)}，
 *         Mixin 会去类里找一个<b>静态</b>的
 *         {@code breakItem(LivingEntity, ItemStack)V}，找不到就直接
 *         {@code InvalidAccessorException} 让游戏起不来。
 *         （{@code CriteriaTriggersAccessor} 之所以能写成 static，是因为
 *         {@code CriteriaTriggers#register} 本身就是静态方法。）</li>
 *     <li><b>必须放在 {@code client} 段。</b>{@code breakItem} 带
 *         {@code @Environment(EnvType.CLIENT)}，专用服务端的 merged jar 里根本没有它，
 *         放在公共段会在服务端启动时 apply 失败。</li>
 * </ol>
 *
 * <p>方法名带 {@code futo$} 前缀：invoker 会在目标类里<b>生成</b>同名方法，
 * 若直接叫 {@code breakItem} 就会和原版那个方法撞名。</p>
 */
@Mixin(LivingEntity.class)
public interface LivingEntityBreakItemAccessor {
    @Invoker("breakItem")
    void futo$breakItem(ItemStack stack);
}


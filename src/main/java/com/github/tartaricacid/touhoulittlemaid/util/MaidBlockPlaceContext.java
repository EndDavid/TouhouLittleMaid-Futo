package com.github.tartaricacid.touhoulittlemaid.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 让「非玩家实体」也能构造 {@link BlockPlaceContext} 的小工具。
 *
 * <p>1.18.2 里 {@code BlockPlaceContext(Level, Player, InteractionHand, ItemStack, BlockHitResult)}
 * 是 <b>protected</b>（只给子类用），而女仆并不是玩家，无法走
 * {@code BlockPlaceContext(Player, ...)} 那个 public 构造器。
 * 上游是 Forge 环境，Agent 直接在自己的包里调用没问题；Fabric 侧则必须继承。
 * 这里用最薄的子类把 protected 构造器暴露出来，行为与原构造器完全一致。</p>
 */
public class MaidBlockPlaceContext extends BlockPlaceContext {
    public MaidBlockPlaceContext(Level level, @Nullable Player player, InteractionHand hand,
                                 ItemStack stack, BlockHitResult hitResult) {
        super(level, player, hand, stack, hitResult);
    }
}


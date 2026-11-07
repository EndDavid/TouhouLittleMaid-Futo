package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IFeedTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFeedOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 喂食主人模式，移植自上游 {@code entity/task/TaskFeedOwner}。
 */
public class TaskFeedOwner implements IFeedTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "feed");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.COOKED_BEEF.getDefaultInstance();
    }

    @Override
    public boolean isFood(ItemStack stack, Player owner) {
        if (stack.getItem() == Items.MILK_BUCKET) {
            for (MobEffectInstance effect : owner.getActiveEffects()) {
                // 1.18.2 的 MobEffectInstance 没有 isCurativeItem（1.19 才有），而原版牛奶本身就是
            // 「无条件解除全部效果」，所以这里只需要判断「有害 + 剩余时间够长」
            if (this.isHarmfulEffect(effect) && effect.getDuration() > 60) {
                    return true;
                }
            }
            return false;
        }
        FoodProperties food = stack.getItem().getFoodProperties();
        if (food != null) {
            return food.getEffects().isEmpty() ||
                   food.getEffects().stream().noneMatch(pair -> this.isHarmfulEffect(pair.getFirst()));
        }
        return false;
    }

    @Override
    public Priority getPriority(ItemStack stack, Player owner) {
        if (stack.is(Items.MILK_BUCKET)) {
            return Priority.HIGH;
        }

        // 蜂蜜瓶可以清除中毒效果，所以当玩家拥有中毒效果时，应当优先使用
        if (stack.is(Items.HONEY_BOTTLE) && owner.hasEffect(MobEffects.POISON)) {
            return Priority.HIGH;
        }

        if (stack.is(Items.GOLDEN_APPLE)) {
            if (owner.getHealth() * 2 < owner.getMaxHealth()) {
                return Priority.HIGH;
            } else {
                return Priority.LOWEST;
            }
        }

        FoodProperties food = stack.getItem().getFoodProperties();
        if (food != null) {
            int heal = food.getNutrition();
            int hunger = 20 - owner.getFoodData().getFoodLevel();
            if (heal == hunger) {
                return Priority.HIGH;
            } else if (heal > hunger) {
                return Priority.LOWEST;
            } else {
                return Priority.LOW;
            }
        }

        return Priority.NONE;
    }

    @Override
    public ItemStack feed(ItemStack stack, Player owner) {
        if (stack.getUseAnimation() == UseAnim.DRINK) {
            owner.level.playSound(null, owner, stack.getDrinkingSound(), SoundSource.NEUTRAL,
                    0.5f, owner.level.getRandom().nextFloat() * 0.1f + 0.9f);
        }
        return stack.getItem().finishUsingItem(stack, owner.level, owner);
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_FEED, 0.3f);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return Lists.newArrayList(Pair.of(5, new MaidFeedOwnerTask(this, 2, 0.6f)));
    }

    private boolean isHarmfulEffect(MobEffectInstance effect) {
        return effect.getEffect().getCategory() == MobEffectCategory.HARMFUL;
    }
}


package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityPicnicMat;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 「在野餐垫上吃东西」—— 坐在垫子上、从中心格取一份食物吃下，移植自上游
 * {@code entity/ai/brain/task/MaidHomeMealTask}。
 *
 * <p>它挂在 <b>RIDE_IDLE</b>（坐着时的闲置）活动里、权重 5：只有先由
 * {@link MaidFindHomeMealTask} 把女仆坐上垫子，这个任务才可能触发。
 * 触发条件是「骑着的那个 {@code EntitySit} 的娱乐类型是 {@code OnHomeMeal}」，
 * 再顺着 {@code EntitySit#getAssociatedBlockPos} 找回野餐垫中心格。</p>
 *
 * <h2>与 {@code MaidWorkMealTask} 的差别</h2>
 * <ul>
 *     <li>食物来源是<b>野餐垫</b>而不是女仆自己的背包；</li>
 *     <li>一次只吃<b>一个</b>（{@code extractItem(slot, 1)}），而工作餐是整组换到手上；</li>
 *     <li>会触发进度判据 {@code MAID_PICNIC_EAT}。</li>
 * </ul>
 *
 * <h2>与上游的差异</h2>
 * <p>上游在「两只手都满」「垫子上没吃的」两种情况会弹聊天气泡
 * （{@code ChatBubbleManger#addInnerChatText}）。本移植版<b>没有移植聊天气泡层</b>，
 * 所以这里只是安静地放弃这一轮 —— 行为（不吃饭、不消耗食物）完全一致，
 * 少的只是那一句画面提示。</p>
 */
public class MaidHomeMealTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 50;

    @Nullable
    private TileEntityPicnicMat tmpPicnicMat = null;

    public MaidHomeMealTask() {
        super(ImmutableMap.of());
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel serverLevel, EntityMaid maid) {
        if (!super.checkExtraStartConditions(serverLevel, maid)) {
            return false;
        }
        if (!maid.getFavorabilityManager().canAdd(Type.HOME_MEAL.getTypeName())) {
            return false;
        }
        if (maid.getVehicle() instanceof EntitySit sit && sit.getJoyType().equals(Type.ON_HOME_MEAL.getTypeName())) {
            BlockPos blockPos = sit.getAssociatedBlockPos();
            if (serverLevel.getBlockEntity(blockPos) instanceof TileEntityPicnicMat picnicMat) {
                this.tmpPicnicMat = picnicMat;
                return true;
            }
        }
        return false;
    }

    @Override
    protected void start(ServerLevel serverLevel, EntityMaid maid, long gameTime) {
        if (this.tmpPicnicMat == null) {
            return;
        }
        List<IMaidMeal> maidMeals = MaidMealManager.getMaidMeals(MaidMealType.HOME_MEAL);

        // 挑一只手：手上没有空位就抢副手（与工作餐同一条规则）
        InteractionHand eatHand = InteractionHand.OFF_HAND;
        for (InteractionHand hand : InteractionHand.values()) {
            if (maid.getItemInHand(hand).isEmpty()) {
                eatHand = hand;
                break;
            }
        }

        // 先把手上原有的东西试着塞进背包（simulate）：塞不下就这一轮作罢，
        // 否则取出来的食物会把手上的东西直接覆盖掉、凭空消失。
        ItemStack itemInHand = maid.getItemInHand(eatHand);
        RangedWrapper availableInv = maid.getAvailableBackpackInv();
        ItemStack handItemCopy = itemInHand.copy();
        ItemStack leftoverStack = ItemHandlerHelper.insertItemStacked(availableInv, handItemCopy, true);
        if (!leftoverStack.isEmpty()) {
            return;
        }

        // 扫一遍垫子上的 9 格，记下「能吃」的槽位
        ItemStackHandler handler = this.tmpPicnicMat.getHandler();
        IntList candidateFood = new IntArrayList();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (IMaidMeal maidMeal : maidMeals) {
                if (maidMeal.canMaidEat(maid, stack, eatHand)) {
                    candidateFood.add(i);
                    break;
                }
            }
        }
        int size = candidateFood.size();
        if (size == 0) {
            return;
        }

        // 随机挑一个能吃的位置（上游原样：跳过随机个数再取第一个）
        int skipCount = maid.getRandom().nextInt(size);
        InteractionHand hand = eatHand;
        candidateFood.intStream().skip(skipCount).findFirst().ifPresent(slotIndex -> {
            ItemStack outputStack = handler.extractItem(slotIndex, 1, false);
            this.tmpPicnicMat.refresh();
            maid.setItemInHand(hand, outputStack);
            ItemStack refreshItemInHand = maid.getItemInHand(hand);
            for (IMaidMeal maidMeal : maidMeals) {
                if (maidMeal.canMaidEat(maid, refreshItemInHand, hand)) {
                    // 原本手上的东西先存进隐藏栏，吃完（completeUsingItem）会还回来
                    maid.memoryHandItemStack(handItemCopy);
                    maidMeal.onMaidEat(maid, refreshItemInHand, hand);
                    if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                        InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.MAID_PICNIC_EAT);
                    }
                    return;
                }
            }
        });
    }

    @Override
    protected void stop(ServerLevel serverLevel, EntityMaid maid, long gameTime) {
        // 缓存的方块实体必须清掉：区块卸载后它会变成一个悬空引用
        this.tmpPicnicMat = null;
    }
}


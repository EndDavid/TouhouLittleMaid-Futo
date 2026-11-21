package com.github.tartaricacid.touhoulittlemaid.loot;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitLootFunction;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.NotNull;
/**
 * 把「这个魂符的初始主人」写成捡到它的那个玩家，移植自上游
 * {@code loot/SetInitMaidOwnerFunction}。
 *
 * <p>为什么需要它：首次进服送的魂符类型是 {@code smart_slab_init}，
 * 使用时会现场生成一只全新的女仆并自动认主。但如果在没锁主人的情况下丢出去，
 * 别的玩家捡到就能用它驯出一只「属于自己」的女仆 —— 上游的做法是在发放的那一刻
 * 把领取者的 UUID 写进 {@code InitMaidOwner}，之后 {@code useOn} 里做 UUID 比对。</p>
 *
 * <p>1.18.2 的 {@code LootItemConditionalFunction} 与上游同名同形，
 * 唯一要做的是自己把 {@link LootItemFunctionType} 注册进
 * {@code Registry.LOOT_FUNCTION_TYPE}（见 {@link InitLootFunction}）。</p>
 */
public class SetInitMaidOwnerFunction extends LootItemConditionalFunction {
    protected SetInitMaidOwnerFunction(LootItemCondition[] predicates) {
        super(predicates);
    }
    @Override
    protected @NotNull ItemStack run(@NotNull ItemStack stack, @NotNull LootContext context) {
        if (stack.is(InitItems.SMART_SLAB_INIT)) {
            Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
            if (entity instanceof Player player) {
                ItemSmartSlab.setInitMaidOwner(stack, player.getUUID());
            }
        }
        return stack;
    }
    @Override
    public @NotNull LootItemFunctionType getType() {
        return InitLootFunction.SET_INIT_MAID_OWNER;
    }
    public static class Serializer extends LootItemConditionalFunction.Serializer<SetInitMaidOwnerFunction> {
        @Override
        public @NotNull SetInitMaidOwnerFunction deserialize(@NotNull JsonObject json,
                                                             @NotNull JsonDeserializationContext context,
                                                             LootItemCondition[] predicates) {
            return new SetInitMaidOwnerFunction(predicates);
        }
    }
}

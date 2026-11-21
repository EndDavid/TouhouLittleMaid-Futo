package com.github.tartaricacid.touhoulittlemaid.init;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.loot.SetInitMaidOwnerFunction;
import net.minecraft.core.Registry;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
/**
 * 战利品函数注册表，对应上游 {@code init/InitLootModifier} 里与魂符相关的那一条。
 *
 * <p>上游用的是 Forge 的 {@code DeferredRegister<LootItemFunctionType>}；
 * 1.18.2 原版的 {@code Registry.LOOT_FUNCTION_TYPE} 是普通注册表，
 * 直接 {@code Registry#register} 即可，不需要 accessor。</p>
 *
 * <p>只有一条：{@code touhou_little_maid:set_init_maid_owner} ——
 * 发放魂符时把领取者的 UUID 写进物品，防止转手后别人拿去用
 * （见 {@link SetInitMaidOwnerFunction}）。</p>
 */
public final class InitLootFunction {
    public static final LootItemFunctionType SET_INIT_MAID_OWNER = Registry.register(
            Registry.LOOT_FUNCTION_TYPE,
            TouhouLittleMaid.rl("set_init_maid_owner"),
            new LootItemFunctionType(new SetInitMaidOwnerFunction.Serializer()));
    private InitLootFunction() {
    }
    public static void init() {
        // 静态字段即完成注册
    }
}

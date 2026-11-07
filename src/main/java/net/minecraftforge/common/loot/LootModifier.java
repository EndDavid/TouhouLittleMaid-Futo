package net.minecraftforge.common.loot;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/**
 * Futo 兼容层：Forge 的全局战利品修改器基类。
 */
public abstract class LootModifier {
    protected final LootItemCondition[] conditions;
    private final java.util.function.Supplier<LootItemCondition[]> conditionsSupplier;

    protected LootModifier(LootItemCondition[] conditions) {
        this.conditions = conditions;
        this.conditionsSupplier = () -> conditions;
    }

    protected boolean doApply(LootContext context) {
        return true;
    }

    public abstract List<ItemStack> apply(List<ItemStack> generatedLoot, LootContext context);

    public LootItemCondition[] getConditions() {
        return conditions;
    }
}


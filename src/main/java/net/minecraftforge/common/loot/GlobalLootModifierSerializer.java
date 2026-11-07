package net.minecraftforge.common.loot;

import com.google.gson.JsonObject;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Futo 兼容层：Forge 全局战利品修改器序列化器的公共基类。
 */
public abstract class GlobalLootModifierSerializer<T extends LootModifier> {
    public abstract T read(com.google.gson.JsonObject object, LootItemCondition[] conditions);

    public abstract JsonObject write(T instance);

    public abstract net.minecraft.resources.ResourceLocation getRegistryName();
}


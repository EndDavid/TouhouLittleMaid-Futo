package net.minecraftforge.registries;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.tags.ITagManager;

import java.util.Collection;
import java.util.Set;

/**
 * Futo 兼容层：Forge 的注册表视图。
 * 既包装原版 {@code Registry}，也支持 Forge 独有注册表（如容器、数据序列化器）。
 */
public interface IForgeRegistry<T> {
    ResourceLocation getKey(T value);

    T getValue(ResourceLocation key);

    boolean containsKey(ResourceLocation key);

    Set<ResourceLocation> getKeys();

    Collection<T> getValues();

    ITagManager<T> tags();
}


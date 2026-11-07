package net.minecraftforge.registries.tags;

import net.minecraft.tags.TagKey;

import java.util.Collection;

public interface ITagManager<T> {
    ITag<T> getTag(TagKey<T> key);

    boolean isKnownTagName(TagKey<T> key);

    TagKey<T> createTagKey(net.minecraft.resources.ResourceLocation id);

    TagKey<T> createOptionalTagKey(net.minecraft.resources.ResourceLocation id, Collection<? extends T> defaults);
}


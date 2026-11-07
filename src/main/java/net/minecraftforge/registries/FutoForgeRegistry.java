package net.minecraftforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 两种实现合一：{@code vanilla != null} 时委托原版注册表，否则退化为 Futo 自建注册表。
 */
public class FutoForgeRegistry<T> implements IForgeRegistry<T> {
    private final Registry<T> vanilla;
    private final Map<ResourceLocation, T> own = new LinkedHashMap<>();
    private final java.util.function.Function<ResourceLocation, TagKey<T>> tagKeyFactory;
    private final SimpleTagManager<T> tagManager = new SimpleTagManager<>(this);

    public FutoForgeRegistry(Registry<T> vanilla, java.util.function.Function<ResourceLocation, TagKey<T>> tagKeyFactory) {
        this.vanilla = vanilla;
        this.tagKeyFactory = tagKeyFactory;
    }

    public Registry<T> vanilla() {
        return vanilla;
    }

    public boolean isOwnRegistry() {
        return vanilla == null;
    }

    public T put(ResourceLocation id, T value) {
        return own.put(id, value);
    }

    @Override
    public ResourceLocation getKey(T value) {
        if (vanilla != null) {
            return vanilla.getKey(value);
        }
        for (Map.Entry<ResourceLocation, T> entry : own.entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public T getValue(ResourceLocation key) {
        if (vanilla != null) {
            return vanilla.get(key);
        }
        return own.get(key);
    }

    @Override
    public boolean containsKey(ResourceLocation key) {
        if (vanilla != null) {
            return vanilla.containsKey(key);
        }
        return own.containsKey(key);
    }

    @Override
    public Set<ResourceLocation> getKeys() {
        if (vanilla != null) {
            return vanilla.keySet();
        }
        return own.keySet();
    }

    @Override
    public Collection<T> getValues() {
        if (vanilla != null) {
            java.util.List<T> list = new java.util.ArrayList<>();
            vanilla.forEach(list::add);
            return list;
        }
        return own.values();
    }

    @Override
    public ITagManager<T> tags() {
        return tagManager;
    }

    public Optional<TagKey<T>> buildTagKey(ResourceLocation id) {
        return tagKeyFactory == null ? Optional.empty() : Optional.of(tagKeyFactory.apply(id));
    }

    public static final class SimpleTagManager<T> implements ITagManager<T> {
        private final FutoForgeRegistry<T> registry;

        SimpleTagManager(FutoForgeRegistry<T> registry) {
            this.registry = registry;
        }

        @Override
        public ITag<T> getTag(TagKey<T> key) {
            return new SimpleTag<>();
        }

        @Override
        public boolean isKnownTagName(TagKey<T> key) {
            return true;
        }

        @Override
        public TagKey<T> createTagKey(ResourceLocation id) {
            return registry.buildTagKey(id).orElse(null);
        }

        @Override
        public TagKey<T> createOptionalTagKey(ResourceLocation id, java.util.Collection<? extends T> defaults) {
            return createTagKey(id);
        }
    }

    public static final class SimpleTag<T> implements ITag<T> {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean contains(T value) {
            return false;
        }

        @Override
        public java.util.stream.Stream<T> stream() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return java.util.Collections.emptyIterator();
        }
    }
}


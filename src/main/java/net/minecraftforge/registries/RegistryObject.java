package net.minecraftforge.registries;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Futo 兼容层：延迟注册对象。值在 {@code DeferredRegister.register(...)} 时创建。
 */
public class RegistryObject<T> implements Supplier<T> {
    private final FutoForgeRegistry<T> registry;
    private final ResourceLocation id;
    private T value;

    public RegistryObject(FutoForgeRegistry<T> registry, ResourceLocation id) {
        this.registry = registry;
        this.id = id;
    }

    @Override
    public T get() {
        if (value != null) {
            return value;
        }
        return registry.getValue(id);
    }

    public ResourceLocation getId() {
        return id;
    }

    public boolean isPresent() {
        return get() != null;
    }

    public void ifPresent(java.util.function.Consumer<? super T> action) {
        T t = get();
        if (t != null) {
            action.accept(t);
        }
    }

    public T orElse(T other) {
        T t = get();
        return t == null ? other : t;
    }

    void setValue(T value) {
        this.value = value;
    }
}


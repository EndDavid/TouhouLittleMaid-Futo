package net.minecraftforge.registries;

import net.minecraftforge.common.FutoLog;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Futo 兼容层：把 Forge 的两段式注册（create → register(eventBus)）映射到 Fabric。
 */
public class DeferredRegister<T> {
    private static final Map<ResourceKey<? extends Registry<?>>, FutoForgeRegistry<?>> OWN_REGISTRIES = new LinkedHashMap<>();

    private final FutoForgeRegistry<T> registry;
    private final String modid;
    private final List<Entry<T>> entries = new ArrayList<>();

    private record Entry<T>(ResourceLocation id, RegistryObject<T> object, Supplier<T> supplier) {
    }

    private DeferredRegister(FutoForgeRegistry<T> registry, String modid) {
        this.registry = registry;
        this.modid = modid;
    }

    public static <T> DeferredRegister<T> create(IForgeRegistry<T> forgeRegistry, String modid) {
        return new DeferredRegister<>((FutoForgeRegistry<T>) forgeRegistry, modid);
    }

    @SuppressWarnings("unchecked")
    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String modid) {
        FutoForgeRegistry<T> registry = (FutoForgeRegistry<T>) OWN_REGISTRIES.get(key);
        if (registry == null) {
            registry = new FutoForgeRegistry<>(null, id -> net.minecraft.tags.TagKey.create(key, id));
            OWN_REGISTRIES.put(key, registry);
        }
        return new DeferredRegister<>(registry, modid);
    }

    @SuppressWarnings("unchecked")
    public static <T> FutoForgeRegistry<T> getOwnRegistry(ResourceKey<? extends Registry<T>> key) {
        return (FutoForgeRegistry<T>) OWN_REGISTRIES.get(key);
    }

    public RegistryObject<T> register(String name, Supplier<? extends T> supplier) {
        ResourceLocation id = new ResourceLocation(modid, name);
        RegistryObject<T> object = new RegistryObject<>(registry, id);
        entries.add(new Entry<>(id, object, (Supplier<T>) supplier));
        return object;
    }

    /**
     * 镜像 Forge 行为：由主类按固定顺序调用，逐个 flush。
     */
    public void register(IEventBus bus) {
        flush();
    }

    @SuppressWarnings("unchecked")
    public void flush() {
        for (Entry<T> entry : entries) {
            if (entry.object().isPresent()) {
                continue;
            }
            T value = entry.supplier().get();
            if (value == null) {
                FutoLog.getLogger().error("Registry entry {} returned null", entry.id());
                continue;
            }
            entry.object().setValue(value);
            if (registry.isOwnRegistry()) {
                registry.put(entry.id(), value);
            } else {
                Registry.register(registry.vanilla(), entry.id(), value);
            }
        }
    }
}

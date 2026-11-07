package net.minecraftforge.fml;

import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Futo 兼容层：配置注册改为直接交给 FutoConfigRegistrar。
 */
public class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();
    private final List<Runnable> pendingConfigs = new ArrayList<>();

    public static ModLoadingContext get() {
        return INSTANCE;
    }

    public void registerConfig(ModConfig.Type type, Object spec) {
        pendingConfigs.add(() -> FutoConfigRegistrar.register(type, spec));
        if (FutoConfigRegistrar.isBootstrapped()) {
            flush();
        }
    }

    public void flush() {
        List<Runnable> copy = new ArrayList<>(pendingConfigs);
        pendingConfigs.clear();
        copy.forEach(Runnable::run);
    }
}


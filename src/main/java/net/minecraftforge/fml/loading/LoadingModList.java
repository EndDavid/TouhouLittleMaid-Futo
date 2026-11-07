package net.minecraftforge.fml.loading;

import net.fabricmc.loader.api.FabricLoader;

public class LoadingModList {
    private static final LoadingModList INSTANCE = new LoadingModList();

    public static LoadingModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}


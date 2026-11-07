package net.minecraftforge.fml.util.thread;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraftforge.fml.LogicalSide;

public final class EffectiveSide {
    private EffectiveSide() {
    }

    public static LogicalSide get() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? LogicalSide.CLIENT : LogicalSide.SERVER;
    }
}


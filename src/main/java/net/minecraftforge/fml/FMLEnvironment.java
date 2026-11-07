package net.minecraftforge.fml;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public class FMLEnvironment {
    public static final net.minecraftforge.api.distmarker.Dist dist =
            FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                    ? net.minecraftforge.api.distmarker.Dist.CLIENT
                    : net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER;

    public static boolean production;
}


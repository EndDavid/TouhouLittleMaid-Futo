package net.minecraftforge.fml;

import net.minecraftforge.api.distmarker.Dist;

public class DistExecutor {
    public static <T> T safeRunForDist(java.util.function.Supplier<java.util.function.Supplier<T>> clientTarget,
                                       java.util.function.Supplier<java.util.function.Supplier<T>> serverTarget) {
        return unsafeRunForDist(clientTarget, serverTarget);
    }

    public static <T> T unsafeRunForDist(java.util.function.Supplier<java.util.function.Supplier<T>> clientTarget,
                                         java.util.function.Supplier<java.util.function.Supplier<T>> serverTarget) {
        return (FMLEnvironment.dist == Dist.CLIENT ? clientTarget : serverTarget).get().get();
    }
}


package net.minecraftforge.fml;

/**
 * Futo 兼容层：配置注册占位。
 * 真正的 ForgeConfigSpec 绑定会在接入 Forge Config API Port 后补上。
 */
public final class FutoConfigRegistrar {
    private static boolean bootstrapped = false;

    private FutoConfigRegistrar() {
    }

    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    public static void bootstrap() {
        bootstrapped = true;
    }

    public static void register(net.minecraftforge.fml.config.ModConfig.Type type, Object spec) {
        // TODO(Futo): 接入 Forge Config API Port
    }
}

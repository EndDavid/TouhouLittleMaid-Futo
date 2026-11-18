package net.minecraftforge.fml;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
/**
 * Forge 的「注册配置」入口，在 Fabric 上转交给 Forge Config API Port。
 *
 * <p><b>这里是本移植版很长一段时间里的一个坑：</b>它原先把注册请求攒进一个列表交给
 * {@code FutoConfigRegistrar}，而那个类的 {@code register(...)} 方法体只有一行
 * {@code // TODO} —— 也就是说「用上游写法注册的配置会静默消失」。
 * 主入口当时恰好走的是 FCAP 自己的 {@code net.minecraftforge.api.ModLoadingContext}，
 * 所以问题没暴露；但任何从上游原样搬过来的文件（上游全都写
 * {@code ModLoadingContext.get().registerConfig(...)}）都会踩这个洞。</p>
 *
 * <p>现在它只是一层转发：FCAP 的签名多一个 modId，本移植版只有这一个模组，
 * 直接填 {@link TouhouLittleMaid#MOD_ID} 即可。于是上游那一行可以逐字保留：</p>
 *
 * <pre>{@code
 * ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GeneralConfig.init());
 * }</pre>
 *
 * <p>{@code ModConfig} 与 {@code IConfigSpec} 都来自 Forge Config API Port，
 * 本项目<b>不再</b>自带同名类。之前自带的那份
 * {@code net.minecraftforge.fml.config.ModConfig} 是个只有枚举的空壳，与 FCAP 的同名类构成
 * <b>重复类</b> —— 谁先被类加载器选中取决于加载顺序，选中空壳时 FCAP 自己的
 * {@code ConfigTracker} 会在调用 {@code getConfigData()} 时抛 {@code NoSuchMethodError}。</p>
 */
public class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();
    public static ModLoadingContext get() {
        return INSTANCE;
    }
    public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec) {
        net.minecraftforge.api.ModLoadingContext.registerConfig(TouhouLittleMaid.MOD_ID, type, spec);
    }
    /**
     * 上游的第二个重载允许指定文件名；FCAP 里同样存在，直接转发。
     */
    public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec, String fileName) {
        net.minecraftforge.api.ModLoadingContext.registerConfig(TouhouLittleMaid.MOD_ID, type, spec, fileName);
    }
}

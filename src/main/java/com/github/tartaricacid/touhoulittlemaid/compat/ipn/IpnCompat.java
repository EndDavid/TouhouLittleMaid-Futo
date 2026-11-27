package com.github.tartaricacid.touhoulittlemaid.compat.ipn;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Inventory Profiles Next（简称 IPN）联动的「装没装」判定。
 *
 * <p>上游 Forge 版用 {@code ModList.get().isLoaded("inventoryprofilesnext")}，
 * Fabric 上就是 {@link FabricLoader#isModLoaded(String)}。两者出现在同一个方法里的次数很少，
 * 所以这里单独抽出来，其它地方只认 {@link #isLoaded()}。</p>
 *
 * <h2>为什么一定要先问「装了吗」</h2>
 * <p>IPN 是<b>纯客户端</b>模组，而且它的 jar 里有一半是 Kotlin（需要
 * {@code fabric-language-kotlin} 与 {@code libipn} 两个前置）。这两个前置不在本仓库的
 * 依赖里，所以：</p>
 * <ul>
 *     <li>编译期需要 IPN 的 jar 才能写 {@code @IPNGuiHint} 注解（见各界面类的类注释）；</li>
 *     <li>运行期<b>绝不能</b>无条件引用 IPN 的类型 —— 缺它时那是
 *         {@code NoClassDefFoundError}，属于 {@code Error}，{@code catch (Exception)} 接不住。</li>
 * </ul>
 * <p>本类只引用 {@code FabricLoader}，因此任何时候加载都安全；真正读 IPN 类型的地方是
 * {@link SortButtonScreen}（只画一张贴图，不碰 IPN 的类）与各界面上的注解
 * （注解由 IPN 自己用反射读，我们不读）。</p>
 */
public final class IpnCompat {
    /** IPN 的 mod id。 */
    public static final String MOD_ID = "inventoryprofilesnext";

    /**
     * 缓存结果。用 {@code Boolean} 而不是 {@code boolean}，这样「还没问过」与
     * 「问过，答案是 false」能区分开 —— {@link SortButtonScreen} 可能在
     * {@link #init()} 之前就被渲染线程调到（界面一打开就画），懒求值可以兜住。
     */
    private static Boolean loaded;

    private IpnCompat() {
    }

    public static boolean isLoaded() {
        Boolean value = loaded;
        if (value == null) {
            value = FabricLoader.getInstance().isModLoaded(MOD_ID);
            loaded = value;
        }
        return value;
    }

    /**
     * 客户端入口调用一次，只为了在日志里留一条可断言的锚点。
     */
    public static void init() {
        TouhouLittleMaid.LOGGER.info("[Futo] Inventory Profiles Next present: {}", isLoaded());
    }
}


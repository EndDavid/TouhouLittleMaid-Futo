package com.github.tartaricacid.touhoulittlemaid.compat.iris;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Iris（Fabric 上的光影模组）联动，对应上游的 {@code compat/oculus/OculusCompat}。
 *
 * <h2>为什么类名从 Oculus 改成 Iris</h2>
 * <p>Oculus 是光影模组 GameOnCracked 在<b>Forge</b> 上的名字，它的 Fabric 版叫
 * Iris —— 上游 TLMO 1.20.1 的 Fabric 分支也是这么处理的：同一个类，{@code MOD_ID}
 * 从 {@code "oculus"} 换成 {@code "iris"}（源码里就写着注释 {@code // Fabric: Oculus->Iris}）。
 * 这个仓库是 Fabric 移植版，所以目录与类名都按 Fabric 的叫法落地，避免有人按
 * 「装 Oculus 了吗」去搜代码却搜不到。</p>
 *
 * <h2>判定方式：只问「装没装」，不问「光影开没开」</h2>
 * <p>上游 Forge 版是 {@code ModList.get().getModContainerById("oculus").isPresent()}，
 * TLMO 1.20.1 Fabric 版是 {@code FabricLoader.getInstance().isModLoaded("iris")}。
 * 这里跟后者一致：<b>不</b>去调 Iris 的 {@code IrisApi#isShaderPackInUse()}。</p>
 *
 * <p>理由有两个：</p>
 * <ol>
 *     <li>我们只需要回答「要不要多补一个退化顶点」（见 {@code MaidFishingHookRenderer}），
 *         这个动作在任何情况下都无害 —— 它只是在 {@code lineStrip} 末尾多写一个与上一节
 *         几乎重合的顶点，把最后一节冲出去；</li>
 *     <li>用模组 API 会引入编译期依赖，并且 {@code IrisApi.getInstance()} 在光影尚未
 *         初始化时是否有值并没有保证。多一个会抛异常的分支，不如照抄已经跑了很久的上游写法。</li>
 * </ol>
 */
public final class IrisCompat {
    /** Iris 的 mod id（Forge 侧叫 oculus）。 */
    public static final String MOD_ID = "iris";

    /** 见 {@link IpnCompat#isLoaded()} 的说明：用包装类型区分「没问过」与「问过是 false」。 */
    private static Boolean loaded;

    private IrisCompat() {
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
        TouhouLittleMaid.LOGGER.info("[Futo] Iris present: {}", isLoaded());
    }
}


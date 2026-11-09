package com.github.tartaricacid.touhoulittlemaid.compat.patchouli;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

/**
 * 帕秋莉手册（Patchouli）兼容入口。
 *
 * <p><b>为什么所有东西都塞在这个类里判断：</b>Patchouli 是可选模组。JVM 只在「真正执行到」
 * 某段字节码时才会去解析它引用的类，所以只要 <b>本类自身</b> 不出现任何
 * {@code vazkii.patchouli.*} 类型，未安装 Patchouli 时加载本类就不会触发
 * {@code NoClassDefFoundError}。真正的 API 调用都压在
 * {@link OpenDefaultBook} / {@link MultiblockRegistry} 里，只有确认模组存在后才调用它们。</p>
 *
 * <p>上游对应 {@code compat/patchouli/PatchouliCompat}：注册祭坛多方块 + 客户端默认开书监听。</p>
 */
public final class PatchouliCompat {
    /**
     * Patchouli 的 mod id，同时也是 {@code ModList#isLoaded} 的键。
     */
    public static final String MOD_ID = "patchouli";
    /**
     * 本模组手册的 id：{@code data/touhou_little_maid/patchouli_books/memorizable_gensokyo/book.json}。
     */
    public static final ResourceLocation BOOK_ID =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "memorizable_gensokyo");

    private static boolean initialized = false;

    private PatchouliCompat() {
    }

    /**
     * Patchouli 是否已安装。
     *
     * <p>走兼容层的 {@link ModList}（其内部就是 {@code FabricLoader#isModLoaded}），
     * 与上游的写法保持一一对应。</p>
     */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 模组初始化阶段调用（主入口）。未安装 Patchouli 时什么也不做。
     */
    public static void init() {
        if (initialized || !isLoaded()) {
            return;
        }
        initialized = true;
        // 祭坛多方块：手册里 overview/multiblocks_altar 一页用 multiblock_id 引用它。
        // 只注册结构本身，不依赖祭坛方块（模板只用原版橡木原木与红羊毛）。
        MultiblockRegistry.init();
        // 默认的「打开本模组手册」监听器只在客户端有意义。
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new OpenDefaultBook());
        }
    }
}


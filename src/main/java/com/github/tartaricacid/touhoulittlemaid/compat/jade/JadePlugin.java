package com.github.tartaricacid.touhoulittlemaid.compat.jade;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBeacon;
import com.github.tartaricacid.touhoulittlemaid.compat.jade.provider.MaidProvider;
import com.github.tartaricacid.touhoulittlemaid.compat.jade.provider.ShrineLampProvider;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * Touhou Little Maid 的 Jade 插件，移植自上游 {@code compat/jade/JadePlugin}
 * （Forge 1.18.2 写的是 {@code mcp.mobius.waila.api.*}，Fabric 侧包名是 {@code snownee.jade.api.*}，
 * 两者是同一个 API，只是 Maven 坐标不同）。
 *
 * <h2>它是怎么被加载的</h2>
 * <p>Forge 版靠 {@code @WailaPlugin} 注解扫描类路径；<b>Fabric 版不扫描注解</b>，走入口点：
 * {@code snownee.jade.Jade#loadComplete()} 里是
 * {@code FabricLoader.getInstance().getEntrypointContainers("jade", IWailaPlugin.class)}。
 * 因此本类登记在 {@code fabric.mod.json} 的 <b>{@code jade}</b> 入口点里，
 * 没装 Jade 时没有任何东西会来取它 —— <b>Jade 是可选依赖</b>，不写进 {@code depends}。</p>
 *
 * <h2>为什么没有 {@code @WailaPlugin}</h2>
 * <p>该注解在 Jade 5.100.0 里是 {@code String value()} 且无默认值（Forge 侧用它登记 modid），
 * Fabric 侧完全不读它，写上去只是噪音，所以这里不加。</p>
 *
 * <h2>与上游的差异</h2>
 * <p>上游 1.18.2 注册时带 {@code TooltipPosition.TAIL}；Jade 5.x 的 Fabric API 已经取消了
 * 这个参数（改成 {@code TooltipPosition} 常量类 + provider 自己的 {@code getDefaultPriority()}），
 * 所以这里只有「注册到哪个类」两个参数，与 TLMO 1.20.1 Fabric 版写法一致。</p>
 */
public class JadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ShrineLampProvider.INSTANCE, BlockMaidBeacon.class);
        registration.registerEntityComponent(MaidProvider.INSTANCE, EntityMaid.class);
        // 这行日志是端到端验证用的锚点：只有「装了 Jade + 入口点被读到 + 本类成功加载」三者同时成立，
        // 启动日志里才会出现它。缺 Jade 时静默跳过（入口点根本不会被取），不会报错。
        TouhouLittleMaid.LOGGER.info("[Jade] Registered maid / shrine lamp tooltip providers");
    }
}


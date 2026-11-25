package com.github.tartaricacid.touhoulittlemaid.compat.modmenu;

import com.github.tartaricacid.touhoulittlemaid.compat.cloth.MenuIntegration;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu 的入口点：让模组列表里出现一个「配置」齿轮，点开就是
 * {@link MenuIntegration} 那张大表。
 *
 * <p>移植自上游 1.20.1 Fabric 版的 {@code cn.sh1rocu.touhoulittlemaid.compat.modmenu.ModMenuApiImpl}，
 * 行为逐字一致：</p>
 * <ul>
 *     <li>装了 {@code cloth-config} → 返回一个工厂，点齿轮直接开配置界面；</li>
 *     <li>没装 → 返回 {@code null}，ModMenu 就不显示这颗齿轮
 *         （而不是显示一颗点了会崩的齿轮）。</li>
 * </ul>
 *
 * <h2>为什么返回 null 而不是「打开请安装 Cloth」那一页</h2>
 * <p>上游返回 null。这里保持一致：没有 Cloth 时配置界面根本没有可显示的内容，
 * 摆一个只有「去下载」的齿轮只会让人以为配置界面坏了。
 * 而 {@code client/gui/mod/ClothConfigScreen} 那一页仍然保留 ——
 * 玩家<b>按键</b>打开配置时会看到它，那才是「我明明点了却什么都没发生」的地方。</p>
 *
 * <h2>为什么这个类不会在没装 ModMenu 时炸</h2>
 * <p>它是 {@code fabric.mod.json} 里 {@code modmenu} 入口点的实现类，
 * 只有 ModMenu 存在时才会被加载。没装 ModMenu 时没有任何东西会碰它的类字面量，
 * 所以 {@code com.terraformersmc.modmenu.*} 永远不需要被解析。</p>
 */
public class ModMenuApiImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!com.github.tartaricacid.touhoulittlemaid.compat.cloth.ClothConfigCompat.isAvailable()) {
            return null;
        }
        // 用 lambda 而不是方法引用，是为了让 MenuIntegration 的类初始化推迟到
        // 「真的有人点齿轮」那一刻 —— 否则这一段在 ModMenu 扫描入口点时就会触碰 Cloth 类型。
        return parent -> MenuIntegration.getConfigBuilder().setParentScreen(parent).build();
    }
}


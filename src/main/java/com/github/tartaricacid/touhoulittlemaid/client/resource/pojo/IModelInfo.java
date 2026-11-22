package com.github.tartaricacid.touhoulittlemaid.client.resource.pojo;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 「一条模型定义」的最小公共接口，对应上游 {@code client/resource/pojo/IModelInfo}。
 *
 * <p>换肤界面（{@code AbstractModelGui}）只关心四件事：模型 id（画高亮框 + 判断几何是否就绪）、
 * 名字与描述（悬停提示）、物品栏缩放（缩略图大小）。女仆模型与坐垫模型都提供这四项，
 * 因此界面可以只写一次。</p>
 *
 * <h2>为什么不直接照抄上游那个接口</h2>
 * <p>上游的 {@code IModelInfo} 还带着 {@code getCacheIconId()}（图标缓存）、
 * {@code decorate()} 与 {@code extra(...)}（都是「反序列化后补全」用的）。
 * 本移植版没有图标缓存系统，那两个补全方法是各自加载器里的实现细节，
 * 放进接口只会逼着每个实现类写一堆无意义的重载。所以这里只保留界面真正调用的四个。</p>
 */
public interface IModelInfo {
    /**
     * 模型 id，例如 {@code touhou_little_maid:cushion}。
     *
     * <p>加载器在 {@code decorate()} 阶段保证它非空（缺字段直接抛
     * {@code JsonSyntaxException}），所以这里可以按非空使用。</p>
     */
    ResourceLocation getModelId();

    /**
     * 未解析的显示名。可能是 {@code {model.ns.path.name}} 这种翻译键，
     * 界面用 {@code ParseI18n} 解析。
     */
    String getName();

    /**
     * 未解析的描述行，同上。
     */
    List<String> getDescription();

    /**
     * 物品栏 / 缩略图的缩放系数，缺省 1.0。
     */
    float getRenderItemScale();
}


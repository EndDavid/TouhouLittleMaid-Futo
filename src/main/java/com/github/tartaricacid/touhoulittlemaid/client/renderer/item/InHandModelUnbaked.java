package com.github.tartaricacid.touhoulittlemaid.client.renderer.item;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 把「平面图标模型」和「手持 3D 模型」合成一个的未烘焙模型。
 *
 * <p>上游靠 Forge 的 {@code ModelRegistryEvent} + {@code ModelBakeEvent} 两次事件：
 * 先把 {@code *_in_hand} 注册成「特殊模型」让它被烘焙，再在烘焙完成后把物品模型
 * 换成一个 {@code PerspectiveBakedModel} 包装器。</p>
 *
 * <p>Fabric 1.18.2 没有这两个事件，但有等价且更靠前的钩子：
 * {@code ModelLoadingRegistry#registerVariantProvider}。我们把物品的
 * {@code <id>#inventory} 直接换成一个<b>自己的 {@link UnbakedModel}</b>，
 * 由它在烘焙时同时产出两套模型。这样就不需要在烘焙完成后改表，也不会和别人抢注册顺序。</p>
 *
 * <h2>两个模型必须用不同的方式烘焙</h2>
 *
 * <p>这是本次移植唯一真正的坑，两个模型的父链都是
 * {@code ... → item/generated → builtin/generated}，也就是 {@code ModelBakery.GENERATION_MARKER}：</p>
 *
 * <ul>
 *   <li><b>平面图标</b>（{@code hakurei_gohei.json}）本身<b>没有 elements</b>，只有
 *       {@code textures.layer0}。它必须走 {@link ModelBakery#bake(ResourceLocation, ModelState)} ——
 *       那里有个分支：只要 {@code getRootModel() == GENERATION_MARKER}，就交给
 *       {@code ItemModelGenerator} 从 {@code layer0} 的贴图透明度「描」出平面四边形。
 *       直接调 {@code UnbakedModel#bake} 会得到一个<b>没有任何四边形</b>的空模型
 *       （物品栏图标直接消失）。</li>
 *   <li><b>手持模型</b>（{@code hakurei_gohei_in_hand.json}）自己带 elements，
 *       但贴图槽叫 {@code #0}，<b>没有 {@code layer0}</b>。
 *       {@code ItemModelGenerator} 是这么开始的：
 *       <pre>
 *       for (String layer : LAYERS /* layer0, layer1... *&#47;) {
 *           if (!model.hasTexture(layer)) break;      // ← 立刻 break
 *           elements.addAll(processFrames(...));
 *       }
 *       return new BlockModel(null, elements, ...);   // ← 原始 elements 被丢弃
 *       </pre>
 *       所以对它走 {@code ModelBakery#bake} 会得到一个<b>空模型</b>（手持时看不见）。
 *       必须直接调 {@link UnbakedModel#bake}，让 {@code BlockModel} 用自己声明的 elements。</li>
 * </ul>
 *
 * <h2>贴图为什么能进图集</h2>
 *
 * <p>{@code hakurei_gohei_in_hand.png} 原本没有被任何模型引用，图集拼接阶段不会被收进去，
 * 烘焙时 {@code spriteGetter} 就拿不到 sprite。所以这里在
 * {@link #getMaterials} 里把两边的材质<b>都</b>交出去，
 * 并在 {@link #getDependencies} 里把手持模型的 id 报给 {@code ModelBakery}
 * 让它先被加载 —— 这两步保证贴图在拼接前就在集合里。</p>
 */
public class InHandModelUnbaked implements UnbakedModel {
    /**
     * 平面图标的模型 id（例如 {@code touhou_little_maid:item/hakurei_gohei}）。
     *
     * <p>保留 id 而不是只留 {@link UnbakedModel}，是因为平面图标必须回到
     * {@code ModelBakery#bake} 才能触发 {@code ItemModelGenerator}。</p>
     */
    private final ResourceLocation flatId;
    /**
     * 手持模型的模型 id（例如 {@code touhou_little_maid:item/hakurei_gohei_in_hand}）。
     */
    private final ResourceLocation handId;
    private final UnbakedModel flat;
    private final UnbakedModel hand;

    public InHandModelUnbaked(ResourceLocation flatId, ResourceLocation handId,
                              UnbakedModel flat, UnbakedModel hand) {
        this.flatId = flatId;
        this.handId = handId;
        this.flat = flat;
        this.hand = hand;
    }

    /**
     * 告诉 {@code ModelBakery}「这两个模型都要先加载好」。
     *
     * <p>把 {@code handId} 自己也放进来是必需的：它原本没人引用，
     * 不在这里报出去就永远不会被加载，它的材质也就不会进入图集。</p>
     */
    @Override
    public Collection<ResourceLocation> getDependencies() {
        Set<ResourceLocation> dependencies = new LinkedHashSet<>();
        dependencies.addAll(this.flat.getDependencies());
        dependencies.add(this.handId);
        dependencies.addAll(this.hand.getDependencies());
        return dependencies;
    }

    /**
     * 两边的材质都要交出去，否则手持贴图不会被打进图集。
     */
    @Override
    public Collection<Material> getMaterials(Function<ResourceLocation, UnbakedModel> modelGetter,
                                             Set<Pair<String, String>> missingTextures) {
        List<Material> materials = new ArrayList<>();
        materials.addAll(this.flat.getMaterials(modelGetter, missingTextures));
        materials.addAll(this.hand.getMaterials(modelGetter, missingTextures));
        return materials;
    }

    @Override
    public BakedModel bake(ModelBakery bakery, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState state, ResourceLocation location) {
        // 平面图标：必须走 ModelBakery#bake 才会触发 ItemModelGenerator（见类注释）
        BakedModel flatBaked = bakery.bake(this.flatId, state);
        // 手持模型：直接调 UnbakedModel#bake，保留自己声明的 elements
        BakedModel handBaked = this.hand.bake(bakery, spriteGetter, state, this.handId);
        // 极端情况下（快照资源缺失）退回平面模型，至少不至于显示成黑白格
        if (handBaked == null) {
            return flatBaked;
        }
        return new PerspectiveBakedModel(flatBaked, handBaked);
    }

}


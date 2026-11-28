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


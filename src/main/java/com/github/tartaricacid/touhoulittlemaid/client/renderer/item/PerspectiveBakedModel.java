package com.github.tartaricacid.touhoulittlemaid.client.renderer.item;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * 「按视角切换模型」的烘焙模型 —— 上游 {@code client/renderer/item/PerspectiveBakedModel} 的移植。
 *
 * <h2>它解决什么问题</h2>
 *
 * <p>御币在<b>背包 / 物品栏</b>里应该是一张 16×16 的平面图标（{@code item/generated} 生成的一层贴片），
 * 而在<b>手上</b>应该是 Blockbench 做的 3D 模型（{@code hakurei_gohei_in_hand}）。
 * 同一个物品 id 只能对应一个 {@code BakedModel}，所以必须有一个包装器在渲染时按视角二选一。</p>
 *
 * <h2>为什么 1.18.2 不能照抄上游的写法</h2>
 *
 * <p>上游覆写的是 {@code BakedModel#handlePerspective(TransformType, PoseStack)} ——
 * <b>那是 Forge 给 {@code BakedModel} 打的扩展方法</b>，原版接口里没有。
 * 原版的视角变换走 {@code getTransforms()} 返回的 {@code ItemTransforms}，</p>
 *
 * <pre>
 * // net.minecraft.client.renderer.entity.ItemRenderer#render(...)
 * ItemTransforms transforms = model.getTransforms();
 * transforms.getTransform(type).apply(leftHand, poseStack);
 * ... renderModelLists(model, ...)     // ← 这里画的是同一个 model 的 quads
 * </pre>
 *
 * <p>也就是说「变换」和「几何」都取自<b>同一个</b> model，包装器无法在
 * {@code getTransforms()} 里知道当前是哪个 type（{@code getTransform(type)} 才知道，
 * 但那时已经晚了，{@code getQuads} 不带 type）。所以没法像上游那样只靠 BakedModel 完成切换。</p>
 *
 * <p>本移植版的做法：这个类只负责<b>携带</b>两套模型并提供 {@link #pickFor(TransformType)}，
 * 真正的选择发生在 {@code mixin/client/ItemRendererMixin} 里 ——
 * 那里同时拿得到 {@code ItemStack} 和 {@code TransformType}，是唯一确定的位置。</p>
 */
public class PerspectiveBakedModel implements BakedModel {
    /**
     * 平面图标（{@code item/generated} 生成，用于 GUI 与物品展示框）。
     */
    private final BakedModel flat;
    /**
     * 手持 3D 模型（{@code *_in_hand.json} 里的 elements）。
     */
    private final BakedModel hand;

    public PerspectiveBakedModel(BakedModel flat, BakedModel hand) {
        this.flat = flat;
        this.hand = hand;
    }

    /**
     * 按视角挑模型。
     *
     * <p>与上游 {@code handlePerspective} 的判据逐字一致：只有
     * {@code GUI}（物品栏 / 背包）和 {@code FIXED}（物品展示框）用平面图标，
     * 其余（第一/第三人称、掉落在地、戴在头上）全部用 3D 模型。</p>
     */
    public BakedModel pickFor(ItemTransforms.TransformType type) {
        if (type == ItemTransforms.TransformType.GUI || type == ItemTransforms.TransformType.FIXED) {
            return this.flat;
        }
        return this.hand;
    }

    /**
     * 包装器自身的所有属性一律转发给平面模型。
     *
     * <p>理由：任何走到这里的调用都发生在<b>没有选模型</b>的上下文里
     * （例如 {@code ModelManager} 取粒子图标、{@code ItemRenderer#getModel} 返回值被外部读属性）。
     * 用平面模型的属性语义最接近「默认外观」，也不会因为 {@code isGui3d()} 之类
     * 与应用了 3D 变换的 pose 不匹配而出现半透明/光照异常。</p>
     */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, Random rand) {
        return this.flat.getQuads(state, side, rand);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.flat.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.flat.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.flat.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return this.flat.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.flat.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return this.flat.getTransforms();
    }

    /**
     * 上游给的是 {@code ItemOverrides.EMPTY}：御币没有 {@code overrides} 谓词，
     * 这里保持同一行为，避免破坏其它模组对 {@code predicates} 的读取。
     */
    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}


package com.github.tartaricacid.touhoulittlemaid.client.renderer.item;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.model.EntityPlaceholderIconModel;
import com.github.tartaricacid.touhoulittlemaid.item.ItemEntityPlaceholder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 「生成？？？」占位物品的图标渲染器，对应上游 {@code TileEntityEntityPlaceholderRenderer}
 * 里 {@code renderByItem} 那一半。
 *
 * <p>画什么取决于物品 NBT 里的 {@code RecipeId}：</p>
 * <ol>
 *     <li>优先用 {@code textures/item/<配方文件名>.png}
 *         （比如 {@code altar/spawn_box} → {@code textures/item/spawn_box.png}）；</li>
 *     <li>那张贴图不存在时退回 {@code textures/item/entity_placeholder.png}。</li>
 * </ol>
 * <p>这样新增一条非物品类祭坛配方时，只要放一张同名贴图进来，进度图标就自动对了 ——
 * 与上游的约定一致。</p>
 *
 * <h2>为什么模型懒加载</h2>
 * <p>物品渲染器注册时只是个单例，那一刻 {@code Minecraft#getEntityModels()} 还没就绪，
 * {@code bakeLayer} 会拿到空模型表。所以第一次真正渲染时才烘焙
 * （{@code PicnicBasketItemRenderer} 与坐垫渲染器同一个理由）。</p>
 */
public final class EntityPlaceholderItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final ResourceLocation FALLBACK =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/item/entity_placeholder.png");
    private static final RenderType FALLBACK_TYPE = RenderType.entityCutoutNoCull(FALLBACK);

    private EntityPlaceholderIconModel icon;
    /**
     * 上一次解析出的贴图，避免每帧都去 {@code ResourceManager} 里查一遍。
     * 记录它的来源 id，{@code RecipeId} 变了才重新解析。
     */
    @Nullable
    private ResourceLocation cachedFor;
    @Nullable
    private RenderType cachedType;

    @Override
    public void render(ItemStack stack, ItemTransforms.TransformType mode, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 物品图标渲染抛异常会崩在 ItemRenderer 里，没有上层接住 —— 画不出来最多是「少个模型」
        try {
            if (this.icon == null) {
                this.icon = new EntityPlaceholderIconModel(
                        Minecraft.getInstance().getEntityModels().bakeLayer(EntityPlaceholderIconModel.LAYER));
            }
            RenderType type = this.resolveRenderType(ItemEntityPlaceholder.getRecipeId(stack));
            poseStack.pushPose();
            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
            VertexConsumer buffer = bufferSource.getBuffer(type);
            this.icon.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
            poseStack.popPose();
        } catch (Throwable e) {
            TouhouLittleMaid.LOGGER.error(
                    "[Futo] Entity placeholder item renderer failed; the item will render as a missing model", e);
        }
    }

    private RenderType resolveRenderType(@Nullable ResourceLocation recipeId) {
        if (recipeId == null) {
            return FALLBACK_TYPE;
        }
        if (recipeId.equals(this.cachedFor) && this.cachedType != null) {
            return this.cachedType;
        }
        String path = recipeId.getPath();
        int slash = path.lastIndexOf('/');
        String fileName = slash < 0 ? path : path.substring(slash + 1);
        ResourceLocation texture = new ResourceLocation(recipeId.getNamespace(),
                "textures/item/" + fileName.toLowerCase(java.util.Locale.US) + ".png");
        RenderType type = this.hasResource(texture)
                ? RenderType.entityCutoutNoCull(texture)
                : FALLBACK_TYPE;
        this.cachedFor = recipeId;
        this.cachedType = type;
        return type;
    }

    /**
     * 贴图存在性检查。
     *
     * <p><b>不能用 {@code getResource(...)}：</b>1.18.2 的那两个重载一个返回
     * {@code Resource} 并在找不到时抛 {@code IOException}、另一个直接抛异常，
     * 没有 1.19+ 的 {@code Optional<Resource>} 形式（这是本仓库反复踩的坑）。
     * {@code hasResource} 才是「问一问、不抛」的那一个。</p>
     */
    private boolean hasResource(ResourceLocation location) {
        return Minecraft.getInstance().getResourceManager().hasResource(location);
    }
}


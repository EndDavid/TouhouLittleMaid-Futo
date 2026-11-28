package com.github.tartaricacid.touhoulittlemaid.client.renderer.item;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemEntityPlaceholder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
/**
 * 「生成？？？」占位物品的图标渲染器，对应上游
 * {@code TileEntityEntityPlaceholderRenderer#renderByItem}。
 *
 * <p>贴图取 {@code textures/item/<配方文件名>.png}，不存在则退回
 * {@code textures/item/entity_placeholder.png}。</p>
 *
 * <p>这里手写一块朝向 +Z 的单位 quad（上游的「单南面」）。用 {@code addBox} 造零厚度板
 * 会同时踩「背面 UV 跑出贴图」与「四个退化侧面共面」，表现是图标变成条纹。</p>
 *
 * <p>帧数自己读 PNG 尺寸与 {@code mcmeta} 得出：这两张贴图没有物品模型引用，
 * 进不了方块图集，查出来永远是 {@code missingno}。</p>
 */
public final class EntityPlaceholderItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final ResourceLocation FALLBACK =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/item/entity_placeholder.png");
    /**
     * 图标所在的 z。
     *
     * <p>{@code 7.5F / 16}：原版 {@code item/generated} 的那块 quad 就在这个位置。</p>
     */
    private static final float QUAD_Z = 7.5F / 16.0F;
    /**
     * 上一次解析出的贴图与帧信息，避免每帧都去 {@code ResourceManager} 里查一遍。
     * {@code RecipeId} 变了才重新解析。
     */
    @Nullable
    private ResourceLocation cachedFor;
    @Nullable
    private Frame cached;
    @Override
    public void render(ItemStack stack, ItemTransforms.TransformType mode, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 物品图标渲染抛异常会崩在 ItemRenderer 里，没有上层接住 —— 画不出来最多是「少个图标」
        try {
            Frame frame = this.resolve(ItemEntityPlaceholder.getRecipeId(stack));
            VertexConsumer buffer = bufferSource.getBuffer(frame.type());
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normalMatrix = poseStack.last().normal();
            Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);
            normal.transform(normalMatrix);
            // 1.18.2 的 com.mojang.math.Vector3f 没有 lengthSquared()，只有返回 boolean 的
            // normalize()（长度为 0 时返回 false 且不改动）。成败都不影响下面这次绘制。
            normal.normalize();
            int index = frame.frameAt(Util.getMillis());
            quad(buffer, pose, normal, frame.count(), index, packedLight, packedOverlay);
        } catch (Throwable e) {
            TouhouLittleMaid.LOGGER.error(
                    "[Futo] Entity placeholder item renderer failed; the item will render as a missing model", e);
        }
    }
    /**
     * 一块朝向 +Z 的单位 quad（x/y 从 0 到 1），UV 只覆盖第 {@code index} 帧。
     *
     * <p>顶点顺序逆时针（从 +Z 看），与 {@code item/generated} 的 quad 一致：
     * y = 1 是贴图<b>上</b>沿（v 小）、y = 0 是下沿（v 大）。法线恒为 +Z ——
     * 物品图标在 GUI 里正对镜头，{@code gui_light: front} 也依赖这一点。</p>
     */
    private static void quad(VertexConsumer buffer, Matrix4f pose, Vector3f normal,
                             int frameCount, int index, int packedLight, int packedOverlay) {
        float frames = Math.max(1, frameCount);
        float vTop = index / frames;
        float vBottom = (index + 1) / frames;
        vertex(buffer, pose, normal, 0.0F, 0.0F, 0.0F, vBottom, packedLight, packedOverlay);
        vertex(buffer, pose, normal, 1.0F, 0.0F, 1.0F, vBottom, packedLight, packedOverlay);
        vertex(buffer, pose, normal, 1.0F, 1.0F, 1.0F, vTop, packedLight, packedOverlay);
        vertex(buffer, pose, normal, 0.0F, 1.0F, 0.0F, vTop, packedLight, packedOverlay);
    }
    /**
     * 写一个顶点。
     *
     * <p>1.18.2 的 {@code VertexConsumer#vertex(x, y, z, ...)} 这一族重载<b>不会</b>自己
     * 乘 pose 矩阵（原版 {@code ModelPart#render} 是先 {@code Vector4f.transform} 再写），
     * 所以这里手动做一次。</p>
     */
    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vector3f normal,
                               float x, float y, float u, float v, int packedLight, int packedOverlay) {
        Vector4f position = new Vector4f(x, y, QUAD_Z, 1.0F);
        position.transform(pose);
        buffer.vertex(position.x(), position.y(), position.z(),
                1.0F, 1.0F, 1.0F, 1.0F,
                u, v, packedOverlay, packedLight,
                normal.x(), normal.y(), normal.z());
    }
    private Frame resolve(@Nullable ResourceLocation recipeId) {
        if (recipeId == null) {
            return Frame.single(FALLBACK);
        }
        if (recipeId.equals(this.cachedFor) && this.cached != null) {
            return this.cached;
        }
        ResourceLocation texture = textureOf(recipeId);
        Frame frame = this.hasResource(texture) ? readFrame(texture) : Frame.single(FALLBACK);
        this.cachedFor = recipeId;
        this.cached = frame;
        return frame;
    }
    /** {@code altar/spawn_box} → {@code touhou_little_maid:textures/item/spawn_box.png}。 */
    private static ResourceLocation textureOf(ResourceLocation recipeId) {
        String path = recipeId.getPath();
        int slash = path.lastIndexOf('/');
        String fileName = slash < 0 ? path : path.substring(slash + 1);
        return new ResourceLocation(recipeId.getNamespace(),
                "textures/item/" + fileName.toLowerCase(java.util.Locale.US) + ".png");
    }
    /**
     * 读一张贴图的帧数与时序。
     *
     * <p>帧数取 {@code mcmeta} 的 {@code frames} 数组长度，没有该数组时按「竖着叠的方形帧」
     * 算 {@code 高 / 宽}。任何一步读不出来都退回单帧 —— 画错不如画一张静止的。</p>
     */
    private static Frame readFrame(ResourceLocation texture) {
        int frames = 1;
        int frametime = 1;
        int[] size = readSize(texture);
        if (size != null && size[0] > 0 && size[1] >= size[0]) {
            frames = Math.max(1, size[1] / size[0]);
        }
        JsonObject animation = readAnimation(texture);
        if (animation != null) {
            if (animation.has("frametime")) {
                frametime = Math.max(1, animation.getAsJsonPrimitive("frametime").getAsInt());
            }
            if (animation.has("frames")) {
                // 有显式的帧序列时，播放长度以它为准（本仓库这两张没有，但第三方包可能有）
                frames = Math.max(1, animation.getAsJsonArray("frames").size());
            }
        }
        return new Frame(RenderType.entityCutoutNoCull(texture), frames, frametime);
    }
    @Nullable
    private static int[] readSize(ResourceLocation texture) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(texture).getInputStream()) {
            NativeImage image = NativeImage.read(in);
            try {
                return new int[]{image.getWidth(), image.getHeight()};
            } finally {
                image.close();
            }
        } catch (Exception e) {
            return null;
        }
    }
    @Nullable
    private static JsonObject readAnimation(ResourceLocation texture) {
        ResourceLocation meta = new ResourceLocation(texture.getNamespace(), texture.getPath() + ".mcmeta");
        if (!Minecraft.getInstance().getResourceManager().hasResource(meta)) {
            return null;
        }
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(meta).getInputStream()) {
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return root.has("animation") ? root.getAsJsonObject("animation") : null;
        } catch (Exception e) {
            return null;
        }
    }
    /**
     * 贴图存在性检查。
     *
     * <p><b>不能用 {@code getResource(...)} 来判断：</b>1.18.2 的那两个重载一个返回
     * {@code Resource} 并在找不到时抛 {@code IOException}、另一个直接抛异常，
     * 没有 1.19+ 的 {@code Optional<Resource>} 形式（这是本仓库反复踩的坑）。
     * {@code hasResource} 才是「问一问、不抛」的那一个。</p>
     */
    private boolean hasResource(ResourceLocation location) {
        return Minecraft.getInstance().getResourceManager().hasResource(location);
    }
    /** 一次解析的结果：画在哪个 render type 上、一共几帧、每帧几 tick。 */
    private record Frame(RenderType type, int count, int frametime) {
        static Frame single(ResourceLocation texture) {
            return new Frame(RenderType.entityCutoutNoCull(texture), 1, 1);
        }
        /**
         * 当前该画第几帧。
         *
         * <p>与原版 {@code AnimatedTexture} 同一个节拍：1 tick = 50 ms，
         * 每 {@code frametime} tick 换一帧。</p>
         */
        int frameAt(long millis) {
            if (this.count <= 1) {
                return 0;
            }
            long ticks = millis / 50L;
            return (int) ((ticks / this.frametime) % this.count);
        }
    }
}

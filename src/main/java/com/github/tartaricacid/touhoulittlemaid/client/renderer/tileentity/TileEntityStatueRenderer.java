package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.model.StatueBaseModel;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * 雕像渲染器，逐字移植自上游 {@code TileEntityStatueRenderer}。
 *
 * <p>两段：先画台座（{@link StatueBaseModel}），再画站在上面的女仆。</p>
 *
 * <h2>三个必须照抄的数值</h2>
 * <table border="1">
 *     <caption>摆放参数</caption>
 *     <tr><th>参数</th><th>含义</th></tr>
 *     <tr><td>{@code translate(0.5/size, 0.21328125, 0.5/size)}</td>
 *         <td>把实体挪到台座中心并抬高。{@code 0.5/size} 是「先缩放再平移」导致的
 *         —— 缩放已经把坐标系缩小了，所以要除以 size 才能回到格子中心</td></tr>
 *     <tr><td>{@code offset = 1/4}（MIDDLE）/{@code 1/3}（BIG）</td>
 *         <td>多格雕像的核心方块不在几何中心，要往反方向推一点，否则整座会偏半格</td></tr>
 *     <tr><td>{@code YP} 90/180/270</td><td>朝向；{@code SOUTH} 是 0°，与
 *         {@code ItemChisel} 里 {@code getDirection().getOpposite()} 的取值一致</td></tr>
 * </table>
 */
public class TileEntityStatueRenderer implements BlockEntityRenderer<TileEntityStatue> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/statue_base.png");

    private final StatueBaseModel baseModel;

    public TileEntityStatueRenderer(BlockEntityRendererProvider.Context context) {
        this.baseModel = new StatueBaseModel(context.bakeLayer(StatueBaseModel.LAYER));
    }

    @Override
    public void render(TileEntityStatue te, float partialTicks, PoseStack poseStack, MultiBufferSource bufferIn,
                       int combinedLightIn, int combinedOverlayIn) {
        // 只有核心那格画；其余格子什么都不画（否则一座雕像会被画 N 遍）
        if (!te.isCoreBlock()) {
            return;
        }

        Level world = Minecraft.getInstance().level;
        EntityCacheUtil.onClientLevelChange(world);

        poseStack.pushPose();
        this.setTranslateAndPose(te, poseStack);
        // 台座模型是「Y 向下」建的（EntityModel 约定），所以要翻一次
        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
        VertexConsumer buffer = bufferIn.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.baseModel.renderToBuffer(poseStack, buffer, combinedLightIn, combinedOverlayIn, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        CompoundTag data = te.getExtraMaidData();
        if (data == null || world == null) {
            return;
        }
        EntityType.byString(data.getString("id")).ifPresent(type -> {
            try {
                this.renderEntity(te, poseStack, bufferIn, combinedLightIn, data, world, type);
            } catch (ExecutionException e) {
                TouhouLittleMaid.LOGGER.error("[Futo] Failed to render the maid inside a statue", e);
            }
        });
    }

    private void renderEntity(TileEntityStatue te, PoseStack poseStack, MultiBufferSource bufferIn,
                              int combinedLightIn, CompoundTag data, Level world, EntityType<?> type)
            throws ExecutionException {
        Entity entity;
        if (type.equals(InitEntities.MAID)) {
            // 按方块坐标缓存：同一帧里每座雕像必须是不同的实体实例，
            // 否则 GeckoLib 的骨骼状态会互相覆盖（一排雕像全变成最后那只）
            long posId = te.getBlockPos().asLong();
            entity = EntityCacheUtil.STATUE_CACHE.get(posId, () -> EntityCacheUtil.createMaidEntity(world));
        } else {
            entity = EntityCacheUtil.ENTITY_CACHE.get(type, () -> {
                Entity e = type.create(world);
                return Objects.requireNonNullElseGet(e, () -> EntityCacheUtil.createMaidEntity(world));
            });
        }

        entity.load(data);
        if (entity instanceof EntityMaid maid) {
            EntityCacheUtil.clearMaidDataResidue(maid, true);
            maid.renderState = MaidRenderState.STATUE;
        }

        float size = te.getSize().getScale();
        float offset = this.getOffset(te);

        poseStack.pushPose();
        poseStack.scale(size, size, size);
        poseStack.translate(0.5 / size, 0.21328125, 0.5 / size);
        switch (te.getFacing()) {
            case EAST -> poseStack.mulPose(Vector3f.YP.rotationDegrees(90));
            case WEST -> poseStack.mulPose(Vector3f.YP.rotationDegrees(270));
            case SOUTH -> {
                // 默认朝向，不转
            }
            case NORTH -> poseStack.mulPose(Vector3f.YP.rotationDegrees(180));
            default -> poseStack.mulPose(Vector3f.YP.rotationDegrees(180));
        }
        EntityRenderDispatcher render = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean isShowHitBox = render.shouldRenderHitBoxes();
        render.setRenderHitBoxes(false);
        render.render(entity, offset, 0, -offset, 0, 0, poseStack, bufferIn, combinedLightIn);
        render.setRenderHitBoxes(isShowHitBox);
        poseStack.popPose();
    }

    /**
     * 多格雕像的核心方块不在几何中心，渲染时要按尺寸往反方向推。
     */
    private float getOffset(TileEntityStatue te) {
        if (te.getSize() == TileEntityStatue.Size.MIDDLE) {
            return 1.0F / 4.0F;
        }
        if (te.getSize() == TileEntityStatue.Size.BIG) {
            return 1.0F / 3.0F;
        }
        return 0.0F;
    }

    /**
     * 台座的摆放：先按朝向把多格偏移抵消掉，再缩放到格子中心。
     */
    private void setTranslateAndPose(TileEntityStatue te, PoseStack poseStack) {
        float size = te.getSize().getScale();
        float offset = this.getOffset(te);

        switch (te.getFacing()) {
            case EAST -> poseStack.translate(-offset * size, 0, -offset * size);
            case NORTH -> poseStack.translate(-offset * size, 0, offset * size);
            case WEST -> poseStack.translate(offset * size, 0, offset * size);
            case SOUTH -> poseStack.translate(offset * size, 0, -offset * size);
            default -> poseStack.translate(0, 0, 0);
        }
        poseStack.scale(size, size, size);
        // 模型原点在「脚底 + 中心」，这里把它挪到格子中心
        poseStack.translate(0.5 / size, 0.5, 0.5 / size);
    }




    // NOTE: no @Override -- BlockEntityRenderer has no getRenderBoundingBox in 1.18.2 vanilla.
    public net.minecraft.world.phys.AABB getRenderBoundingBox(TileEntityStatue te) {
        return new net.minecraft.world.phys.AABB(te.getBlockPos().offset(-5, -1, -5), te.getBlockPos().offset(5, 10, 5));
    }
}

package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.model.StatueBaseModel;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
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
 * 手办渲染器，逐字移植自上游 {@code TileEntityGarageKitRenderer}。
 *
 * <p>与 {@link TileEntityStatueRenderer} 结构相同（台座 + 实体），差别只有两点：
 * 手办永远是单格，所以没有多格偏移；包围盒也只有一个方块大。</p>
 */
public class TileEntityGarageKitRenderer implements BlockEntityRenderer<TileEntityGarageKit> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/statue_base.png");

    private final StatueBaseModel baseModel;

    public TileEntityGarageKitRenderer(BlockEntityRendererProvider.Context context) {
        this.baseModel = new StatueBaseModel(context.bakeLayer(StatueBaseModel.LAYER));
    }

    @Override
    public void render(TileEntityGarageKit te, float partialTicks, PoseStack poseStack, MultiBufferSource bufferIn,
                       int combinedLightIn, int combinedOverlayIn) {
        // 台座：缩到半格、抬到格子中心、翻正
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.translate(1, 0.5, 1);
        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
        VertexConsumer buffer = bufferIn.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.baseModel.renderToBuffer(poseStack, buffer, combinedLightIn, combinedOverlayIn, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        CompoundTag data = te.getExtraData();
        Level world = Minecraft.getInstance().level;
        EntityCacheUtil.onClientLevelChange(world);
        if (data.isEmpty() || world == null) {
            return;
        }

        EntityType.byString(data.getString("id")).ifPresent(type -> {
            try {
                this.renderEntity(te, poseStack, bufferIn, combinedLightIn, data, world, type);
            } catch (ExecutionException e) {
                TouhouLittleMaid.LOGGER.error("[Futo] Failed to render the entity inside a garage kit", e);
            }
        });
    }

    private void renderEntity(TileEntityGarageKit te, PoseStack poseStack, MultiBufferSource bufferIn,
                              int combinedLightIn, CompoundTag data, Level world, EntityType<?> type)
            throws ExecutionException {
        Entity entity;
        if (type.equals(InitEntities.MAID)) {
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
            maid.renderState = MaidRenderState.GARAGE_KIT;
        }

        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.translate(1, 0.21328125, 1);
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
        render.render(entity, 0, 0, 0, 0, 0, poseStack, bufferIn, combinedLightIn);
        render.setRenderHitBoxes(isShowHitBox);
        poseStack.popPose();
    }
}


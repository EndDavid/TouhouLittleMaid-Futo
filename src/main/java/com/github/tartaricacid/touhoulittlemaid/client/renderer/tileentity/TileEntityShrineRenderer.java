package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockShrine;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityShrine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TileEntityShrineRenderer implements BlockEntityRenderer<TileEntityShrine> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/shrine.png");

    public TileEntityShrineRenderer(BlockEntityRendererProvider.Context context) {
        // 懒加载，理由同 TileEntityAltarRenderer
    }

    @Override
    public void render(TileEntityShrine shrine, float partialTick, PoseStack poseStack, MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn) {
        Direction facing = shrine.getBlockState().getValue(BlockShrine.FACING);
        poseStack.pushPose();
        AnimatedGeoModel model = BedrockBlockModels.get("shrine");
        poseStack.translate(0.5, BedrockBlockModels.floorOffset("shrine"), 0.5);
        poseStack.mulPose(Vector3f.YN.rotationDegrees(180 - facing.get2DDataValue() * 90));
        GeoBlockRenderer.render(model, TEXTURE, poseStack, bufferIn, combinedLightIn, combinedOverlayIn,
                RenderType.entityCutoutNoCull(TEXTURE));
        poseStack.popPose();

        Level level = shrine.getLevel();
        if (level == null) {
            return;
        }
        ItemStack stack = shrine.getStorageItem();
        poseStack.pushPose();
        poseStack.translate(0.5, 0.85, 0.5);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        float deg = (level.getGameTime() + partialTick) % 360;
        poseStack.mulPose(Vector3f.YN.rotationDegrees(deg));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemTransforms.TransformType.GROUND, combinedLightIn, combinedOverlayIn, poseStack, bufferIn, 0);
        poseStack.popPose();
    }
}

package com.github.tartaricacid.touhoulittlemaid.client.model;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 「生成？？？」占位物品的扁平图标，逐字对应上游 {@code client/model/EntityPlaceholderModel}。
 *
 * <p>上游用基岩模型库的「单南面」方块（{@code BedrockCubePerFace} + {@code singleSouthFace()}）
 * 造一块厚度为 0、只用一张 16×16 贴图的板。本移植版没有那个库，做法与
 * {@link PicnicBasketIconModel} 完全一致：用原版 {@code addBox} 造同样一块厚度为 0 的板，
 * 六面共用同一段 UV，正对摄像机时看到的仍然是同一张图。</p>
 *
 * <p>坐标与上游逐字相同（{@code 8, 24, -10} + {@code -16,-16,9.5} + 16×16），
 * 这样 {@code models/item/entity_placeholder.png} 里那套 {@code display} 变换
 * （从 1.20.1 抄来的，已经调好）可以原样复用。</p>
 */
public class EntityPlaceholderIconModel extends EntityModel<Entity> {
    /**
     * 图层 id 里那段 "main" 与本移植版其它模型保持一致（见 {@link PicnicBasketIconModel}）。
     */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TouhouLittleMaid.MOD_ID, "main"), "entity_placeholder_icon");

    private final ModelPart quad;

    public EntityPlaceholderIconModel(ModelPart root) {
        this.quad = root.getChild("quad");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition root = meshdefinition.getRoot();
        PartDefinition quad = root.addOrReplaceChild("quad", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-16.0F, -16.0F, 9.5F, 16.0F, 16.0F, 0.0F),
                PartPose.offset(8.0F, 24.0F, -10.0F));
        return LayerDefinition.create(meshdefinition, 16, 16);
    }

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // 静态图标，没有动画
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.quad.render(poseStack, buffer, packedLight, packedOverlay);
    }
}


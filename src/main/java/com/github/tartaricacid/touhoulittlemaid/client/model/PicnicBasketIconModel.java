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
 * 野餐篮在<b>图形界面</b>里的扁平图标，对应上游的 {@code EntityPlaceholderModel}。
 *
 * <p>上游用的是基岩模型库里的「单南面」方块（{@code BedrockCubePerFace} + {@code singleSouthFace()}），
 * 也就是「一块厚度为 0、只用一张 16×16 贴图的板」。本移植版没有那个库
 * （方块模型全部改写成原版 {@code ModelPart}），所以这里用原版
 * {@code addBox} 造同样一块厚度为 0 的板 —— 它有六个面，但六面共用同一段 UV，
 * 而这块板正对着摄像机，看到的仍然是同一张图。</p>
 *
 * <p>坐标与上游逐字相同（{@code 8, 24, -10} + {@code -16,-16,9.5} + 16×16），
 * 这样 {@code models/item/picnic_basket.json} 里那套 {@code display} 变换
 * （在 1.20.1 上已经调好的）可以原样复用。</p>
 */
public class PicnicBasketIconModel extends EntityModel<Entity> {
    /**
     * 图层 id 里那段 "main" 与本移植版其它方块模型保持一致（见 {@code KeyboardModel}）。
     */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TouhouLittleMaid.MOD_ID, "main"), "picnic_basket_icon");

    private final ModelPart quad;

    public PicnicBasketIconModel(ModelPart root) {
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
        this.quad.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}


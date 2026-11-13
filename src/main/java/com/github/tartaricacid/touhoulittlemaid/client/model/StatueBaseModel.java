package com.github.tartaricacid.touhoulittlemaid.client.model;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 雕像 / 手办脚下的台座模型，逐字移植自上游 {@code client/model/StatueBaseModel}。
 *
 * <p>它有两层：底下一块 48×2×48 的方形台面，上面叠一圈 45×3×45 的台阶；
 * 四边各插一面 45×7 的矮旗（{@code bone4} / {@code bone8} / {@code bone12} /
 * {@code bone16}，分别绕 Y 轴转 0° / -90° / -180° / -270°）。旗子内部再挂两根
 * 2×8 的小柱当作旗杆装饰。</p>
 *
 * <p><b>为什么坐标这么大：</b>整个模型在 {@link #renderToBuffer} 里统一乘
 * {@code 0.325}，所以建模时用的是「放大 3 倍左右」的坐标，读起来会看到
 * {@code 103.8422} 这种数字。不要把它们当成笔误改成 1/8 格，否则台座会缩成一小坨。</p>
 *
 * <p>贴图 {@code textures/entity/statue_base.png} 是 256×256，UV 分布在
 * {@code (0,0)}（台面）、{@code (0,53)}（台阶）、{@code (0,104)}（旗面）三块区域。</p>
 */
public class StatueBaseModel extends EntityModel<Entity> {
    /**
     * 模型层。上游用的 {@code new ModelLayerLocation(rl("main"), "statue_base")}，
     * 第一个参数是「图层组」、第二个是组内名字，注册时必须原样照抄，
     * 否则 {@code EntityModelSet#bakeLayer} 找不到。
     */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TouhouLittleMaid.MOD_ID, "main"), "statue_base");

    private final ModelPart bone;

    public StatueBaseModel(ModelPart root) {
        this.bone = root.getChild("bone");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bone = partdefinition.addOrReplaceChild("bone",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-24.0F, -2.0F, -19.0F, 48.0F, 2.0F, 48.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 53).addBox(-22.5F, -10.5F, -17.5F, 45.0F, 3.0F, 45.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, -5.0F));

        PartDefinition bone2 = bone.addOrReplaceChild("bone2", CubeListBuilder.create(),
                PartPose.offset(0.0F, -0.1F, 5.0F));

        PartDefinition bone3 = bone2.addOrReplaceChild("bone3", CubeListBuilder.create(),
                PartPose.offset(0.0F, -111.0F, 0.0F));

        PartDefinition bone4 = bone3.addOrReplaceChild("bone4",
                CubeListBuilder.create()
                        .texOffs(0, 104).addBox(-22.5F, 103.8422F, 0.0F, 45.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 4.4705F, 0.1745F, 0.0F, 0.0F));

        bone4.addOrReplaceChild("bone5",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, 105.0F, -0.001F, 2.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.5F, 0.3422F, 0.0F, 0.0F, 0.0F, 0.1719F));

        bone4.addOrReplaceChild("bone6",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(0.0F, 105.0F, 0.001F, 2.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.5F, 0.3422F, 0.0F, 0.0F, 0.0F, -0.1719F));

        PartDefinition bone7 = bone2.addOrReplaceChild("bone7", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, -111.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition bone8 = bone7.addOrReplaceChild("bone8",
                CubeListBuilder.create()
                        .texOffs(0, 104).addBox(-22.5F, 103.8422F, 0.0F, 45.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 4.4705F, 0.1745F, 0.0F, 0.0F));

        bone8.addOrReplaceChild("bone9",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, 105.0F, -0.001F, 2.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.5F, 0.3422F, 0.0F, 0.0F, 0.0F, 0.1719F));

        bone8.addOrReplaceChild("bone10",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(0.0F, 105.0F, 0.001F, 2.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.5F, 0.3422F, 0.0F, 0.0F, 0.0F, -0.1719F));

        PartDefinition bone11 = bone2.addOrReplaceChild("bone11", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, -111.0F, 0.0F, 0.0F, -3.1416F, 0.0F));

        PartDefinition bone12 = bone11.addOrReplaceChild("bone12",
                CubeListBuilder.create()
                        .texOffs(0, 104).addBox(-22.5F, 103.8422F, 0.0F, 45.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 4.4705F, 0.1745F, 0.0F, 0.0F));

        bone12.addOrReplaceChild("bone13",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, 105.0F, -0.001F, 2.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.5F, 0.3422F, 0.0F, 0.0F, 0.0F, 0.1719F));

        bone12.addOrReplaceChild("bone14",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(0.0F, 105.0F, 0.001F, 2.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.5F, 0.3422F, 0.0F, 0.0F, 0.0F, -0.1719F));

        PartDefinition bone15 = bone2.addOrReplaceChild("bone15", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, -111.0F, 0.0F, 0.0F, -4.7124F, 0.0F));

        PartDefinition bone16 = bone15.addOrReplaceChild("bone16",
                CubeListBuilder.create()
                        .texOffs(0, 104).addBox(-22.5F, 103.8422F, 0.0F, 45.0F, 7.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 4.4705F, 0.1745F, 0.0F, 0.0F));

        bone16.addOrReplaceChild("bone17",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, 105.0F, -0.001F, 2.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.5F, 0.3422F, 0.0F, 0.0F, 0.0F, 0.1719F));

        bone16.addOrReplaceChild("bone18",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(0.0F, 105.0F, 0.001F, 2.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.5F, 0.3422F, 0.0F, 0.0F, 0.0F, -0.1719F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // 台座是静物，没有动画
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        poseStack.scale(0.325F, 0.325F, 0.325F);
        this.bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}


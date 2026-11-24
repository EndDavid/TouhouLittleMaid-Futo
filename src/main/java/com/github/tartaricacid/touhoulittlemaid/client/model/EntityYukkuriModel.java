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
import net.minecraft.world.entity.monster.Slime;

/**
 * 油库里史莱姆的模型（把原版史莱姆换成「灵梦油库里」）。
 *
 * <p>移植自上游 1.18.2 的 {@code client/model/EntityYukkuriModel}。上游 1.20.1 换成了
 * bedrock JSON（{@code models/bedrock/entity/reimu_yukkuri.json}），但两边的几何是<b>逐盒等价</b>的：</p>
 *
 * <table border="1">
 *     <caption>UV 对照（1.18.2 手写网格 ⟷ 1.20.1 bedrock JSON）</caption>
 *     <tr><th>bone</th><th>uv / size</th></tr>
 *     <tr><td>bone 主体</td><td>(0,18) 8×8×8</td></tr>
 *     <tr><td>bone 外层</td><td>(0,0) 9×9×9</td></tr>
 *     <tr><td>bone 耳朵</td><td>(38,0) 2×4×2，inflate -0.35，其中一个 mirror</td></tr>
 *     <tr><td>bone 头饰</td><td>(0,0) 2×3×0</td></tr>
 *     <tr><td>bone2 / bone3</td><td>(48,14) 7×4×1，inflate -0.2，bone3 mirror</td></tr>
 *     <tr><td>bone4 / bone5</td><td>(50,20) 6×4×1，inflate -0.3，bone5 mirror</td></tr>
 *     <tr><td>bone6 / bone7</td><td>(48,0) 2×7×6，bone7 mirror</td></tr>
 *     <tr><td>bone8（尾巴）</td><td>(54,35) 4×9×1</td></tr>
 * </table>
 *
 * <p>贴图尺寸同为 64×64，所以上游 1.20.1 重绘的那张
 * {@code textures/bedrock/entity/reimu_yukkuri.png} 可以直接贴在这套网格上 ——
 * 本轮移植的「材质更改」正是换成了这一张（相对 baseline 里的旧
 * {@code textures/entity/yukkuri.png}，64×64 中有 577 个像素被重画）。</p>
 *
 * <p>之所以不直接搬 1.20.1 的 bedrock JSON：那需要先移植上游的实体 bedrock 模型加载器
 * （{@code SimpleBedrockModel} 那一套），而本仓库目前只有方块的 {@code BedrockBlockModels}。
 * 两套几何既然等价，用手写网格是成本更低、外观完全一致的等价替代。</p>
 */
public class EntityYukkuriModel extends EntityModel<Slime> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TouhouLittleMaid.MOD_ID, "main"), "yukkuri");

    private final ModelPart bone;

    public EntityYukkuriModel(ModelPart root) {
        this.bone = root.getChild("bone");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 主体：内层 8 立方 + 外层 9 立方（外层是那圈半透明的果冻壳）
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create()
                        .texOffs(0, 18).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-4.5F, -8.5F, -4.5F, 9.0F, 9.0F, 9.0F, new CubeDeformation(0.0F))
                        // 两只耳朵：右耳 mirror，左耳正常
                        .texOffs(38, 0).mirror()
                        .addBox(3.0F, -3.75F, -5.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.35F)).mirror(false)
                        .texOffs(38, 0).addBox(-5.0F, -3.75F, -5.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.35F))
                        // 头顶的蝴蝶结贴片
                        .texOffs(0, 0).addBox(-1.5F, -11.0F, -2.0F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // 四片裙边 / 丝带
        bone.addOrReplaceChild("bone2", CubeListBuilder.create()
                .texOffs(48, 14).addBox(-0.8195F, -2.0F, 0.1061F, 7.0F, 4.0F, 1.0F, new CubeDeformation(-0.2F)),
                PartPose.offsetAndRotation(0.0F, -6.5F, 4.0F, 0.0F, -0.1745F, -0.4363F));
        bone.addOrReplaceChild("bone3", CubeListBuilder.create()
                        .texOffs(48, 14).mirror()
                        .addBox(-6.1805F, -2.0F, 0.1061F, 7.0F, 4.0F, 1.0F, new CubeDeformation(-0.2F)).mirror(false),
                PartPose.offsetAndRotation(0.0F, -6.5F, 4.0F, 0.0F, 0.1745F, 0.4363F));
        bone.addOrReplaceChild("bone4", CubeListBuilder.create()
                .texOffs(50, 20).addBox(-0.3195F, -2.0F, 0.1061F, 6.0F, 4.0F, 1.0F, new CubeDeformation(-0.3F)),
                PartPose.offsetAndRotation(0.0F, -6.5F, 4.0F, 0.0F, -0.1745F, 2.7053F));
        bone.addOrReplaceChild("bone5", CubeListBuilder.create()
                        .texOffs(50, 20).mirror()
                        .addBox(-5.6805F, -2.0F, 0.1061F, 6.0F, 4.0F, 1.0F, new CubeDeformation(-0.3F)).mirror(false),
                PartPose.offsetAndRotation(0.0F, -6.5F, 4.0F, 0.0F, 0.1745F, -2.7053F));

        // 双手
        bone.addOrReplaceChild("bone6", CubeListBuilder.create()
                .texOffs(48, 0).addBox(0.1F, -3.5F, -3.3F, 2.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.0F, -3.0F, 1.0F, 0.0F, 0.0F, -0.2618F));
        bone.addOrReplaceChild("bone7", CubeListBuilder.create()
                        .texOffs(48, 0).mirror()
                        .addBox(-2.1F, -3.5F, -3.3F, 2.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-3.0F, -3.0F, 1.0F, 0.0F, 0.0F, 0.2618F));

        // 尾巴
        bone.addOrReplaceChild("bone8", CubeListBuilder.create()
                .texOffs(54, 35).addBox(-2.0F, -0.6042F, -1.0909F, 4.0F, 9.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -8.0F, 6.0F, 0.1745F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** 油库里是「一坨」，没有行走动画（与上游一致）。 */
    @Override
    public void setupAnim(Slime slime, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        this.bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}


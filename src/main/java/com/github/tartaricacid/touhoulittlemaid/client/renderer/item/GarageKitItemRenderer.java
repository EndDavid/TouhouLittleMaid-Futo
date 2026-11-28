package com.github.tartaricacid.touhoulittlemaid.client.renderer.item;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.model.StatueBaseModel;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.item.ItemGarageKit;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * 手办物品图标的渲染器，逐字移植自上游 {@code TileEntityItemStackGarageKitRenderer}。
 *
 * <p>Fabric 没有 {@code IItemRenderProperties#getItemStackRenderer}，等价入口是
 * {@link BuiltinItemRendererRegistry}（坐垫的 {@code ChairItemRenderer} 用的是同一套）。
 * 前提是 {@code models/item/garage_kit.json} 里写着 {@code "parent": "builtin/entity"}，
 * 否则这个渲染器永远不会被调用 —— 资源里那份已经是了。</p>
 *
 * <h2>画什么</h2>
 * <p><b>先画台座，再画实体</b>，两段各有各的变换，与上游一致：</p>
 * <table border="1">
 *     <caption>两段变换</caption>
 *     <tr><th>段</th><th>变换</th></tr>
 *     <tr><td>台座</td><td>{@code scale(0.5)} → {@code translate(1, 0.5, 1)} → {@code ZN(180)}</td></tr>
 *     <tr><td>实体</td><td>{@code scale(0.5)} → {@code translate(1, 0.21328125, 1)} → {@code YP(180)}</td></tr>
 * </table>
 *
 * <p><b>上一版漏画了台座</b>（注释还写反了），结果是「实体一旦画不出来，图标就是一片空白」。
 * 台座补回来之后，即使实体渲染失败，物品栏里也仍然能看到一个台座 —— 这既是上游行为，
 * 也顺手给了这个图标一个兜底。</p>
 */
public class GarageKitItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/statue_base.png");

    /**
     * 台座模型。{@code BuiltinItemRenderer} 的接口不提供 {@code EntityModelSet}，
     * 所以第一次渲染时自己去 {@code Minecraft#getEntityModels()} 里 bake 一份并缓存。
     *
     * <p>不能在构造器里 bake：这个渲染器是在客户端初始化阶段 new 出来的，
     * 那时模型层可能还没准备。</p>
     */
    @Nullable
    private StatueBaseModel baseModel;


    @Override
    public void render(ItemStack stack, ItemTransforms.TransformType mode, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }

        EntityCacheUtil.onClientLevelChange(level);
        CompoundTag data = ItemGarageKit.getMaidData(stack);

        // ---- 台座：先画，与实体分开 push/pop，两段变换互不影响 ----
        if (this.baseModel == null) {
            this.baseModel = new StatueBaseModel(
                    minecraft.getEntityModels().bakeLayer(StatueBaseModel.LAYER));
        }
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.translate(1, 0.5, 1);
        // 台座是「Y 向下」建的（EntityModel 约定），翻一次
        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.baseModel.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        if (data.isEmpty()) {
            return;
        }
        EntityType.byString(data.getString("id")).ifPresent(type -> {
            try {
                this.renderEntity(poseStack, bufferSource, packedLight, data, level, type);
            } catch (Throwable e) {
                // BuiltinItemRenderer 抛异常没有任何东西接，会直接把游戏带下去。
                // 手办的失败面比坐垫宽得多（NBT 来自玩家的箱子里），所以这里必须兜住。
                TouhouLittleMaid.LOGGER.error("[Futo] Garage kit item renderer failed; pedestal only", e);
            }
        });
    }

    private void renderEntity(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                              CompoundTag data, Level level, EntityType<?> type) throws ExecutionException {
        Entity entity;
        if (type.equals(InitEntities.MAID)) {
            // 按 NBT 的【值】缓存，不能用 stack 或 stack.copy()。
            // ItemStack 在 1.18.2 没有重写 equals/hashCode，身份语义 + 每帧新对象 =
            // 每次渲染都「未命中」→ 每帧新建一只带 GeckoLib 控制器的 EntityMaid →
            // 内存爆掉、GC 打满 CPU、游戏卡死。详见 EntityCacheUtil.GARAGE_KIT_CACHE。
            entity = EntityCacheUtil.GARAGE_KIT_CACHE.get(data.toString(),
                    () -> EntityCacheUtil.createMaidEntity(level));
        } else {
            entity = EntityCacheUtil.ENTITY_CACHE.get(type, () -> {
                Entity e = type.create(level);
                return Objects.requireNonNullElseGet(e, () -> EntityCacheUtil.createMaidEntity(level));
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
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180));
        EntityRenderDispatcher render = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean isShowHitBox = render.shouldRenderHitBoxes();
        render.setRenderHitBoxes(false);
        // 与上游一致：实体在「花式」画法下渲染，否则某些半透明层会消失
        RenderSystem.runAsFancy(() -> render.render(entity, 0, 0, 0, 0, 0, poseStack, bufferSource, packedLight));
        render.setRenderHitBoxes(isShowHitBox);
        poseStack.popPose();
    }
}


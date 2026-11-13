package com.github.tartaricacid.touhoulittlemaid.client.renderer.item;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.item.ItemGarageKit;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * 手办物品图标的渲染器，对应上游 {@code TileEntityItemStackGarageKitRenderer}。
 *
 * <p>Fabric 没有 {@code IItemRenderProperties#getItemStackRenderer}，等价入口是
 * {@link BuiltinItemRendererRegistry}（坐垫的 {@code ChairItemRenderer} 用的是同一套）。
 * 前提是 {@code models/item/garage_kit.json} 里写着 {@code "parent": "builtin/entity"}，
 * 否则这个渲染器永远不会被调用 —— 资源里那份已经是了。</p>
 *
 * <h2>摆放数值</h2>
 * <p>{@code scale(0.5)} + {@code translate(1, 0.21328125, 1)} 与上游一致。
 * 注意台座那一段是 {@code translate(1, 0.5, 1)}（比实体高），
 * 因为台座模型自己带了 {@code ZN(180)} 的翻转。</p>
 */
public class GarageKitItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    @Override
    public void render(ItemStack stack, ItemTransforms.TransformType mode, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }
        CompoundTag data = ItemGarageKit.getMaidData(stack);
        if (data.isEmpty()) {
            return;
        }
        EntityCacheUtil.onClientLevelChange(level);

        // 图标里没有方块实体，所以台座不画 —— 上游的物品渲染器只画实体。
        EntityType.byString(data.getString("id")).ifPresent(type -> {
            try {
                this.renderEntity(stack, poseStack, bufferSource, packedLight, data, level, type);
            } catch (Throwable e) {
                // BuiltinItemRenderer 抛异常没有任何东西接，会直接把游戏带下去。
                // 手办的失败面比坐垫宽得多（NBT 来自玩家的箱子里），所以这里必须兜住。
                TouhouLittleMaid.LOGGER.error("[Futo] Garage kit item renderer failed; skipping this item", e);
            }
        });
    }

    private void renderEntity(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedLight, CompoundTag data, Level level, EntityType<?> type)
            throws ExecutionException {
        Entity entity;
        if (type.equals(InitEntities.MAID)) {
            // 按 NBT 的【值】缓存，不能用 stack.copy()。
            // ItemStack 没有重写 equals/hashCode，身份语义 + 每帧新建 = 每次渲染都
            // 「未命中」→ 每帧新建一只带 GeckoLib 控制器的 EntityMaid → 内存爆掉、
            // GC 打满 CPU、游戏卡死。详见 EntityCacheUtil.GARAGE_KIT_CACHE 的注释。
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
            maid.renderState = com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState.GARAGE_KIT;
        }

        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.translate(1, 0.21328125, 1);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180));
        EntityRenderDispatcher render = minecraft().getEntityRenderDispatcher();
        boolean isShowHitBox = render.shouldRenderHitBoxes();
        render.setRenderHitBoxes(false);
        render.render(entity, 0, 0, 0, 0, 0, poseStack, bufferSource, packedLight);
        render.setRenderHitBoxes(isShowHitBox);
        poseStack.popPose();
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}

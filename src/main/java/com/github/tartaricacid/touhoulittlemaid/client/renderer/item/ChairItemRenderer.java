package com.github.tartaricacid.touhoulittlemaid.client.renderer.item;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityChairRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.resource.ChairModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.item.ItemChair;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 坐垫物品的 3D 图标渲染器（上游 {@code TileEntityItemStackChairRenderer} 的替代品）。
 *
 * <p><b>差异说明。</b>上游用 Forge 的 {@code IItemRenderProperties#getItemStackRenderer()}
 * 挂一个 {@code BlockEntityWithoutLevelRenderer}。Fabric 1.18.2 没有那个机制，
 * 对应物是 {@code BuiltinItemRendererRegistry}：注册之后原版 {@code ItemRenderer}
 * 会跳过烘焙模型、直接调用这里的 {@link #render}，因此物品栏里看到的就是一只真的坐垫。</p>
 *
 * <p>坐标与缩放逐字照抄上游：先按 {@code render_item_scale} 整体缩放，再把实体摆在
 * {@code (1/scale - 0.125, 0.25, 0.75)} —— 那三个数字是在模型空间里手调出来的居中值，
 * 换掉就会偏。渲染期间把 {@link EntityChairRenderer#renderHitBox} 关掉，否则图标里会多一个红框。</p>
 */
public final class ChairItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    /**
     * 复用的假实体。物品渲染是即时模式，一帧内不会重入同一个物品槽位两次以上，
     * 因此单实例足够（上游用 {@code EntityCacheUtil} 缓存也是同样的理由）。
     */
    @Nullable
    private EntityChair cachedChair;
    @Nullable
    private Level cachedLevel;
    /**
     * 一次性熔断标志。
     *
     * <p>物品图标渲染抛异常 = 整个游戏崩（它挂在 {@code ItemRenderer} 里，没有任何一层会接住）。
     * 图标画不出来顶多是「物品栏里少个模型」，绝不该升级成崩溃 —— 所以这里接住一次、
     * 打一条带完整堆栈的 ERROR、然后把 3D 图标整个关掉。日志里那一条是留给排查的线索，
     * 不会静默吞掉。</p>
     */
    private boolean broken = false;

    @Override
    public void render(ItemStack stack, ItemTransforms.TransformType mode, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (this.broken) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }
        try {
            renderChair(minecraft, stack, poseStack, bufferSource, packedLight, level);
        } catch (Throwable e) {
            this.broken = true;
            TouhouLittleMaid.LOGGER.error(
                    "[Futo] Chair item icon renderer failed; the 3D icon is disabled for this session", e);
        }
    }

    private void renderChair(Minecraft minecraft, ItemStack stack, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, Level level) {
        EntityChair chair = getChair(level);
        if (chair == null) {
            return;
        }
        ItemChair.Data data = ItemChair.getData(stack);
        chair.setModelId(data.getModelId());
        float scale = ChairModelRegistry.getModelRenderItemScale(data.getModelId());

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        boolean oldHitBox = EntityChairRenderer.renderHitBox;
        EntityChairRenderer.renderHitBox = false;
        RenderSystem.runAsFancy(() -> minecraft.getEntityRenderDispatcher().render(chair,
                1.0 / scale - 0.125, 0.25, 0.75, 0.0F, 0.0F,
                poseStack, bufferSource, packedLight));
        EntityChairRenderer.renderHitBox = oldHitBox;
        poseStack.popPose();
    }

    @Nullable
    private EntityChair getChair(Level level) {
        // 换过世界就得重建：实体身上挂着 Level 引用
        if (this.cachedChair == null || this.cachedLevel != level) {
            // 与上游一致走 EntityType#create —— 它才能真正走工厂与（Fabric 侧的）属性表注入，
            // 直接 new 会绕过这些钩子。
            EntityChair chair = InitEntities.CHAIR.create(level);
            this.cachedChair = chair != null ? chair : new EntityChair(level);
            this.cachedLevel = level;
        }
        return this.cachedChair;
    }
}

package com.github.tartaricacid.touhoulittlemaid.client.init;

import com.github.tartaricacid.touhoulittlemaid.client.model.StatueBaseModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.item.GarageKitItemRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityGarageKitRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityStatueRenderer;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;

/**
 * 雕像 / 手办的客户端注册。
 *
 * <p><b>为什么单独一个 {@code ClientModInitializer} 而不是塞进
 * {@code TouhouLittleMaidFutoClient}：</b>那个类有 200 多行、被改过很多次，
 * 往里插代码要靠中文锚点定位，风险高（本移植版已经因为大文件插入丢过两次方法）。
 * 新增一个入口点只改 {@code fabric.mod.json} 的一行数组，是纯追加，两边都不碰。</p>
 *
 * <p>注册三样东西：</p>
 * <ol>
 *     <li>模型层 {@link StatueBaseModel#LAYER} —— 台座几何，两个渲染器都要 bake；</li>
 *     <li>两个方块实体渲染器；</li>
 *     <li>手办物品的 {@code BuiltinItemRenderer}（Fabric 没有 BEWLR，
 *         物品栏里的 3D 图标靠这个）。</li>
 * </ol>
 */
public final class InitStatueClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(StatueBaseModel.LAYER, StatueBaseModel::createBodyLayer);

        BlockEntityRendererRegistry.register(InitBlocks.STATUE_TE, TileEntityStatueRenderer::new);
        BlockEntityRendererRegistry.register(InitBlocks.GARAGE_KIT_TE, TileEntityGarageKitRenderer::new);

        BuiltinItemRendererRegistry.INSTANCE.register(InitItems.GARAGE_KIT, new GarageKitItemRenderer());
    }
}


package com.github.tartaricacid.touhoulittlemaid.client.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.item.InHandModelUnbaked;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.VanillaConfig;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.model.ModelProviderContext;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 原版物品图标替换（上游 1.20.1 {@code client/init/InitSpecialItemRender}）。
 */
public final class InitSpecialItemRender {
    private static final ResourceLocation POINT_ITEM =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "item/point_item");
    private static final ResourceLocation LIFE_POINT =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "item/life_point");

    private static final ModelResourceLocation XP_BOTTLE_INVENTORY =
            new ModelResourceLocation(new ResourceLocation("minecraft", "experience_bottle"), "inventory");
    private static final ModelResourceLocation TOTEM_INVENTORY =
            new ModelResourceLocation(new ResourceLocation("minecraft", "totem_of_undying"), "inventory");

    /**
     * 有 {@code *_in_hand} 手持模型的物品（逐项对应上游 {@code addInHandModel} 的 5 个调用）。
     *
     * <p>值是物品 id 的 path，对应的两个模型 id 分别是
     * {@code touhou_little_maid:item/<path>} 与 {@code touhou_little_maid:item/<path>_in_hand}。</p>
     *
     * <h2>为什么原版需要这种「单独的 in_hand 模型文件」</h2>
     *
     * <p>反编译 {@code ModelBakery} 的构造函数可以看到原版自己就是这么干的：</p>
     *
     * <pre>
     * // 顶层模型要显式登记才会被烘焙、贴图才会进图集
     * this.loadTopLevel(..., "blocks");
     * this.loadTopLevel(..., "items", "inventory");
     * // ↓ 只有这两件被硬编码进来
     * this.loadTopLevel(new ModelResourceLocation("minecraft:trident_in_hand#inventory"));
     * this.loadTopLevel(new ModelResourceLocation("minecraft:spyglass_in_hand#inventory"));
     * </pre>
     *
     * <p>三叉戟 / 望远镜的 3D 手持模型就是这样被「额外塞进顶层模型表」的 ——
     * 它们不属于任何物品 id 的默认模型占位，靠上面的显式调用才被烘焙，
     * 贴图也才会在拼接阶段进入图集。本移植版用 {@link InHandModelUnbaked}
     * 把这件事做在 Fabric 的 {@code ModelVariantProvider} 里，效果等价。</p>
     *
     * <h2>当前生效状态</h2>
     *
     * <table border="1">
     *     <caption>五个手持模型</caption>
     *     <tr><th>path</th><th>物品是否已注册</th><th>说明</th></tr>
     *     <tr><td>{@code hakurei_gohei}</td><td>是</td><td>博丽御币</td></tr>
     *     <tr><td>{@code sanae_gohei}</td><td>是</td><td>东风谷御币</td></tr>
     *     <tr><td>{@code extinguisher}</td><td>是</td><td>灭火器</td></tr>
     *     <tr><td>{@code camera}</td><td>是</td><td>文文的相机（拍照 → 照片）</td></tr>
     *     <tr><td>{@code maid_beacon}</td><td>是</td><td>神社庭灯（方块 + 方块实体 + GUI，见 {@code block/BlockMaidBeacon}）</td></tr>
     * </table>
     *
     * <p>五项全部生效。{@code maid_beacon} 是最晚补齐的一项：它的手持模型引用的是
     * {@code block/maid_beacon} 这张方块贴图（{@code maid_beacon_in_hand.json} 里的
     * {@code "0"} 槽），而不是 {@code item/maid_beacon}；平面图标才是后者。
     * 这两张图能同时进图集，靠的是 {@link InHandModelUnbaked} 的
     * {@code getMaterials()} 把两套材质都报给 {@code ModelBakery}。</p>
     */
    private static final String[] IN_HAND_ITEMS = {
            "hakurei_gohei",
            "sanae_gohei",
            "extinguisher",
            "camera",
            "maid_beacon"
    };

    private InitSpecialItemRender() {
    }

    public static void init() {
        ModelLoadingRegistry.INSTANCE.registerVariantProvider(manager -> (modelId, context) -> {
            if (XP_BOTTLE_INVENTORY.equals(modelId) && VanillaConfig.replaceXpBottleTexture()) {
                return context.loadModel(POINT_ITEM);
            }
            if (TOTEM_INVENTORY.equals(modelId) && VanillaConfig.replaceTotemTexture()) {
                return context.loadModel(LIFE_POINT);
            }
            return loadInHandModel(modelId, context);
        });
        TouhouLittleMaid.LOGGER.debug("[Futo] special item render registered (xp bottle / totem / {} in-hand models)",
                IN_HAND_ITEMS.length);
    }

    /**
     * 若这个 {@code ModelResourceLocation} 属于「有手持模型」的物品，返回包装器。
     *
     * @return {@code null} 表示不归本模组管，交回原版处理
     */
    @Nullable
    private static UnbakedModel loadInHandModel(ModelResourceLocation modelId, ModelProviderContext context) {
        // 只处理 <id>#inventory；方块状态那些变体一律放过
        if (!"inventory".equals(modelId.getVariant())) {
            return null;
        }
        for (String path : IN_HAND_ITEMS) {
            // 用 namespace + path 直接比：ModelResourceLocation 覆写了 equals（还比 variant），
            // 而这里只想比「物品 id」本身，所以不走 equals。
            if (!TouhouLittleMaid.MOD_ID.equals(modelId.getNamespace()) || !path.equals(modelId.getPath())) {
                continue;
            }
            ResourceLocation flatId = new ResourceLocation(TouhouLittleMaid.MOD_ID, "item/" + path);
            ResourceLocation handId = new ResourceLocation(TouhouLittleMaid.MOD_ID, "item/" + path + "_in_hand");
            UnbakedModel flat = context.loadModel(flatId);
            UnbakedModel hand = context.loadModel(handId);
            if (flat == null || hand == null) {
                // 资源缺失就退回原版行为，千万不要返回半个包装器
                TouhouLittleMaid.LOGGER.warn("[Futo] missing model for in-hand gohei {} (flat={}, hand={})",
                        path, flat != null, hand != null);
                return null;
            }
            return new InHandModelUnbaked(flatId, handId, flat, hand);
        }
        return null;
    }

    /**
     * 让编译器知道 {@code Items} 真的被用到（保持与上游一致的「只处理这两件物品」语义）。
     */
    public static boolean isReplaced(net.minecraft.world.item.Item item) {
        return item == Items.EXPERIENCE_BOTTLE || item == Items.TOTEM_OF_UNDYING;
    }
}

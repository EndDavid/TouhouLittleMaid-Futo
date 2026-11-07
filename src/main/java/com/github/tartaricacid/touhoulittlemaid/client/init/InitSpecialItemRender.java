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
 *
 * <p>上游做的事很简单：把 {@code minecraft:experience_bottle} 的
 * {@code #inventory} 模型换成 {@code touhou_little_maid:item/point_item}
 * （一张蓝色「点」的贴图），不死图腾换成 {@code item/life_point}。
 * 两者都由 {@code VanillaConfig} 里的开关控制。</p>
 *
 * <h2>为什么 1.18.2 要用另一套 API</h2>
 *
 * <p>上游走 Forge 的 {@code ModelEvent.BakingCompleted}：等原版把所有模型<b>烘焙完</b>，
 * 再往 {@code bakedTopLevelModels} 这张表里塞一个 {@code ReplaceableBakedModel} 包装器
 * （运行时按配置决定用哪个模型）。</p>
 *
 * <p>Fabric 1.18.2 没有这个事件，但有等价且更靠前的钩子：
 * {@link ModelLoadingRegistry#registerVariantProvider} —— 它在<b>加载阶段</b>就允许我们
 * 把一个 {@code ModelResourceLocation} 换成另一个 {@code UnbakedModel}。
 * 换的是「读哪个 JSON」，不是「烘焙好的模型」，所以：</p>
 *
 * <ul>
 *     <li>不需要写 {@code ReplaceableBakedModel} 那种把十几个方法全部转发的包装类；</li>
 *     <li>代价是<b>只能在加载时决定</b>，改配置要重进游戏（或 F3+T）才生效。
 *         上游的包装器是每帧读配置的，这里做不到 —— 但图标替换本来也不需要在游戏内热切换。</li>
 * </ul>
 *
 * <p>注意 {@code ctx.loadModel(Identifier)} 收的是<b>模型 id</b>（含 {@code item/} 前缀），
 * 不是物品 id：{@code touhou_little_maid:item/point_item} 对应
 * {@code assets/touhou_little_maid/models/item/point_item.json}。</p>
 *
 * <h2>御币的手持模型（上游 {@code addInHandModel}）</h2>
 *
 * <p>上游在 {@code InitSpecialItemRender} 里注册了 5 个「手持模型」（{@code *_in_hand.json}）：
 * 博丽御币、东风谷御币、灭火器、相机、女仆信标。本移植版先移植<b>两个御币</b>
 * （这两件物品在当前端口里已经完整可用，且 {@code *_in_hand} 的贴图齐全）。</p>
 *
 * <p>它们的处理方式和「图标替换」不同：图标替换只是「换读哪个 JSON」，
 * 而手持模型需要在<b>同一个物品</b>上按视角切换两套模型，所以返回一个
 * {@link InHandModelUnbaked} 包装器，由它在烘焙时同时产出平面模型与 3D 模型。
 * 详见该类的注释。</p>
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
     * 有 {@code *_in_hand} 手持模型的物品（上游 {@code addInHandModel} 的前两项）。
     *
     * <p>值是物品 id 的 path，对应的两个模型 id 分别是
     * {@code touhou_little_maid:item/<path>} 与 {@code touhou_little_maid:item/<path>_in_hand}。</p>
     */
    private static final String[] IN_HAND_ITEMS = {"hakurei_gohei", "sanae_gohei"};

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
        TouhouLittleMaid.LOGGER.info("[Futo] special item render registered (xp bottle / totem / gohei in-hand)");
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

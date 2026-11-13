package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 手办物品，逐字移植自上游 {@code item/ItemGarageKit}。
 *
 * <h2>为什么它是个「物品」而不是普通 BlockItem 的语义</h2>
 * <p>它的 NBT 里装着<b>一整只女仆的存档数据</b>（{@code EntityInfo} 子标签），
 * 放下时原样抄进 {@code TileEntityGarageKit}。所以同一个 {@code garage_kit} 物品，
 * 每一份 NBT 都是不同的手办 —— 全都不一样。</p>
 *
 * <h2>与上游的差异</h2>
 * <table border="1">
 *     <caption>渲染与名称</caption>
 *     <tr><th>上游</th><th>本移植版</th></tr>
 *     <tr><td>{@code IItemRenderProperties#getItemStackRenderer}
 *         （Forge 独占）</td><td>物品图标改用 Fabric 的
 *         {@code BuiltinItemRendererRegistry}，见 {@code TouhouLittleMaidFutoClient}</td></tr>
 *     <tr><td>{@code getName} 里判断 {@code FMLEnvironment.dist == CLIENT}</td>
 *         <td>直接判断 {@code Minecraft.getInstance().level != null} 不行 ——
 *         {@code getName} 在服务端也会调，所以只认「模型注册表里有这条」</td></tr>
 * </table>
 */
public class ItemGarageKit extends BlockItem {
    private static final String ENTITY_INFO = "EntityInfo";
    private static final String DEFAULT_ENTITY_ID = "touhou_little_maid:maid";
    private static final String ENTITY_ID_TAG_NAME = "id";

    /**
     * 没有 NBT 时的兜底：默认模型（灵梦）的手办。
     *
     * <p>静态常量在类初始化时就建好，因此不能引用 {@code InitEntities.MAID}
     * （那时的注册顺序不确定），直接写字面量 —— 与上游一致。</p>
     */
    private static final CompoundTag DEFAULT_DATA = getDefaultData();

    public ItemGarageKit() {
        super(com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.GARAGE_KIT,
                new FabricItemSettings().tab(InitItems.GARAGE_KIT_TAB).stacksTo(1));
    }

    private static boolean hasMaidData(ItemStack stack) {
        return stack.hasTag() && !Objects.requireNonNull(stack.getTag()).getCompound(ENTITY_INFO).isEmpty();
    }

    /**
     * 取出物品里保存的实体数据；没有就返回默认的灵梦手办。
     */
    public static CompoundTag getMaidData(ItemStack stack) {
        if (hasMaidData(stack)) {
            return Objects.requireNonNull(stack.getTag()).getCompound(ENTITY_INFO);
        }
        return DEFAULT_DATA;
    }

    private static CompoundTag getDefaultData() {
        CompoundTag data = new CompoundTag();
        data.putString(ENTITY_ID_TAG_NAME, DEFAULT_ENTITY_ID);
        data.putString(EntityMaid.MODEL_ID_TAG, EntityMaid.DEFAULT_MODEL_ID);
        return data;
    }

    /**
     * 名字前缀 {@code block.touhou_little_maid.garage_kit.prefix}（「手办：」），
     * 后面接被手办化的东西的名字。
     *
     * <ul>
     *     <li>不是女仆（拿刷怪蛋换过的）→ 用那个实体的名字；</li>
     *     <li>是女仆 → 用 {@code ModelId} 去模型注册表查显示名，查不到退回物品默认名。</li>
     * </ul>
     *
     * <p>模型注册表由资源重载填充，服务端是空的 —— 所以服务端会落到「默认名」分支，
     * 这没有问题：物品名只在客户端显示。</p>
     */
    @Override
    public Component getName(ItemStack stack) {
        MutableComponent prefix = new TranslatableComponent("block.touhou_little_maid.garage_kit.prefix");
        CompoundTag data = getMaidData(stack);

        String entityId = data.getString(ENTITY_ID_TAG_NAME);
        // 如果是其他实体，那么不需要显示 model id
        if (!entityId.equals(DEFAULT_ENTITY_ID)) {
            EntityType<?> entityType = Registry.ENTITY_TYPE.get(
                    net.minecraft.resources.ResourceLocation.tryParse(entityId));
            if (entityType != null) {
                return prefix.append(entityType.getDescription());
            }
        }

        // 然后才是默认模型名。
        //
        // 注意这里必须走 ParseI18n，不能直接 new TranslatableComponent(info.getName())：
        // MaidModelInfo 的 name 默认值是【带大括号的】"{model.<ns>.<path>.name}"
        // （见 MaidModelInfo#decorate），大括号是 ParseI18n 自己的格式，把整串当翻译键
        // 传给 TranslatableComponent 只会原样画出 "{model.touhou_little_maid.xxx.name}"。
        // 这正是「所有雕像都没有译名」的原因。
        String modelId = data.getString(EntityMaid.MODEL_ID_TAG);
        com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo info =
                com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry.get(modelId);
        if (info != null && info.getName() != null && !info.getName().isEmpty()) {
            return prefix.append(ParseI18n.parse(info.getName()));
        }
        return super.getName(stack);
    }
}

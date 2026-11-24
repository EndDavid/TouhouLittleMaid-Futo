package com.github.tartaricacid.touhoulittlemaid.compat.rei;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.screen.MaidMainScreen;
import com.github.tartaricacid.touhoulittlemaid.compat.rei.altar.ReiAltarRecipeCategory;
import com.github.tartaricacid.touhoulittlemaid.compat.rei.altar.ReiAltarRecipeDisplay;
import com.github.tartaricacid.touhoulittlemaid.compat.rei.altar.ReiAltarRecipeMaker;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.CollapsibleEntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Touhou Little Maid 的 REI 客户端插件，移植自上游 {@code compat/rei/MaidREIClientPlugin}。
 *
 * <h2>它是怎么被加载的</h2>
 * <p>Fabric 没有 {@code @REIPluginClient} 注解（那是 REI 的 Forge 版用来扫描类路径的）。
 * Fabric 侧走入口点：本类登记在 {@code fabric.mod.json} 的 <b>{@code rei_client}</b> 入口点里，
 * REI 启动时自己去取这个入口点的实现。没装 REI 时没有任何东西会来取它，
 * 因此 <b>REI 是可选依赖</b> —— 我们不把它写进 {@code depends}。</p>
 *
 * <h2>做了什么</h2>
 * <ul>
 *     <li><b>祭坛合成分类</b>：把 {@code touhou_little_maid:altar_crafting} 的配方全量搬进 REI，
 *         并按祭坛九宫格摆成 3x2 的形状（见 {@link ReiAltarRecipeCategory}）；</li>
 *     <li><b>工作台标记</b>：把这套配方标成「需要御币」—— 祭坛是手持御币右击方块才激活的，
 *         所以 REI 的工作台提示放的是两种御币；</li>
 *     <li><b>条目折叠</b>：车库套件 / 坐垫在 REI 里各自把变体收进一组，否则坐垫那几十种
 *         模型会把物品列表刷屏；</li>
 *     <li><b>车库套件说明</b>：给车库套件加一条信息页，讲清楚「火烤 1x1 雕像可以转换」；</li>
 *     <li><b>排除区</b>：告诉 REI 女仆主界面占了多大的屏幕区域，别把物品面板画上去。</li>
 * </ul>
 *
 * <h2>与上游的差异（为什么少了三段）</h2>
 * <p>上游还注册了 {@code registerScreens}（背包界面里的「点一下切到配方」热区）与
 * {@code registerTransferHandlers}（把配方一键摆进工作台背包 / 熔炉背包）。这两段依赖
 * {@code CraftingTableBackpackContainerScreen} / {@code FurnaceBackpackContainerScreen} ——
 * 那是「打开女仆背包装备后切到工作台 / 熔炉」的界面，<b>本移植版还没有这套容器</b>
 * （我们的背包目前是挂在实体上的数据容器，没有对应的 GUI 与 Menu）。
 * 等那套界面做出来，把上游那 4 行 transfer handler 注册补回来即可，这里的结构不需要改。</p>
 */
public class MaidREIClientPlugin implements REIClientPlugin {
    public static final CategoryIdentifier<ReiAltarRecipeDisplay> ALTAR =
            CategoryIdentifier.of(TouhouLittleMaid.MOD_ID, "plugin/altar");

    @Override
    public void registerCollapsibleEntries(CollapsibleEntryRegistry registry) {
        List<Item> groupItems = new ArrayList<>();
        groupItems.add(InitItems.GARAGE_KIT);
        groupItems.add(InitItems.CHAIR);
        for (Item item : groupItems) {
            // 上游用 ForgeRegistries.ITEMS.getKey；Fabric 上就是 Registry.ITEM.getKey
            ResourceLocation groupId = Registry.ITEM.getKey(item);
            registry.group(groupId, item.getDescription(), VanillaEntryTypes.ITEM,
                    entryStack -> entryStack.getValue().is(item));
        }
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new ReiAltarRecipeCategory());
        // 祭坛不是方块界面的「工作台」：手持御币右击祭坛方块才会开合成，
        // 所以这里标的是两种御币（与上游一致）。
        registry.addWorkstations(ALTAR,
                EntryStacks.of(InitItems.SANAE_GOHEI), EntryStacks.of(InitItems.HAKUREI_GOHEI));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        ReiAltarRecipeMaker.registerAltarRecipes(registry);
        registerInfoDisplay(registry);
    }

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        // 女仆主界面是 256x256 的整块面板，会把 REI 的物品面板整个盖住。
        // 1.18.2 的原版没有 getGuiLeft()/getGuiTop()，所以在 MaidMainScreen 上开了两个访问器。
        zones.register(MaidMainScreen.class, screen -> {
            List<Rectangle> rectangles = new ArrayList<>(1);
            rectangles.add(new Rectangle(screen.futo$guiLeft(), screen.futo$guiTop(),
                    MaidMainContainer.GUI_WIDTH, MaidMainContainer.GUI_HEIGHT));
            return rectangles;
        });
    }

    private void registerInfoDisplay(DisplayRegistry registry) {
        registry.add(getInfoDisplay(InitItems.GARAGE_KIT,
                new TranslatableComponent("jei.touhou_little_maid.garage_kit.info")));
    }

    private DefaultInformationDisplay getInfoDisplay(Item item, Component component) {
        return DefaultInformationDisplay.createFromEntry(EntryStacks.of(item), item.getDescription())
                .line(component);
    }
}


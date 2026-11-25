package com.github.tartaricacid.touhoulittlemaid.compat.cloth;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import com.github.tartaricacid.touhoulittlemaid.config.GeneralConfig;
import com.github.tartaricacid.touhoulittlemaid.config.ServerConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.ChairConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.RenderConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.VanillaConfig;
import com.github.tartaricacid.touhoulittlemaid.util.version.TComponent;
import com.google.common.collect.Lists;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局配置界面，移植自上游 {@code compat/cloth/MenuIntegration}。
 *
 * <p>这是「按分类逐项列出所有配置」的那张大表：女仆 / 坐垫 / 杂项 / 原版替换 / 渲染，
 * 外加 {@link GlobalAIIntegration} 的 AI 一页。上游 1.18.2 Forge 版与 1.20.1 版内容一致，
 * 这里逐项对齐（144 条语言键在 {@code zh_cn.json} / {@code en_us.json} 里本来就齐全）。</p>
 *
 * <h2>与上游的差异（四处，都有原因）</h2>
 * <ol>
 *     <li><b>没有 {@code MaidMealRegConfigEvent.handleConfig(...)}。</b>
 *         上游在「正则餐食黑名单」保存时把正则展开成一张具体物品 id 表；
 *         本移植版改成在查询时按需匹配（{@code MaidMealManager#isRegexBlockList}），
 *         结果等价，所以那几个 {@code setSaveConsumer} 里只保留 {@code set(...)}。</li>
 *     <li><b>保存时要显式落盘。</b>上游靠 Forge 自己的配置界面顺手存盘；
 *         本移植版是自建界面，所以 {@link #getConfigBuilder()} 设了 {@code setSavingRunnable}
 *         去调 {@link GeneralConfig#save()} 与 {@link ServerConfig#save()}。</li>
 *     <li><b>没有 {@code registerModsPage()}。</b>它用的是 Forge 的
 *         {@code ConfigGuiHandler.ConfigGuiFactory}（模组列表里的「配置」按钮），
 *         Fabric 上对应的是 ModMenu 的 {@code modmenu} 入口点 —— 见
 *         {@code compat/modmenu/ModMenuApiImpl}。</li>
 *     <li><b>五项「子系统还没移植」的配置多了第二条 tooltip。</b>
 *         见 {@link #notPorted()}：悦灵替换 1 项、枪械射程 3 项、YSM 轮盘 1 项。
 *         条目本身保留（保证 {@code .toml} 与上游可互换），但明说它们暂不生效。</li>
 * </ol>
 *
 * <p><b>{@code Registry.ITEM.stream()} 的排序</b>：上游按 {@code Item::toString} 排，
 * 那个结果依赖对象哈希、每次启动可能不同。这里改成按注册名排，
 * 玩家看到的顺序才是稳定的（下拉框里找物品时这点很重要）。</p>
 */
public final class MenuIntegration {
    private MenuIntegration() {
    }

    public static ConfigBuilder getConfigBuilder() {
        ConfigBuilder root = ConfigBuilder.create().setTitle(new TextComponent("Touhou Little Maid"));
        root.setGlobalized(true);
        root.setGlobalizedExpanded(false);
        // 点「保存」时把内存里的值写回 toml。COMMON 与 SERVER 都要存 ——
        // 界面里两个 spec 的条目都有（SERVER 的那几项在 misc 里）。
        root.setSavingRunnable(() -> {
            GeneralConfig.save();
            ServerConfig.save();
        });
        ConfigEntryBuilder entryBuilder = root.entryBuilder();
        maidConfig(root, entryBuilder);
        chairConfig(root, entryBuilder);
        miscConfig(root, entryBuilder);
        vanillaConfig(root, entryBuilder);
        renderConfig(root, entryBuilder);
        GlobalAIIntegration.aiChat(root, entryBuilder);
        MinecraftForge.EVENT_BUS.post(new AddClothConfigEvent(root, entryBuilder));
        return root;
    }

    @SuppressWarnings("all")
    private static void maidConfig(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory maid = root.getOrCreateCategory(TComponent.translatable("config.touhou_little_maid.maid"));

        maid.addEntry(entryBuilder.startIntSlider(TComponent.translatable("config.touhou_little_maid.maid.global_maid_sound_frequency"), MaidConfig.GLOBAL_MAID_SOUND_FREQUENCY.get(), 0, 100)
                .setDefaultValue(100).setTooltip(TComponent.translatable("config.touhou_little_maid.maid.global_maid_sound_frequency.tooltip"))
                .setSaveConsumer(i -> MaidConfig.GLOBAL_MAID_SOUND_FREQUENCY.set(i)).build());

        maid.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.maid.global_maid_show_chat_bubble"), MaidConfig.GLOBAL_MAID_SHOW_CHAT_BUBBLE.get())
                .setDefaultValue(true).setTooltip(TComponent.translatable("config.touhou_little_maid.maid.global_maid_show_chat_bubble.tooltip"))
                .setSaveConsumer(MaidConfig.GLOBAL_MAID_SHOW_CHAT_BUBBLE::set).build());

        // 三个「物品 id 字符串」配置。上游用「物品对象」下拉框（能看图标），这里保持一致：
        // TopCellElementBuilder.ofItemObject 负责顶栏显示，CellCreatorBuilder.ofItemObject 负责列表项。
        maid.addEntry(entryBuilder.startDropdownMenu(TComponent.translatable("config.touhou_little_maid.maid.maid_tamed_item"),
                        DropdownMenuBuilder.TopCellElementBuilder.ofItemObject(
                                ForgeRegistries.ITEMS.getValue(new ResourceLocation(MaidConfig.MAID_TAMED_ITEM.get()))),
                        DropdownMenuBuilder.CellCreatorBuilder.ofItemObject())
                .setSelections(sortedItems())
                .setDefaultValue(Items.CAKE).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_tamed_item.tooltip"))
                .setSaveConsumer(item -> MaidConfig.MAID_TAMED_ITEM.set(itemId(item))).build());

        maid.addEntry(entryBuilder.startDropdownMenu(new TranslatableComponent("config.touhou_little_maid.maid.maid_temptation_item"),
                        DropdownMenuBuilder.TopCellElementBuilder.ofItemObject(
                                ForgeRegistries.ITEMS.getValue(new ResourceLocation(MaidConfig.MAID_TEMPTATION_ITEM.get()))),
                        DropdownMenuBuilder.CellCreatorBuilder.ofItemObject())
                .setSelections(sortedItems())
                .setDefaultValue(Items.CAKE).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_temptation_item.tooltip"))
                .setSaveConsumer(item -> MaidConfig.MAID_TEMPTATION_ITEM.set(itemId(item))).build());

        maid.addEntry(entryBuilder.startDropdownMenu(new TranslatableComponent("config.touhou_little_maid.maid.maid_ntr_item"),
                        DropdownMenuBuilder.TopCellElementBuilder.ofItemObject(
                                ForgeRegistries.ITEMS.getValue(new ResourceLocation(MaidConfig.MAID_NTR_ITEM.get()))),
                        DropdownMenuBuilder.CellCreatorBuilder.ofItemObject())
                .setSelections(sortedItems())
                .setDefaultValue(Items.STRUCTURE_VOID).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_ntr_item.tooltip"))
                .setSaveConsumer(item -> MaidConfig.MAID_NTR_ITEM.set(itemId(item))).build());

        maid.addEntry(entryBuilder.startIntSlider(new TranslatableComponent("config.touhou_little_maid.maid.maid_work_range"), MaidConfig.MAID_WORK_RANGE.get(), 3, 64)
                .setDefaultValue(12).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_work_range.tooltip"))
                .setSaveConsumer(i -> MaidConfig.MAID_WORK_RANGE.set(i)).build());

        maid.addEntry(entryBuilder.startIntSlider(new TranslatableComponent("config.touhou_little_maid.maid.maid_idle_range"), MaidConfig.MAID_IDLE_RANGE.get(), 3, 32)
                .setDefaultValue(6).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_idle_range.tooltip"))
                .setSaveConsumer(i -> MaidConfig.MAID_IDLE_RANGE.set(i)).build());

        maid.addEntry(entryBuilder.startIntSlider(new TranslatableComponent("config.touhou_little_maid.maid.maid_sleep_range"), MaidConfig.MAID_SLEEP_RANGE.get(), 3, 32)
                .setDefaultValue(6).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_sleep_range.tooltip"))
                .setSaveConsumer(i -> MaidConfig.MAID_SLEEP_RANGE.set(i)).build());

        maid.addEntry(entryBuilder.startIntSlider(new TranslatableComponent("config.touhou_little_maid.maid.maid_non_home_range"), MaidConfig.MAID_NON_HOME_RANGE.get(), 3, 32)
                .setDefaultValue(8).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_non_home_range.tooltip"))
                .setSaveConsumer(i -> MaidConfig.MAID_NON_HOME_RANGE.set(i)).build());

        maid.addEntry(entryBuilder.startIntSlider(TComponent.translatable("config.touhou_little_maid.maid.bow_range"), MaidConfig.BOW_RANGE.get(), 8, 192)
                .setDefaultValue(48).setTooltip(TComponent.translatable("config.touhou_little_maid.maid.bow_range.tooltip"))
                .setSaveConsumer(i -> MaidConfig.BOW_RANGE.set(i)).build());

        maid.addEntry(entryBuilder.startIntSlider(TComponent.translatable("config.touhou_little_maid.maid.cross_bow_range"), MaidConfig.CROSS_BOW_RANGE.get(), 8, 192)
                .setDefaultValue(64).setTooltip(TComponent.translatable("config.touhou_little_maid.maid.cross_bow_range.tooltip"))
                .setSaveConsumer(i -> MaidConfig.CROSS_BOW_RANGE.set(i)).build());

        maid.addEntry(entryBuilder.startIntSlider(TComponent.translatable("config.touhou_little_maid.maid.danmaku_range"), MaidConfig.DANMAKU_RANGE.get(), 8, 192)
                .setDefaultValue(64).setTooltip(TComponent.translatable("config.touhou_little_maid.maid.danmaku_range.tooltip"))
                .setSaveConsumer(i -> MaidConfig.DANMAKU_RANGE.set(i)).build());

        maid.addEntry(entryBuilder.startIntSlider(TComponent.translatable("config.touhou_little_maid.maid.trident_range"), MaidConfig.TRIDENT_RANGE.get(), 8, 192)
                .setDefaultValue(48).setTooltip(TComponent.translatable("config.touhou_little_maid.maid.trident_range.tooltip"))
                .setSaveConsumer(i -> MaidConfig.TRIDENT_RANGE.set(i)).build());

        maid.addEntry(entryBuilder.startIntField(new TranslatableComponent("config.touhou_little_maid.maid.feed_animal_max_number"), MaidConfig.FEED_ANIMAL_MAX_NUMBER.get())
                .setMin(6).setMax(65536).setDefaultValue(50).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.feed_animal_max_number.tooltip"))
                .setSaveConsumer(i -> MaidConfig.FEED_ANIMAL_MAX_NUMBER.set(i)).build());

        maid.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.maid.maid_change_model"), MaidConfig.MAID_CHANGE_MODEL.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_change_model.tooltip"))
                .setSaveConsumer(MaidConfig.MAID_CHANGE_MODEL::set).build());

        maid.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.maid.maid_gomoku_owner_limit"), MaidConfig.MAID_GOMOKU_OWNER_LIMIT.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_gomoku_owner_limit.tooltip"))
                .setSaveConsumer(MaidConfig.MAID_GOMOKU_OWNER_LIMIT::set).build());

        maid.addEntry(entryBuilder.startIntField(new TranslatableComponent("config.touhou_little_maid.maid.owner_max_maid_num"), MaidConfig.OWNER_MAX_MAID_NUM.get())
                .setDefaultValue(Integer.MAX_VALUE).setMin(0).setMax(Integer.MAX_VALUE)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.owner_max_maid_num.tooltip"))
                .setSaveConsumer(i -> MaidConfig.OWNER_MAX_MAID_NUM.set(i)).build());

        maid.addEntry(entryBuilder.startDoubleField(new TranslatableComponent("config.touhou_little_maid.maid.replace_allay_percent"), MaidConfig.REPLACE_ALLAY_PERCENT.get())
                .setDefaultValue(0.2).setMin(0).setMax(1)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.replace_allay_percent.tooltip"), notPorted())
                .setSaveConsumer(i -> MaidConfig.REPLACE_ALLAY_PERCENT.set(i)).build());

        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_backpack_blacklist"), MaidConfig.MAID_BACKPACK_BLACKLIST.get())
                .setDefaultValue(Lists.newArrayList())
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_backpack_blacklist.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_BACKPACK_BLACKLIST.set(l)).build());

        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_attack_ignore"), MaidConfig.MAID_ATTACK_IGNORE.get())
                .setDefaultValue(Lists.newArrayList("mekanism:robit"))
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_attack_ignore.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_ATTACK_IGNORE.set(l)).build());

        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_ranged_attack_ignore"), MaidConfig.MAID_RANGED_ATTACK_IGNORE.get())
                .setDefaultValue(Lists.newArrayList())
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_ranged_attack_ignore.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_RANGED_ATTACK_IGNORE.set(l)).build());

        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_work_meals_block_list"), MaidConfig.MAID_WORK_MEALS_BLOCK_LIST.get())
                .setDefaultValue(defaultMeals(true))
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_work_meals_block_list.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_WORK_MEALS_BLOCK_LIST.set(l)).build());

        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_home_meals_block_list"), MaidConfig.MAID_HOME_MEALS_BLOCK_LIST.get())
                .setDefaultValue(defaultMeals(true))
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_home_meals_block_list.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_HOME_MEALS_BLOCK_LIST.set(l)).build());

        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_heal_meals_block_list"), MaidConfig.MAID_HEAL_MEALS_BLOCK_LIST.get())
                .setDefaultValue(defaultMeals(false))
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_heal_meals_block_list.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_HEAL_MEALS_BLOCK_LIST.set(l)).build());

        // 正则黑名单：本移植版在查询时匹配（见类注释第 1 条），所以这里只需 set。
        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_work_meals_block_list_regex"), MaidConfig.MAID_WORK_MEALS_BLOCK_LIST_REGEX.get())
                .setDefaultValue(Lists.newArrayList())
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_work_meals_block_list_regex.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_WORK_MEALS_BLOCK_LIST_REGEX.set(l)).build());

        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_home_meals_block_list_regex"), MaidConfig.MAID_HOME_MEALS_BLOCK_LIST_REGEX.get())
                .setDefaultValue(Lists.newArrayList())
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_home_meals_block_list_regex.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_HOME_MEALS_BLOCK_LIST_REGEX.set(l)).build());

        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_heal_meals_block_list_regex"), MaidConfig.MAID_HEAL_MEALS_BLOCK_LIST_REGEX.get())
                .setDefaultValue(Lists.newArrayList())
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_heal_meals_block_list_regex.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_HEAL_MEALS_BLOCK_LIST_REGEX.set(l)).build());

        // 「吃完返还容器」在配置里是 List<List<String>>，界面上只能给 List<String>，
        // 所以按上游的做法用「a,b」这种逗号串中转。
        maid.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.maid.maid_eaten_return_container_list"), flattenContainerList())
                .setDefaultValue(Lists.newArrayList())
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_eaten_return_container_list.tooltip"))
                .setSaveConsumer(l -> MaidConfig.MAID_EATEN_RETURN_CONTAINER_LIST.set(parseContainerList(l))).build());

        maid.addEntry(entryBuilder.startIntField(new TranslatableComponent("config.touhou_little_maid.maid.maid_gun_long_distance"), MaidConfig.MAID_GUN_LONG_DISTANCE.get())
                .setDefaultValue(64).setMin(0).setMax(512)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_gun_long_distance.tooltip"), notPorted())
                .setSaveConsumer(i -> MaidConfig.MAID_GUN_LONG_DISTANCE.set(i)).build());

        maid.addEntry(entryBuilder.startIntField(new TranslatableComponent("config.touhou_little_maid.maid.maid_gun_medium_distance"), MaidConfig.MAID_GUN_MEDIUM_DISTANCE.get())
                .setDefaultValue(48).setMin(0).setMax(512)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_gun_medium_distance.tooltip"), notPorted())
                .setSaveConsumer(i -> MaidConfig.MAID_GUN_MEDIUM_DISTANCE.set(i)).build());

        maid.addEntry(entryBuilder.startIntField(new TranslatableComponent("config.touhou_little_maid.maid.maid_gun_near_distance"), MaidConfig.MAID_GUN_NEAR_DISTANCE.get())
                .setDefaultValue(32).setMin(0).setMax(512)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.maid.maid_gun_near_distance.tooltip"), notPorted())
                .setSaveConsumer(i -> MaidConfig.MAID_GUN_NEAR_DISTANCE.set(i)).build());
    }

    private static void chairConfig(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory chair = root.getOrCreateCategory(new TranslatableComponent("config.touhou_little_maid.chair"));
        chair.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.chair.chair_change_model"), ChairConfig.CHAIR_CHANGE_MODEL.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.chair.chair_change_model.tooltip"))
                .setSaveConsumer(ChairConfig.CHAIR_CHANGE_MODEL::set).build());

        chair.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.chair.chair_can_destroyed_by_anyone"), ChairConfig.CHAIR_CAN_DESTROYED_BY_ANYONE.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.chair.chair_can_destroyed_by_anyone.tooltip"))
                .setSaveConsumer(ChairConfig.CHAIR_CAN_DESTROYED_BY_ANYONE::set).build());
    }

    @SuppressWarnings("all")
    private static void miscConfig(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory misc = root.getOrCreateCategory(new TranslatableComponent("config.touhou_little_maid.misc"));

        misc.addEntry(entryBuilder.startDoubleField(new TranslatableComponent("config.touhou_little_maid.misc.maid_fairy_power_point"), MiscConfig.MAID_FAIRY_POWER_POINT.get())
                .setDefaultValue(0.16).setMin(0).setMax(5)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.maid_fairy_power_point.tooltip"))
                .setSaveConsumer(d -> MiscConfig.MAID_FAIRY_POWER_POINT.set(d)).build());

        misc.addEntry(entryBuilder.startIntField(new TranslatableComponent("config.touhou_little_maid.misc.maid_fairy_spawn_probability"), MiscConfig.MAID_FAIRY_SPAWN_PROBABILITY.get())
                .setDefaultValue(70).setMin(0).setMax(Integer.MAX_VALUE)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.maid_fairy_spawn_probability.tooltip"))
                .setSaveConsumer(d -> MiscConfig.MAID_FAIRY_SPAWN_PROBABILITY.set(d)).build());

        misc.addEntry(entryBuilder.startStrList(new TranslatableComponent("config.touhou_little_maid.misc.maid_fairy_blacklist_dimension"), (List<String>) MiscConfig.MAID_FAIRY_BLACKLIST_DIMENSION.get())
                .setDefaultValue(Lists.newArrayList())
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.maid_fairy_blacklist_dimension.tooltip"))
                .setSaveConsumer(l -> MiscConfig.MAID_FAIRY_BLACKLIST_DIMENSION.set(l)).build());

        misc.addEntry(entryBuilder.startDoubleField(new TranslatableComponent("config.touhou_little_maid.misc.player_death_loss_power_point"), MiscConfig.PLAYER_DEATH_LOSS_POWER_POINT.get())
                .setDefaultValue(1.0).setMin(0).setMax(5)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.player_death_loss_power_point.tooltip"))
                .setSaveConsumer(d -> MiscConfig.PLAYER_DEATH_LOSS_POWER_POINT.set(d)).build());

        misc.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.misc.give_smart_slab"), MiscConfig.GIVE_SMART_SLAB.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.give_smart_slab.tooltip"))
                .setSaveConsumer(MiscConfig.GIVE_SMART_SLAB::set).build());

        misc.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.misc.give_patchouli_book"), MiscConfig.GIVE_PATCHOULI_BOOK.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.give_patchouli_book.tooltip"))
                .setSaveConsumer(MiscConfig.GIVE_PATCHOULI_BOOK::set).build());

        misc.addEntry(entryBuilder.startDoubleField(new TranslatableComponent("config.touhou_little_maid.misc.shrine_lamp_effect_cost"), MiscConfig.SHRINE_LAMP_EFFECT_COST.get())
                .setDefaultValue(0.9).setMin(0).setMax(Double.MAX_VALUE)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.shrine_lamp_effect_cost.tooltip"))
                .setSaveConsumer(d -> MiscConfig.SHRINE_LAMP_EFFECT_COST.set(d)).build());

        misc.addEntry(entryBuilder.startDoubleField(new TranslatableComponent("config.touhou_little_maid.misc.shrine_lamp_max_storage"), MiscConfig.SHRINE_LAMP_MAX_STORAGE.get())
                .setDefaultValue(100).setMin(0).setMax(Double.MAX_VALUE)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.shrine_lamp_max_storage.tooltip"))
                .setSaveConsumer(d -> MiscConfig.SHRINE_LAMP_MAX_STORAGE.set(d)).build());

        misc.addEntry(entryBuilder.startIntField(new TranslatableComponent("config.touhou_little_maid.misc.shrine_lamp_max_range"), MiscConfig.SHRINE_LAMP_MAX_RANGE.get())
                .setDefaultValue(6).setMin(0).setMax(Integer.MAX_VALUE)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.shrine_lamp_max_range.tooltip"))
                .setSaveConsumer(d -> MiscConfig.SHRINE_LAMP_MAX_RANGE.set(d)).build());

        misc.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.misc.close_optifine_warning"), MiscConfig.CLOSE_OPTIFINE_WARNING.get())
                .setDefaultValue(false).setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.close_optifine_warning.tooltip"))
                .setSaveConsumer(MiscConfig.CLOSE_OPTIFINE_WARNING::set).build());

        misc.addEntry(entryBuilder.startIntField(new TranslatableComponent("config.touhou_little_maid.misc.scarecrow_range"), MiscConfig.SCARECROW_RANGE.get())
                .setDefaultValue(16 * 3).setMin(0).setMax(Integer.MAX_VALUE)
                .setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.scarecrow_range.tooltip"))
                .setSaveConsumer(d -> MiscConfig.SCARECROW_RANGE.set(d)).build());

        misc.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.misc.use_new_maid_fairy_model"), MiscConfig.USE_NEW_MAID_FAIRY_MODEL.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.misc.use_new_maid_fairy_model.tooltip"))
                .setSaveConsumer(MiscConfig.USE_NEW_MAID_FAIRY_MODEL::set).build());
    }

    private static void vanillaConfig(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory vanilla = root.getOrCreateCategory(new TranslatableComponent("config.touhou_little_maid.vanilla"));

        vanilla.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.vanilla.replace_slime_model"), VanillaConfig.REPLACE_SLIME_MODEL.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.vanilla.replace_slime_model.tooltip"))
                .setSaveConsumer(VanillaConfig.REPLACE_SLIME_MODEL::set).build());

        vanilla.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.vanilla.replace_xp_texture"), VanillaConfig.REPLACE_XP_TEXTURE.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.vanilla.replace_xp_texture.tooltip"))
                .setSaveConsumer(VanillaConfig.REPLACE_XP_TEXTURE::set).build());

        vanilla.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.vanilla.replace_totem_texture"), VanillaConfig.REPLACE_TOTEM_TEXTURE.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.vanilla.replace_totem_texture.tooltip"))
                .setSaveConsumer(VanillaConfig.REPLACE_TOTEM_TEXTURE::set).build());

        vanilla.addEntry(entryBuilder.startBooleanToggle(new TranslatableComponent("config.touhou_little_maid.vanilla.replace_xp_bottle_texture"), VanillaConfig.REPLACE_XP_BOTTLE_TEXTURE.get())
                .setDefaultValue(true).setTooltip(new TranslatableComponent("config.touhou_little_maid.vanilla.replace_xp_bottle_texture.tooltip"))
                .setSaveConsumer(VanillaConfig.REPLACE_XP_BOTTLE_TEXTURE::set).build());
    }

    private static void renderConfig(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory render = root.getOrCreateCategory(TComponent.translatable("config.touhou_little_maid.render"));

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_compass_tip"), RenderConfig.ENABLE_COMPASS_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_COMPASS_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_golden_apple_tip"), RenderConfig.ENABLE_GOLDEN_APPLE_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_GOLDEN_APPLE_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_potion_tip"), RenderConfig.ENABLE_POTION_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_POTION_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_milk_bucket_tip"), RenderConfig.ENABLE_MILK_BUCKET_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_MILK_BUCKET_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_script_book_tip"), RenderConfig.ENABLE_SCRIPT_BOOK_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_SCRIPT_BOOK_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_glass_bottle_tip"), RenderConfig.ENABLE_GLASS_BOTTLE_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_GLASS_BOTTLE_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_name_tag_tip"), RenderConfig.ENABLE_NAME_TAG_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_NAME_TAG_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_lead_tip"), RenderConfig.ENABLE_LEAD_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_LEAD_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_saddle_tip"), RenderConfig.ENABLE_SADDLE_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_SADDLE_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_shears_tip"), RenderConfig.ENABLE_SHEARS_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_SHEARS_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_ysm_roulette_tip"), RenderConfig.ENABLE_YSM_ROULETTE_TIP.get())
                .setDefaultValue(true).setTooltip(notPorted())
                .setSaveConsumer(RenderConfig.ENABLE_YSM_ROULETTE_TIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.render.enable_ai_chat_tip"), RenderConfig.ENABLE_AI_CHAT_TIP.get())
                .setDefaultValue(true).setSaveConsumer(RenderConfig.ENABLE_AI_CHAT_TIP::set).build());
    }

    // ------------------------------------------------------------------ 小工具

    /**
     * 「这一项对应的子系统本移植版还没做」的提示行，追加在原 tooltip 之后。
     *
     * <p>为什么<b>保留</b>这些条目而不是删掉：配置文件要与上游可互换
     * （同一个 {@code .toml} 拿来拿去都能用），删掉条目会让 FCAP 在读到未知键时
     * 覆盖整个文件。所以界面上保留，但必须说清楚它现在不生效 ——
     * 否则玩家改完发现没反应，只会以为是 bug。</p>
     *
     * <p>对应关系：{@code ReplaceAllayPercent} 依赖悦灵替换（1.19+ 才有悦灵）；
     * 三把枪的射程依赖 {@code compat/tacz}（TACZ 枪械）；
     * {@code EnableYsmRouletteTip} 依赖 Yes Steve Model 联动。三者都还没移植。</p>
     */
    private static TranslatableComponent notPorted() {
        return new TranslatableComponent("config.touhou_little_maid.not_ported.tooltip");
    }

    /**
     * 下拉框里的物品列表：按<b>注册名</b>排序。
     *
     * <p>上游用 {@code Comparator.comparing(Item::toString)}，而 {@code toString} 会带上
     * 对象哈希，同一个物品在不同次启动里的字符串可能不同 —— 玩家每次打开界面看到的顺序
     * 都不一样，很难找。这里改成注册名，稳定且可预期。</p>
     */
    private static LinkedHashSet<Item> sortedItems() {
        return Registry.ITEM.stream()
                .sorted(Comparator.comparing(item -> itemId(item)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String itemId(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "minecraft:air" : key.toString();
    }

    /** 餐食默认黑名单。{@code includeChorus} 为真时多一条紫颂果（工作餐 / 家庭餐）。 */
    private static List<String> defaultMeals(boolean includeChorus) {
        List<String> list = Lists.newArrayList(
                MaidConfig.getItemId(Items.PUFFERFISH),
                MaidConfig.getItemId(Items.POISONOUS_POTATO),
                MaidConfig.getItemId(Items.ROTTEN_FLESH),
                MaidConfig.getItemId(Items.SPIDER_EYE));
        if (includeChorus) {
            list.add(MaidConfig.getItemId(Items.CHORUS_FRUIT));
        }
        return list;
    }

    private static List<String> flattenContainerList() {
        return MaidConfig.eatenReturnContainerList().stream()
                .map(pair -> pair.get(0) + "," + pair.get(1))
                .collect(Collectors.toList());
    }

    private static List<List<String>> parseContainerList(List<String> lines) {
        List<List<String>> out = new ArrayList<>();
        for (String line : lines) {
            String[] split = line.split(",");
            // 只认「两个字段」的行。上游写成 `if (split.length != 2) continue;`，
            // 也就是说打错的行会被静默丢弃 —— 保留这个行为，不要让一条坏数据把整张表清空。
            if (split.length != 2) {
                continue;
            }
            out.add(Arrays.asList(split[0].trim(), split[1].trim()));
        }
        return out;
    }
}


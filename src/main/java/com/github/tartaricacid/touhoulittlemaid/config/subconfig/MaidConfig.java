package com.github.tartaricacid.touhoulittlemaid.config.subconfig;

import com.google.common.collect.Lists;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 女仆相关配置，移植自上游 {@code config/subconfig/MaidConfig}。
 *
 * <p><b>取值请用文件末尾的静态辅助方法</b>（如 {@link #workRange()}），不要直接
 * {@code MAID_WORK_RANGE.get()}。原因不是「{@code get()} 会抛异常」（它不会：未加载时返回默认值，
 * 依据见文件末尾），而是上游读的就是这些默认值：
 * 辅助方法把「默认值」写在调用点上，与上游行为逐处对得上。</p>
 */
public final class MaidConfig {
    public static final String TAG_PREFIX = "#";

    public static ForgeConfigSpec.IntValue GLOBAL_MAID_SOUND_FREQUENCY;
    public static ForgeConfigSpec.BooleanValue GLOBAL_MAID_SHOW_CHAT_BUBBLE;

    public static ForgeConfigSpec.ConfigValue<String> MAID_TAMED_ITEM;
    public static ForgeConfigSpec.ConfigValue<String> MAID_TEMPTATION_ITEM;
    public static ForgeConfigSpec.ConfigValue<String> MAID_NTR_ITEM;

    public static ForgeConfigSpec.IntValue MAID_WORK_RANGE;
    public static ForgeConfigSpec.IntValue MAID_IDLE_RANGE;
    public static ForgeConfigSpec.IntValue MAID_SLEEP_RANGE;
    public static ForgeConfigSpec.IntValue MAID_NON_HOME_RANGE;

    public static ForgeConfigSpec.IntValue BOW_RANGE;
    public static ForgeConfigSpec.IntValue CROSS_BOW_RANGE;
    public static ForgeConfigSpec.IntValue DANMAKU_RANGE;
    public static ForgeConfigSpec.IntValue TRIDENT_RANGE;

    public static ForgeConfigSpec.IntValue FEED_ANIMAL_MAX_NUMBER;
    public static ForgeConfigSpec.BooleanValue MAID_CHANGE_MODEL;
    public static ForgeConfigSpec.BooleanValue MAID_GOMOKU_OWNER_LIMIT;
    public static ForgeConfigSpec.IntValue OWNER_MAX_MAID_NUM;
    public static ForgeConfigSpec.DoubleValue REPLACE_ALLAY_PERCENT;

    public static ForgeConfigSpec.ConfigValue<List<String>> MAID_BACKPACK_BLACKLIST;
    public static ForgeConfigSpec.ConfigValue<List<String>> MAID_ATTACK_IGNORE;
    public static ForgeConfigSpec.ConfigValue<List<String>> MAID_RANGED_ATTACK_IGNORE;

    public static ForgeConfigSpec.ConfigValue<List<String>> MAID_WORK_MEALS_BLOCK_LIST;
    public static ForgeConfigSpec.ConfigValue<List<String>> MAID_HOME_MEALS_BLOCK_LIST;
    public static ForgeConfigSpec.ConfigValue<List<String>> MAID_HEAL_MEALS_BLOCK_LIST;

    public static ForgeConfigSpec.ConfigValue<List<String>> MAID_WORK_MEALS_BLOCK_LIST_REGEX;
    public static ForgeConfigSpec.ConfigValue<List<String>> MAID_HOME_MEALS_BLOCK_LIST_REGEX;
    public static ForgeConfigSpec.ConfigValue<List<String>> MAID_HEAL_MEALS_BLOCK_LIST_REGEX;
    public static ForgeConfigSpec.ConfigValue<List<List<String>>> MAID_EATEN_RETURN_CONTAINER_LIST;

    public static ForgeConfigSpec.IntValue MAID_GUN_LONG_DISTANCE;
    public static ForgeConfigSpec.IntValue MAID_GUN_MEDIUM_DISTANCE;
    public static ForgeConfigSpec.IntValue MAID_GUN_NEAR_DISTANCE;


    private MaidConfig() {
    }

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("maid");

        builder.comment("This is a global config that applies to all maids: how often maids speak");
        GLOBAL_MAID_SOUND_FREQUENCY = builder.defineInRange("GlobalMaidSoundFrequency", 100, 0, 100);

        builder.comment("This is a global config that applies to all maids: Whether or not to display chat bubbles");
        GLOBAL_MAID_SHOW_CHAT_BUBBLE = builder.define("GlobalMaidShowChatBubble", true);

        builder.comment("The item that can tamed maid",
                "Use the registered name of the item directly or write tag name with # as prefix");
        MAID_TAMED_ITEM = builder.define("MaidTamedItem", "minecraft:cake");

        builder.comment("The item that can temptation maid",
                "Use the registered name of the item directly or write tag name with # as prefix");
        MAID_TEMPTATION_ITEM = builder.define("MaidTemptationItem", "minecraft:cake");

        builder.comment("The item that can NTR maid",
                "Use the registered name of the item directly or write tag name with # as prefix");
        MAID_NTR_ITEM = builder.define("MaidNtrItem", "minecraft:structure_void");

        builder.comment("The max range of maid work mode");
        MAID_WORK_RANGE = builder.defineInRange("MaidWorkRange", 12, 3, 64);

        builder.comment("The max range of maid idle mode");
        MAID_IDLE_RANGE = builder.defineInRange("MaidIdleRange", 6, 3, 32);

        builder.comment("The max range of maid sleep mode");
        MAID_SLEEP_RANGE = builder.defineInRange("MaidSleepRange", 6, 3, 32);

        builder.comment("The max range of maid's Non-Home mode");
        MAID_NON_HOME_RANGE = builder.defineInRange("MaidNonHomeRange", 8, 3, 32);

        builder.comment("The max range of maid's bow attack");
        BOW_RANGE = builder.defineInRange("BowRange", 48, 8, 192);

        builder.comment("The max range of maid's crossbow attack");
        CROSS_BOW_RANGE = builder.defineInRange("CrossbowRange", 64, 8, 192);

        builder.comment("The max range of maid's danmaku attack");
        DANMAKU_RANGE = builder.defineInRange("DanmakuRange", 64, 8, 192);

        builder.comment("The max range of maid's trident attack");
        TRIDENT_RANGE = builder.defineInRange("TridentRange", 48, 8, 192);

        builder.comment("The max number of animals around when the maid breeds animals");
        FEED_ANIMAL_MAX_NUMBER = builder.defineInRange("FeedAnimalMaxNumber", 50, 6, 65536);

        builder.comment("Maid can switch models freely");
        MAID_CHANGE_MODEL = builder.define("MaidChangeModel", true);

        builder.comment("Maid can only play gomoku with her owner");
        MAID_GOMOKU_OWNER_LIMIT = builder.define("MaidGomokuOwnerLimit", true);

        builder.comment("The maximum number of maids the player own");
        OWNER_MAX_MAID_NUM = builder.defineInRange("OwnerMaxMaidNum", Integer.MAX_VALUE, 0, Integer.MAX_VALUE);

        builder.comment("These items cannot be placed in the maid backpack");
        MAID_BACKPACK_BLACKLIST = builder.define("MaidBackpackBlackList", Lists.newArrayList());

        builder.comment("The entity that the maid will not recognize as targets for attack");
        MAID_ATTACK_IGNORE = builder.define("MaidAttackIgnore", Lists.newArrayList("mekanism:robit"));

        builder.comment("The entity that the maid will not hurt when in ranged attack");
        MAID_RANGED_ATTACK_IGNORE = builder.define("MaidRangedAttackIgnore", Lists.newArrayList());

        builder.comment("Percentage chance of replace Allays spawn in pillager outposts with Maids");
        REPLACE_ALLAY_PERCENT = builder.defineInRange("ReplaceAllayPercent", 0.2, 0, 1);

        builder.comment("These items cannot be used as a maid's work meals");
        MAID_WORK_MEALS_BLOCK_LIST = builder.define("MaidWorkMealsBlockList", Lists.newArrayList(
                getItemId(Items.PUFFERFISH),
                getItemId(Items.POISONOUS_POTATO),
                getItemId(Items.ROTTEN_FLESH),
                getItemId(Items.SPIDER_EYE),
                getItemId(Items.CHORUS_FRUIT)
        ));

        builder.comment("These items cannot be used as a maid's home meals");
        MAID_HOME_MEALS_BLOCK_LIST = builder.define("MaidHomeMealsBlockList", Lists.newArrayList(
                getItemId(Items.PUFFERFISH),
                getItemId(Items.POISONOUS_POTATO),
                getItemId(Items.ROTTEN_FLESH),
                getItemId(Items.SPIDER_EYE),
                getItemId(Items.CHORUS_FRUIT)
        ));

        builder.comment("These items cannot be used as a maid's heal meals");
        MAID_HEAL_MEALS_BLOCK_LIST = builder.define("MaidHealMealsBlockList", Lists.newArrayList(
                getItemId(Items.PUFFERFISH),
                getItemId(Items.POISONOUS_POTATO),
                getItemId(Items.ROTTEN_FLESH),
                getItemId(Items.SPIDER_EYE)
        ));

        builder.comment("These items cannot be used as a maid's work meals which match the regex");
        MAID_WORK_MEALS_BLOCK_LIST_REGEX = builder.define("MaidWorkMealsBlockListRegEx", Lists.newArrayList());

        builder.comment("These items cannot be used as a maid's home meals which match the regex");
        MAID_HOME_MEALS_BLOCK_LIST_REGEX = builder.define("MaidHomeMealsBlockListRegEx", Lists.newArrayList());

        builder.comment("These items cannot be used as a maid's heal meals which match the regex");
        MAID_HEAL_MEALS_BLOCK_LIST_REGEX = builder.define("MaidHealMealsBlockListRegEx", Lists.newArrayList());

        builder.comment("These entries configure the container returned after a maid has eaten",
                "Eg: [\"minecraft:beetroot_soup\", \"minecraft:bowl\"]");
        MAID_EATEN_RETURN_CONTAINER_LIST = builder.define("MaidEatenReturnContainerList", Lists.newArrayList());

        builder.comment("Recognition distance of a maid under the gun task, Suitable for sniper rifles");
        MAID_GUN_LONG_DISTANCE = builder.defineInRange("MaidGunLongDistance", 64, 0, 512);

        builder.comment("Recognition distance of a maid under the gun task, Suitable for most types");
        MAID_GUN_MEDIUM_DISTANCE = builder.defineInRange("MaidGunMediumDistance", 48, 0, 512);

        builder.comment("Recognition distance of a maid under the gun task, Suitable for pistols and shotguns");
        MAID_GUN_NEAR_DISTANCE = builder.defineInRange("MaidGunNearDistance", 32, 0, 512);

        builder.pop();
    }

    /**
     * 物品注册名（上游 {@code ItemsUtil#getItemId} 的 Fabric 版实现）。
     */
    public static String getItemId(Item item) {
        ResourceLocation key = Registry.ITEM.getKey(item);
        return key == null ? "minecraft:air" : key.toString();
    }

    // ------------------------------------------------------------------ 取值
    //
    // 这里<b>不需要</b> try/catch。ForgeConfigSpec.ConfigValue#get() 在配置文件尚未加载时
    // 返回的是<b>默认值</b>，不会抛异常 —— 反编译 Forge Config API Port 3.2.4 的
    // ConfigValue#get 可见第一段就是：
    //     if (spec.childConfig == null) return defaultSupplier.get();
    // 上游 Forge 1.18.2 的同一方法也是这个结构。
    //
    // 早先这一层包了五个 try/catch，注释还写着「未加载时会抛 IllegalStateException」，
    // 那是错的：除了让读代码的人误解，它还会把<b>真正的</b>异常（值类型不匹配、
    // 默认值非法）一起吞掉，配置看起来「怎么改都没用」却没有一行日志。
    //
    // 现在只保留 null 检查：字段为 null 表示 init() 根本没被调用，那是编程错误。

    private static int intOr(ForgeConfigSpec.IntValue value, int fallback) {
        return value == null ? fallback : value.get();
    }

    private static boolean boolOr(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        return value == null ? fallback : value.get();
    }

    private static String stringOr(ForgeConfigSpec.ConfigValue<String> value, String fallback) {
        return value == null ? fallback : value.get();
    }

    private static List<String> listOr(ForgeConfigSpec.ConfigValue<List<String>> value, List<String> fallback) {
        return value == null ? fallback : value.get();
    }

    private static double doubleOr(ForgeConfigSpec.DoubleValue value, double fallback) {
        return value == null ? fallback : value.get();
    }


    public static int workRange() {
        return intOr(MAID_WORK_RANGE, 12);
    }

    public static int idleRange() {
        return intOr(MAID_IDLE_RANGE, 6);
    }

    public static int sleepRange() {
        return intOr(MAID_SLEEP_RANGE, 6);
    }

    /**
     * 全局女仆语音频率百分比（0 ~ 100，上游 {@code GlobalMaidSoundFrequency}）。
     *
     * <p>这个配置项一直存在，但此前<b>没有任何代码读它</b> ——
     * 上游是把它和「每只女仆的 SoundFreq」相乘后再做概率丢弃的：
     * <pre>
     * soundFrequency = maid.getConfigManager().getSoundFreq()
     *                * MaidConfig.GLOBAL_MAID_SOUND_FREQUENCY.get() / 100;
     * </pre>
     * 因此只改单只女仆的频率时，全局那一项（默认 100）乘进去仍是原值；
     * 反之把全局调到 0 就能一次性静音全部女仆。</p>
     */
    public static int globalMaidSoundFrequency() {
        return intOr(GLOBAL_MAID_SOUND_FREQUENCY, 100);
    }

    public static int nonHomeRange() {
        return intOr(MAID_NON_HOME_RANGE, 8);
    }

    public static int bowRange() {
        return intOr(BOW_RANGE, 48);
    }

    public static int crossBowRange() {
        return intOr(CROSS_BOW_RANGE, 64);
    }

    /**
     * 弹幕射程。默认 64（上游 {@code DanmakuRange = 64, 8..192}）。
     */
    public static int danmakuRange() {
        return intOr(DANMAKU_RANGE, 64);
    }

    public static int tridentRange() {
        return intOr(TRIDENT_RANGE, 48);
    }

    public static int feedAnimalMaxNumber() {
        return intOr(FEED_ANIMAL_MAX_NUMBER, 50);
    }

    public static int ownerMaxMaidNum() {
        return intOr(OWNER_MAX_MAID_NUM, Integer.MAX_VALUE);
    }

    public static boolean maidChangeModel() {
        return boolOr(MAID_CHANGE_MODEL, true);
    }

    public static String tamedItem() {
        return stringOr(MAID_TAMED_ITEM, "minecraft:cake");
    }

    public static String temptationItem() {
        return stringOr(MAID_TEMPTATION_ITEM, "minecraft:cake");
    }

    public static String ntrItem() {
        return stringOr(MAID_NTR_ITEM, "minecraft:structure_void");
    }

    public static List<String> backpackBlacklist() {
        return listOr(MAID_BACKPACK_BLACKLIST, Lists.newArrayList());
    }

    public static List<String> attackIgnore() {
        return listOr(MAID_ATTACK_IGNORE, Lists.newArrayList("mekanism:robit"));
    }

    public static List<String> rangedAttackIgnore() {
        return listOr(MAID_RANGED_ATTACK_IGNORE, Lists.newArrayList());
    }

    /**
     * 工作餐 / 回血餐的黑名单（按注册名精确匹配）。
     *
     * <p>上游直接 {@code MAID_WORK_MEALS_BLOCK_LIST.get()}，这里必须走 {@code listOr}：
     * 吃饭判定发生在服务端 AI 里，而 AI 可能在配置尚未加载时就被调用。
     * 回退值是上游的默认列表（毒土豆 / 河豚 / 腐肉 / 蜘蛛眼 / 紫颂果）。</p>
     */
    public static List<String> workMealsBlockList() {
        return listOr(MAID_WORK_MEALS_BLOCK_LIST, Lists.newArrayList(
                "minecraft:pufferfish", "minecraft:poisonous_potato",
                "minecraft:rotten_flesh", "minecraft:spider_eye", "minecraft:chorus_fruit"));
    }

    public static List<String> healMealsBlockList() {
        return listOr(MAID_HEAL_MEALS_BLOCK_LIST, Lists.newArrayList(
                "minecraft:pufferfish", "minecraft:poisonous_potato",
                "minecraft:rotten_flesh", "minecraft:spider_eye"));
    }

    /**
     * 正则形式的餐食黑名单。上游用 {@code MaidMealRegConfigEvent} 在配置加载时
     * 展开成具体 id 表；这里由 {@code MaidMealManager#isRegexBlockList} 按需匹配。
     */
    public static List<String> workMealsBlockListRegex() {
        return listOr(MAID_WORK_MEALS_BLOCK_LIST_REGEX, Lists.newArrayList());
    }

    public static List<String> healMealsBlockListRegex() {
        return listOr(MAID_HEAL_MEALS_BLOCK_LIST_REGEX, Lists.newArrayList());
    }

    /**
     * 吃完之后要返还的容器（上游 {@code DefaultEatenEvent}）。
     *
     * <p>形如 {@code [["minecraft:beetroot_soup", "minecraft:bowl"]]}：先看物品自带的
     * {@code getContainerItem()}，为空时才查这张表。</p>
     */
    public static List<List<String>> eatenReturnContainerList() {
        return MAID_EATEN_RETURN_CONTAINER_LIST == null
                ? Lists.<List<String>>newArrayList() : MAID_EATEN_RETURN_CONTAINER_LIST.get();
    }
}

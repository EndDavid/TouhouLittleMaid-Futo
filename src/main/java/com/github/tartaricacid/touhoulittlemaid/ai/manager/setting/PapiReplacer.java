package com.github.tartaricacid.touhoulittlemaid.ai.manager.setting;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.apache.commons.lang3.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
/**
 * 系统设定里的占位符替换，移植自上游 {@code ai/manager/setting/PapiReplacer}。
 *
 * <p>把 {@code ${game_time}} 这类占位符换成当前世界 / 女仆的真实状态，
 * 这样模型能说出「现在是雷雨天」「你手里拿着铁镐」这种话。</p>
 *
 * <p>{@code ${inventory_items}} 用的是 {@code EntityMaid#getAvailableBackpackInv()}
 * —— 本移植版同样返回 Forge 风格的 {@link RangedWrapper}（自带兼容层），
 * 所以这段与上游逐字一致，不需要换成 Fabric 的 {@code Container} 遍历。</p>
 */
public final class PapiReplacer {
    static String replace(String input, EntityMaid maid, String language) {
        Level level = maid.level;
        return input.replace("${game_time}", getTime(level))
                .replace("${weather}", getWeather(level))
                .replace("${dimension}", getDimension(level))
                .replace("${mainhand_item}", getSlotItemName(EquipmentSlot.MAINHAND, maid))
                .replace("${offhand_item}", getSlotItemName(EquipmentSlot.OFFHAND, maid))
                .replace("${inventory_items}", getInventoryItems(maid))
                .replace("${output_json_format}", getOutputJsonFormat())
                .replace("${chat_language}", language)
                .replace("${tts_language}", ttsLanguage(maid.getAiChatManager().getTtsLanguage()))
                .replace("${healthy}", getHealthyInfo(maid))
                .replace("${owner_healthy}", getOwnerHealthyInfo(maid))
                .replace("${armor_items}", getArmorItems(maid))
                .replace("${effects}", getEffects(maid))
                .replace("${biome}", getBiome(maid))
                .replace("${owner_name}", getOwnerName(maid))
                .replace("${custom_setting}", StringUtils.defaultString(maid.getAiChatManager().getCustomSetting()));
    }
    /**
     * 不能调用 LanguageManager，那个是客户端方法.
     */
    private static String ttsLanguage(String languageTag) {
        // 将语言代码转换为 Locale 所需的格式，例如 zh_cn -> zh-CN
        String[] parts = StringUtils.defaultString(languageTag).split("_");
        if (parts.length == 2) {
            languageTag = parts[0] + "-" + parts[1].toUpperCase(Locale.ENGLISH);
        }
        Locale locale = Locale.forLanguageTag(languageTag);
        return locale.getDisplayLanguage() + " (" + locale.getDisplayCountry() + ")";
    }
    private static String getBiome(EntityMaid maid) {
        Biome biome = maid.level.getBiome(maid.blockPosition()).value();
        ResourceLocation key = maid.level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getKey(biome);
        return key == null ? "未知 Biome" : key.toString();
    }
    private static String getEffects(EntityMaid maid) {
        List<String> names = new ArrayList<>();
        maid.getActiveEffects().forEach(i -> names.add(i.toString()));
        if (names.isEmpty()) {
            return "无";
        }
        return StringUtils.join(names, ", ");
    }
    private static String getArmorItems(EntityMaid maid) {
        List<String> names = new ArrayList<>();
        maid.getArmorSlots().forEach(stack -> {
            if (!stack.isEmpty()) {
                String itemName = stack.getDisplayName().getString();
                int count = stack.getCount();
                names.add(String.format("%sx%s", itemName, count));
            }
        });
        if (names.isEmpty()) {
            return "空的";
        }
        return StringUtils.join(names, ", ");
    }
    private static String getHealthyInfo(EntityMaid maid) {
        float maxHealth = maid.getMaxHealth();
        float health = maid.getHealth();
        return String.format("%s (max %s)", health, maxHealth);
    }
    private static String getOwnerHealthyInfo(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner != null) {
            float maxHealth = owner.getMaxHealth();
            float health = owner.getHealth();
            return String.format("%s (max %s)", health, maxHealth);
        }
        return StringUtils.EMPTY;
    }
    private static String getOwnerName(EntityMaid maid) {
        String ownerName = maid.getAiChatManager().getOwnerName();
        if (StringUtils.isBlank(ownerName)) {
            return "主人";
        }
        return ownerName;
    }
    private static String getWeather(Level level) {
        if (level.isThundering()) {
            return "雷雨天";
        }
        if (level.isRaining()) {
            return "阴雨天";
        }
        return "晴天";
    }
    private static String getTime(Level level) {
        long time = level.getDayTime();
        long hours = (time / 1000 + 6) % 24;
        long minutes = (time % 1000) / (50 / 3);
        return String.format("%02d:%02d", hours, minutes);
    }
    private static String getDimension(Level level) {
        ResourceKey<Level> dimension = level.dimension();
        if (dimension == Level.OVERWORLD) {
            return "主世界";
        }
        if (dimension == Level.NETHER) {
            return "下界";
        }
        if (dimension == Level.END) {
            return "末地";
        }
        return dimension.location().toString();
    }
    private static String getSlotItemName(EquipmentSlot slot, EntityMaid maid) {
        ItemStack stack = maid.getItemBySlot(slot);
        if (stack.isEmpty()) {
            return "空的";
        }
        String itemName = stack.getDisplayName().getString();
        int count = stack.getCount();
        return String.format("%sx%s", itemName, count);
    }
    private static String getInventoryItems(EntityMaid maid) {
        List<String> names = new ArrayList<>();
        RangedWrapper backpack = maid.getAvailableBackpackInv();
        for (int i = 0; i < backpack.getSlots(); i++) {
            ItemStack stack = backpack.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String itemName = stack.getDisplayName().getString();
                int count = stack.getCount();
                names.add(String.format("%sx%s", itemName, count));
            }
        }
        if (names.isEmpty()) {
            return "空的";
        }
        return StringUtils.join(names, ", ");
    }
    private static String getOutputJsonFormat() {
        return com.github.tartaricacid.touhoulittlemaid.ai.service.Service.GSON.toJson(new ResponseChat());
    }
    private PapiReplacer() {
    }
}

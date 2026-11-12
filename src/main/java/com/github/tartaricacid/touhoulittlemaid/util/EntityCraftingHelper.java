package com.github.tartaricacid.touhoulittlemaid.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 解析祭坛合成表里 {@code output} 那一块实体描述的工具，逐字移植自上游
 * {@code util/EntityCraftingHelper}。
 *
 * <p>祭坛合成的产物是<b>实体</b>而不是物品，所以输出写成：</p>
 * <pre>
 * "output": {
 *   "type": "touhou_little_maid:maid",
 *   "nbt": { ... },                          // 可选，实体的额外 NBT
 *   "copy": {                                // 可选，「把某个材料的 NBT 抄过来」
 *     "ingredient": { "item": "touhou_little_maid:film" },
 *     "tag": "MaidInfo"
 *   }
 * }
 * </pre>
 *
 * <p>{@code nbt} 既可以是 JSON 对象（会被转成 SNBT 再解析），也可以是 SNBT 字符串
 * —— 上游就这两种都支持，所以这里保持一致。</p>
 */
public final class EntityCraftingHelper {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String TYPE_TAG = "type";
    private static final String NBT_TAG = "nbt";
    private static final String COPY_TAG = "copy";
    private static final String INGREDIENT_TAG = "ingredient";
    private static final String TAG_TAG = "tag";

    private EntityCraftingHelper() {
    }

    /**
     * 解析 {@code output} 对象。
     *
     * @throws JsonParseException 实体类型不存在，或 NBT 写错了
     */
    public static Output getEntityData(JsonObject json) {
        try {
            Optional<EntityType<?>> optional = EntityType.byString(GsonHelper.getAsString(json, TYPE_TAG));
            if (optional.isEmpty()) {
                throw new JsonParseException("Entity Type Tag Not Found");
            }
            EntityType<?> type = optional.get();

            JsonElement nbtElement = json.get(NBT_TAG);
            CompoundTag outputData = new CompoundTag();
            if (nbtElement != null) {
                if (nbtElement.isJsonObject()) {
                    outputData = TagParser.parseTag(GSON.toJson(nbtElement));
                } else {
                    outputData = TagParser.parseTag(GsonHelper.convertToString(nbtElement, NBT_TAG));
                }
            }

            JsonElement copyElement = json.get(COPY_TAG);
            if (copyElement != null && copyElement.isJsonObject()) {
                JsonObject copyObject = copyElement.getAsJsonObject();
                Ingredient ingredient = Ingredient.fromJson(copyObject.get(INGREDIENT_TAG));
                String tag = null;
                if (copyObject.has(TAG_TAG) && StringUtils.isNotBlank(copyObject.get(TAG_TAG).getAsString())) {
                    tag = copyObject.get(TAG_TAG).getAsString();
                }
                return new Output(type, outputData, ingredient, tag);
            }
            return new Output(type, outputData);
        } catch (CommandSyntaxException e) {
            throw new JsonSyntaxException("Invalid NBT Entry: " + e);
        }
    }

    /**
     * 解析结果：实体类型 + 额外数据 + 可选的「NBT 抄写」规则。
     */
    public static class Output {
        private final EntityType<?> type;
        private final CompoundTag data;
        private final Ingredient copyInput;
        @Nullable
        private final String copyTag;

        public Output(EntityType<?> type, CompoundTag data, Ingredient copyInput, @Nullable String copyTag) {
            this.type = type;
            this.data = data;
            this.copyInput = copyInput;
            this.copyTag = copyTag;
        }

        public Output(EntityType<?> type, CompoundTag data) {
            this(type, data, Ingredient.EMPTY, null);
        }

        public EntityType<?> getType() {
            return type;
        }

        public CompoundTag getData() {
            return data;
        }

        public Ingredient getCopyInput() {
            return copyInput;
        }

        @Nullable
        public String getCopyTag() {
            return copyTag;
        }
    }
}


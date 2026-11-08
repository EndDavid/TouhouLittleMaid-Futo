package com.github.tartaricacid.touhoulittlemaid.client.resource.pojo;

import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 女仆模型包中 {@code maid_model.json} 的描述对象。
 *
 * <p>JSON 字段名与上游完全一致，因此现有的模型包无需任何修改即可解析。
 * 上游该类还实现 {@code IModelInfo} 并携带聊天气泡等字段，那些属于背包/GUI 里程碑,
 * 这里先只保留渲染所需的部分。</p>
 */
public class MaidModelInfo {
    private static final float RENDER_ENTITY_SCALE_MIN = 0.2f;
    private static final float RENDER_ENTITY_SCALE_MAX = 2.0f;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private List<String> description;

    @SerializedName("model")
    private ResourceLocation model;

    @SerializedName("texture")
    private ResourceLocation texture;

    @SerializedName("extra_textures")
    private List<ResourceLocation> extraTextures;

    @SerializedName("model_id")
    private ResourceLocation modelId;

    @SerializedName("use_sound_pack_id")
    private String useSoundPackId;

    @SerializedName("render_item_scale")
    private float renderItemScale = 1.0f;

    @SerializedName("render_entity_scale")
    private float renderEntityScale = 1.0f;

    @SerializedName("animation")
    private List<ResourceLocation> animation;

    @SerializedName("show_hata")
    private boolean showHata = true;

    @SerializedName("show_backpack")
    private boolean showBackpack = true;

    @SerializedName("show_custom_head")
    private boolean showCustomHead = true;

    @SerializedName("can_hold_trolley")
    private boolean canHoldTrolley = true;

    @SerializedName("can_hold_vehicle")
    private boolean canHoldVehicle = true;

    @SerializedName("can_riding_broom")
    private boolean canRidingBroom = true;

    @SerializedName("is_gecko")
    private boolean isGeckoModel = false;

    public ResourceLocation getTexture() {
        return texture;
    }

    public List<ResourceLocation> getExtraTextures() {
        return extraTextures;
    }

    public String getName() {
        return name;
    }

    public List<String> getDescription() {
        return description;
    }

    public List<ResourceLocation> getAnimation() {
        return animation;
    }

    /**
     * 覆盖动画文件列表。加载器会把本移植版执行不了的 {@code .js} 条目剔除后再写回
     * （本移植版没有 JS 动画引擎，见 GeckoModelLoader 的说明）。
     */
    public void setAnimation(List<ResourceLocation> animation) {
        this.animation = animation;
    }

    public ResourceLocation getModelId() {
        return modelId;
    }

    @Nullable
    public String getUseSoundPackId() {
        return useSoundPackId;
    }

    public ResourceLocation getModel() {
        return model;
    }

    public boolean isGeckoModel() {
        return isGeckoModel;
    }

    public float getRenderItemScale() {
        return renderItemScale;
    }

    public float getRenderEntityScale() {
        return renderEntityScale;
    }

    public boolean isShowHata() {
        return showHata;
    }

    public boolean isShowBackpack() {
        return showBackpack;
    }

    public boolean isShowCustomHead() {
        return showCustomHead;
    }

    public boolean isCanHoldTrolley() {
        return canHoldTrolley;
    }

    public boolean isCanHoldVehicle() {
        return canHoldVehicle;
    }

    public boolean isCanRidingBroom() {
        return canRidingBroom;
    }

    /**
     * 按基岩模型命名约定补全 {@code model} / {@code texture} 的默认位置。
     */
    public MaidModelInfo decorate() {
        if (description == null) {
            description = Collections.emptyList();
        }
        if (modelId == null) {
            throw new JsonSyntaxException("Expected \"model_id\" in model");
        }
        if (model == null) {
            model = new ResourceLocation(modelId.getNamespace(), "models/entity/" + modelId.getPath() + ".json");
        }
        if (texture == null) {
            texture = new ResourceLocation(modelId.getNamespace(), "textures/entity/" + modelId.getPath() + ".png");
        }
        if (name == null) {
            name = String.format("{model.%s.%s.name}", modelId.getNamespace(), modelId.getPath());
        }
        renderEntityScale = Mth.clamp(renderEntityScale, RENDER_ENTITY_SCALE_MIN, RENDER_ENTITY_SCALE_MAX);
        return this;
    }
}

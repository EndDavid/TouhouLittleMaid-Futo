package com.github.tartaricacid.touhoulittlemaid.client.resource.pojo;

import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 一条坐垫模型定义（{@code maid_chair.json} 里 {@code model_list} 的一个元素），
 * 移植自上游 {@code client/resource/pojo/ChairModelInfo}。
 *
 * <p>与上游的差异只有一处：上游还提供 {@code getCacheIconId()}（图标缓存键），
 * 本移植版没有图标缓存系统；它实现了 {@link IModelInfo} 以复用同一个换肤界面。</p>
 */
public class ChairModelInfo implements IModelInfo {
    private static final float RENDER_ENTITY_SCALE_MIN = 0.2F;
    private static final float RENDER_ENTITY_SCALE_MAX = 2.0F;
    private static final String GECKO_ANIMATION = ".animation.json";

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private List<String> description;

    @SerializedName("model")
    private ResourceLocation model;

    @SerializedName("texture")
    private ResourceLocation texture;

    @SerializedName("model_id")
    private ResourceLocation modelId;

    @SerializedName("render_item_scale")
    private float renderItemScale = 1.0F;

    @SerializedName("render_entity_scale")
    private float renderEntityScale = 1.0F;

    @SerializedName("animation")
    private List<ResourceLocation> animation;

    @SerializedName("mounted_height")
    private float mountedYOffset;

    @SerializedName("tameable_can_ride")
    private boolean tameableCanRide = true;

    @SerializedName("no_gravity")
    private boolean noGravity = false;

    @SerializedName("is_gecko")
    private boolean isGeckoModel = false;

    public String getName() {
        return this.name;
    }

    public List<String> getDescription() {
        return this.description;
    }

    public ResourceLocation getModel() {
        return this.model;
    }

    public ResourceLocation getTexture() {
        return this.texture;
    }

    public ResourceLocation getModelId() {
        return this.modelId;
    }

    @Nullable
    public List<ResourceLocation> getAnimation() {
        return this.animation;
    }

    public float getRenderItemScale() {
        return this.renderItemScale;
    }

    public float getRenderEntityScale() {
        return this.renderEntityScale;
    }

    public float getMountedYOffset() {
        return this.mountedYOffset;
    }

    public boolean isTameableCanRide() {
        return this.tameableCanRide;
    }

    public boolean isNoGravity() {
        return this.noGravity;
    }

    public boolean isGeckoModel() {
        return this.isGeckoModel;
    }

    /**
     * 补齐缺省值。逐条对应上游 {@code ChairModelInfo#decorate}。
     */
    public ChairModelInfo decorate() {
        if (this.description == null) {
            this.description = Collections.emptyList();
        }
        if (this.modelId == null) {
            throw new JsonSyntaxException("Expected \"model_id\" in chair model");
        }
        // 如果 model 或 texture 为空，自动生成默认位置的模型
        if (this.model == null) {
            this.model = new ResourceLocation(this.modelId.getNamespace(),
                    "models/entity/" + this.modelId.getPath() + ".json");
        }
        if (this.texture == null) {
            this.texture = new ResourceLocation(this.modelId.getNamespace(),
                    "textures/entity/" + this.modelId.getPath() + ".png");
        }
        // 如果名称为空，自动生成本地化名称
        if (this.name == null) {
            this.name = String.format("{model.%s.%s.name}", this.modelId.getNamespace(), this.modelId.getPath());
        }
        if (this.isGeckoModel) {
            if (this.animation == null || this.animation.isEmpty()) {
                this.animation = Collections.singletonList(GeckoModelLoader.DEFAULT_CHAIR_ANIMATION);
            } else {
                this.animation = this.animation.stream()
                        .filter(res -> res.getPath().endsWith(GECKO_ANIMATION))
                        .collect(Collectors.toList());
            }
        }
        this.renderEntityScale = Mth.clamp(this.renderEntityScale, RENDER_ENTITY_SCALE_MIN, RENDER_ENTITY_SCALE_MAX);
        // 将写入的像素高度转换为游戏内部的高度（上游注释：数值以 1/16 格为单位，且原点偏 3）
        this.mountedYOffset = (this.mountedYOffset - 3) * 0.0625F;
        return this;
    }
}
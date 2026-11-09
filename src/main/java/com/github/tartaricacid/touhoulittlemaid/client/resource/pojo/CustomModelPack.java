package com.github.tartaricacid.touhoulittlemaid.client.resource.pojo;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 一个女仆模型包（{@code assets/<ns>/maid_model.json}）。
 *
 * <p>对应上游 {@code client/resource/pojo/CustomModelPack}，这里只保留女仆用得到的那部分：
 * 上游还有椅子专用字段，本移植版没有椅子。</p>
 *
 * <p><b>为什么需要它。</b>换肤界面上游是「包」驱动的：顶部一排包页签、每个包独立分页、
 * 左侧显示包名 / 描述 / 作者 / 版本 / 日期。如果只有扁平的模型列表，
 * 界面就退化成「一大坨格子」，既对不上上游也无法表达包信息。</p>
 */
public class CustomModelPack {
    /**
     * 图标是否需要动画。上游用一个三态：未检测 / 不需要 / 需要。
     * 检测方式是看贴图是否「高 &gt; 宽」（竖向图集），是则按帧播放。
     */
    public enum AnimationState {
        UNCHECK, TRUE, FALSE
    }

    private String packName = "";
    private List<String> description = Collections.emptyList();
    private String version;
    private String date;
    private List<String> author = Collections.emptyList();
    @Nullable
    private ResourceLocation icon;
    private List<MaidModelInfo> modelList = Collections.emptyList();

    /** 图标动画状态与参数，由界面首次绘制时探测后写回。 */
    private AnimationState iconAnimation = AnimationState.UNCHECK;
    private int iconAspectRatio = 1;
    /** 每帧停留的 tick 数，对应 JSON 字段 {@code icon_delay}（上游缺省 2）。 */
    private int iconDelay = 2;

    public String getPackName() {
        return this.packName;
    }

    public void setPackName(String packName) {
        this.packName = packName == null ? "" : packName;
    }

    public List<String> getDescription() {
        return this.description;
    }

    public void setDescription(List<String> description) {
        this.description = description;
    }

    @Nullable
    public String getVersion() {
        return this.version;
    }

    public void setVersion(@Nullable String version) {
        this.version = version;
    }

    @Nullable
    public String getDate() {
        return this.date;
    }

    public void setDate(@Nullable String date) {
        this.date = date;
    }

    public List<String> getAuthor() {
        return this.author;
    }

    public void setAuthor(List<String> author) {
        this.author = author;
    }

    @Nullable
    public ResourceLocation getIcon() {
        return this.icon;
    }

    public void setIcon(@Nullable ResourceLocation icon) {
        this.icon = icon;
    }

    public List<MaidModelInfo> getModelList() {
        return this.modelList;
    }

    public void setModelList(List<MaidModelInfo> modelList) {
        this.modelList = modelList;
    }

    public AnimationState getIconAnimation() {
        return this.iconAnimation;
    }

    public void setIconAnimation(AnimationState state) {
        this.iconAnimation = state;
    }

    public int getIconAspectRatio() {
        return this.iconAspectRatio;
    }

    public void setIconAspectRatio(int ratio) {
        this.iconAspectRatio = Math.max(1, ratio);
    }

    public int getIconDelay() {
        return this.iconDelay;
    }

    public void setIconDelay(int iconDelay) {
        this.iconDelay = iconDelay;
    }

    /**
     * 补齐缺省值。模型列表为空时整包丢弃（由加载器判断）。
     */
    public CustomModelPack decorate() {
        if (this.description == null) {
            this.description = Collections.emptyList();
        }
        if (this.author == null) {
            this.author = Collections.emptyList();
        }
        if (this.modelList == null) {
            this.modelList = Collections.emptyList();
        }
        // 与上游 CustomModelPack#decorate 一致：没有 icon 就退回内置的空白包图标，
        // 否则页签上会缺一块。
        if (this.icon == null) {
            this.icon = new ResourceLocation(TouhouLittleMaid.MOD_ID,
                    "textures/gui/empty_model_pack_icon.png");
        }
        if (this.iconDelay <= 0) {
            this.iconDelay = 1;
        }
        return this;
    }
}

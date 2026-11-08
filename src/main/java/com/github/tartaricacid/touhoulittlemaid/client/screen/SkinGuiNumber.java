package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * 换肤界面的分页 / 网格换算，逐字移植自上游
 * {@code client/gui/entity/model/SkinGuiNumber}。
 *
 * <p>所有下标都在这里算，界面类只负责画 —— 这样「一页多少个」「第几行对应第几个模型」
 * 只有一处定义，翻页与滚动不会各自算错。</p>
 *
 * <p>三个常量就是上游的值：每页 7 个包页签、每行 11 个模型、每屏 5 行。</p>
 */
public class SkinGuiNumber {
    /** 每页最多 7 个模型包（顶部页签数量）。 */
    private static final int PACK_INTERVAL = 7;
    /** 每行 11 个模型。 */
    private static final int MODEL_PER_ROW = 11;
    /** 一屏 5 行，即 55 个。 */
    private static final int MODEL_TOTAL_ROW = 5;

    private final List<CustomModelPack> modelPackList;

    public SkinGuiNumber(List<CustomModelPack> modelPackList) {
        this.modelPackList = modelPackList;
    }

    public int getPackSize() {
        return this.modelPackList.size();
    }

    public int getModelSize(int packIndex) {
        packIndex = Mth.clamp(packIndex, 0, this.modelPackList.size() - 1);
        return this.modelPackList.get(packIndex).getModelList().size();
    }

    public int getPageSize() {
        return (this.modelPackList.size() - 1) / PACK_INTERVAL + 1;
    }

    /**
     * 当前包落在本页的第几个页签上（0~6）。
     *
     * <p>界面靠它决定「哪个页签画成按下态」，所以必须与 {@link #tabToPackIndex} 互逆。</p>
     */
    public int getTabIndex(int packIndex) {
        return packIndex % PACK_INTERVAL;
    }

    /**
     * 当前页实际有几个页签。
     *
     * <p>最后一页可能不满 7 个，所以不能一律按 7 画 —— 多出来的页签要隐藏，
     * 否则会出现「点了没反应的空页签」。</p>
     */
    public int getTabSize(int packIndex) {
        if (packIndex < (getPageSize() - 1) * PACK_INTERVAL) {
            return PACK_INTERVAL;
        }
        return (getPackSize() - 1) % PACK_INTERVAL + 1;
    }

    public int tabToPackIndex(int tabIndex, int pageIndex) {
        return Mth.clamp(pageIndex * PACK_INTERVAL + tabIndex, 0, getPackSize() - 1);
    }

    public int modelFromIndex(int rowIndex) {
        return MODEL_PER_ROW * rowIndex;
    }

    public int modelToIndex(int packIndex, int rowIndex) {
        return Math.min(MODEL_PER_ROW * (rowIndex + MODEL_TOTAL_ROW), getModelSize(packIndex));
    }

    /** 当前包最多能滚动几行（不足一屏时为 0）。 */
    public int getRowSize(int packIndex) {
        int row = (getModelSize(packIndex) - 1) / MODEL_PER_ROW + 1;
        return Math.max(row - MODEL_TOTAL_ROW, 0);
    }

    public boolean canScroll(int packIndex, int rowIndex) {
        int modelSize = getModelSize(packIndex) - MODEL_PER_ROW * rowIndex;
        return modelSize > MODEL_TOTAL_ROW * MODEL_PER_ROW;
    }

    /** 滚动条滑块的位置（0~1）。分母与上游一致，直接用行数推算。 */
    public float getCurrentScroll(int packIndex, int rowIndex) {
        return Mth.clamp((float) (rowIndex * (1.0 / ((getModelSize(packIndex) - 1) / MODEL_PER_ROW - 4))), 0, 1);
    }
}

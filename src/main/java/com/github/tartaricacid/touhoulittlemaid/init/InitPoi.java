package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.poi.MaidPoiManager;
import com.github.tartaricacid.touhoulittlemaid.mixin.PoiTypeAccessor;
import net.minecraft.world.entity.ai.village.poi.PoiType;

/**
 * 兴趣点类型注册表，对应上游 {@code init/InitPoi}。
 *
 * <p>上游用 Forge 的 {@code DeferredRegister}（{@code ForgeRegistries.POI_TYPES}），
 * 这里直接调用原版那个 private static 的 {@code PoiType#register} —— 见
 * {@link PoiTypeAccessor} 的注释，它是唯一能把方块状态同时写进
 * {@code TYPE_BY_STATE} 的入口。</p>
 *
 * <p><b>必须在 {@code InitBlocks} 之后调用</b>：状态集合是从已注册方块的
 * {@code getStateDefinition()} 里枚举出来的。</p>
 */
public final class InitPoi {
    /**
     * 女仆床。{@code maxTickets = 0}（不限认领数），{@code validRange = 1}。
     * 独占由床自身的 {@code OCCUPIED} 状态表达，见 {@link MaidPoiManager}。
     */
    public static PoiType MAID_BED;

    /**
     * 稻草人。女仆妖精生成前会查它，半径见 {@code MiscConfig#SCARECROW_RANGE}。
     */
    public static PoiType SCARECROW;

    /**
     * 娱乐方块（棋类 / 将来的键盘、书架、电脑）。{@code MaidBoardGameTask} 就是靠它找棋盘。
     */
    public static PoiType JOY_BLOCK;

    private InitPoi() {
    }

    public static void init() {
        // 名字会原样交给 new ResourceLocation(name)，所以必须带命名空间
        MAID_BED = PoiTypeAccessor.invokeRegister(TouhouLittleMaid.MOD_ID + ":maid_bed",
                MaidPoiManager.getBedStates(), 0, 1);
        SCARECROW = PoiTypeAccessor.invokeRegister(TouhouLittleMaid.MOD_ID + ":scarecrow",
                MaidPoiManager.getScarecrowStates(), 0, 1);
        JOY_BLOCK = PoiTypeAccessor.invokeRegister(TouhouLittleMaid.MOD_ID + ":joy_block",
                MaidPoiManager.getJoyBlockStates(), 0, 1);
    }
}

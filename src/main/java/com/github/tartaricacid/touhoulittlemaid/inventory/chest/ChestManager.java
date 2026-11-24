package com.github.tartaricacid.touhoulittlemaid.inventory.chest;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;

import java.util.ArrayList;
import java.util.List;

/**
 * 「什么算箱子」的登记表，移植自上游 {@code inventory/chest/ChestManager}。
 *
 * <p>隙间饰品每 100 tick 会遍历这张表，问每一个实现「你认不认这个方块实体」，
 * 所以表本身是不可变的（{@code init()} 之后换成不可变副本），避免运行期被误改。</p>
 *
 * <p><b>与上游的差异：</b>上游在这里还注册了 {@code IronChestType}
 * （铁箱子模组的兼容）以及 {@code ILittleMaid#addChestType} 的扩展点。
 * 本移植版的 {@code api} 里没有扩展体系，也没有铁箱子模组，
 * 所以只保留原版的两项；等扩展 API 搬过来再加循环。</p>
 */
public final class ChestManager {
    private static List<IChestType> allChestTypes;

    private ChestManager() {
    }

    public static void init() {
        ChestManager manager = new ChestManager();
        manager.add(new VanillaChestType());
        manager.add(new BarrelChestType());
        // 零食柜（1.20.1 新增的方块）：隙间能把它当目标往里塞食物
        manager.add(new SnackCabinetType());
        allChestTypes = List.copyOf(allChestTypes);
    }

    public static List<IChestType> getAllChestTypes() {
        return allChestTypes == null ? List.of() : allChestTypes;
    }

    public void add(IChestType chestType) {
        if (allChestTypes == null) {
            allChestTypes = new ArrayList<>();
        }
        allChestTypes.add(chestType);
    }
}
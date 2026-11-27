package com.github.tartaricacid.touhoulittlemaid.compat.farmersdelight;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 农夫乐事（Farmer's Delight）联动入口，移植自上游 TLMO Fabric 1.20.1 的同名类。
 *
 * <h2>它做什么</h2>
 * <p>把 {@link FarmersDelightEdible} 注册进 {@link MaidEdibleBlockManager} ——
 * 也就是让女仆能偷吃农夫乐事的<b>派（PieBlock）</b>与<b>盛宴（FeastBlock）</b>。
 * 这两类方块都是「一整块放下去、按口数递减」的设计，正好符合方块类食物的语义。</p>
 *
 * <h2>为什么必须问「装了吗」再 add</h2>
 * <p>{@link FarmersDelightEdible} 的类字面量里引用了 {@code vectorwing.farmersdelight.*}。
 * 一旦在没装农夫乐事的环境里执行到 {@code new FarmersDelightEdible()}，
 * 类加载阶段就会 {@code NoClassDefFoundError} —— 那是 {@code Error}，{@code catch (Exception)} 接不住。
 * 所以这里先 {@code isModLoaded}，判否时整段短路，农夫乐事的类型<strong>一个都不会被解析</strong>。</p>
 */
public final class FarmersDelightCompat {
    /**
     * 农夫乐事的 mod id。Fabric 与 Forge 两个版本的 id 相同（{@code farmersdelight}）。
     */
    public static final String ID = "farmersdelight";

    private FarmersDelightCompat() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(ID);
    }

    /**
     * 往登记表里加一份实现。没装农夫乐事时什么都不做。
     */
    public static void addFarmersDelightEdible() {
        boolean loaded = isLoaded();
        TouhouLittleMaid.LOGGER.info("[Futo] Farmer's Delight present: {}", loaded);
        if (!loaded) {
            return;
        }
        MaidEdibleBlockManager.add(new FarmersDelightEdible());
    }
}


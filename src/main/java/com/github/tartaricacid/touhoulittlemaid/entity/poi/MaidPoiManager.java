package com.github.tartaricacid.touhoulittlemaid.entity.poi;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.block.BlockScarecrow;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Set;

/**
 * 兴趣点（POI）方块状态集合，移植自上游 {@code entity/poi/MaidPoiManager}。
 *
 * <h2>POI 是干什么的</h2>
 * <p>原版用它让村民「找到工作台 / 床」，这里让女仆找到自己的床、让女仆妖精避开稻草人。
 * 每个 POI 类型持有一组方块状态；{@code PoiManager} 在区块加载时把世界里的这些状态
 * 记进一张索引表，之后按半径查询就是 O(邻居数) 而不是 O(方块数)。</p>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li><b>只移植了两个 POI：{@code maid_bed} 与 {@code scarecrow}。</b>
 *         上游另外两个（{@code joy_block} / {@code home_meal}）依赖棋类、电脑、书架、
 *         键盘与野餐垫 —— 那些方块还没移植，等它们落地后往这里加即可。</li>
 *     <li><b>注册方式：</b>1.18.2 的 {@code PoiType#register} 是 private static，
 *         走 {@code mixin/PoiTypeAccessor} 调用（上游用 Forge 公开的注册表）。
 *         参数里的名字必须带命名空间，因为它会被 {@code new ResourceLocation(name)} 包一层。</li>
 *     <li><b>这里只给出状态集合，注册在 {@code init/InitPoi}。</b>
 *         上游返回的是 {@code new PoiType(...)}，交给 Forge 的 {@code DeferredRegister}
 *         去注册；而 1.18.2 的 {@code PoiType} 构造器是 private，必须连同
 *         「写进 {@code TYPE_BY_STATE}」那一步一起做（见 {@code mixin/PoiTypeAccessor}），
 *         所以「构造」和「注册」不能拆开。</li>
 * </ol>
 */
public final class MaidPoiManager {
    private static final Set<BlockState> BEDS = ImmutableList.of(InitBlocks.MAID_BED)
            .stream().flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
            .filter(blockState -> blockState.getValue(BlockMaidBed.PART) == BedPart.HEAD)
            .filter(blockState -> !blockState.getValue(BlockMaidBed.OCCUPIED))
            .collect(ImmutableSet.toImmutableSet());

    private static final Set<BlockState> SCARECROW = ImmutableList.of(InitBlocks.SCARECROW)
            .stream().flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
            .filter(blockState -> blockState.getValue(BlockScarecrow.HALF) == DoubleBlockHalf.LOWER)
            .collect(ImmutableSet.toImmutableSet());

    private MaidPoiManager() {
    }

    public static Set<BlockState> getBedStates() {
        return BEDS;
    }

    public static Set<BlockState> getScarecrowStates() {
        return SCARECROW;
    }
}

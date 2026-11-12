package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 女仆床的物品形态，逐字移植自上游 {@code item/ItemMaidBed}。
 *
 * <h2>为什么要覆盖 {@code placeBlock}</h2>
 * <p>原版 {@link BlockItem#placeBlock} 用的是 {@link Block#UPDATE_ALL}
 * （{@code UPDATE_NEIGHBORS | UPDATE_CLIENTS}），<b>不含</b>
 * {@code UPDATE_KNOWN_SHAPE} / {@code UPDATE_IMMEDIATE}。女仆床是双格方块：
 * 放置时 {@code setPlacedBy} 会立刻去摆「床头」那一格，而两格互相
 * {@code updateShape}。用 {@code UPDATE_ALL} 时第二次 {@code setBlock} 会触发邻居更新，
 * 邻居更新又回灌到正在放置的脚下那一格，客户端会看到床头闪一下再被算成“半个床”。</p>
 *
 * <p>换成 {@code UPDATE_KNOWN_SHAPE | UPDATE_IMMEDIATE | UPDATE_CLIENTS} 之后，
 * 摆放过程被当成「一次连续的形状变更」，不会中途触发空气判定 —— 与上游逐字一致。</p>
 */
public class ItemMaidBed extends BlockItem {
    public ItemMaidBed() {
        super(InitBlocks.MAID_BED, new FabricItemSettings().stacksTo(1).tab(InitItems.MAIN_TAB));
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return context.getLevel().setBlock(context.getClickedPos(), state,
                Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_IMMEDIATE | Block.UPDATE_CLIENTS);
    }
}


package com.github.tartaricacid.touhoulittlemaid.entity.ai.edible;

import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;

import static com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock.belowIsSnackStand;

/**
 * 原版蛋糕的「方块类食物」实现，移植自上游 {@code entity/ai/edible/CakeEdible}。
 *
 * <h2>它是这套系统的样板</h2>
 * <p>没有它的话，「偷吃方块食物」这条 AI 在没装任何食物模组时就是个空转 ——
 * 上游把蛋糕当成内置实现，所以这套机制至少在原版环境里是可观察的。</p>
 *
 * <p>吃一口 = 把 {@code BITES} 加一；吃到第 {@code MAX_BITES} 口就把方块删掉。
 * 与玩家自己吃蛋糕同一套状态机，只是女仆不需要先「使用物品」。</p>
 */
public class CakeEdible implements IMaidEdibleBlock {
    @Override
    public boolean shouldMoveTo(EntityMaid maid, BlockPos pos, BlockState state) {
        return state.is(Blocks.CAKE) && belowIsSnackStand(maid, pos);
    }

    @Override
    public int getFavorabilityPoints(EntityMaid maid, BlockPos pos, BlockState state) {
        return 1;
    }

    @Override
    public boolean consume(EntityMaid maid, BlockPos pos, BlockState state) {
        int bites = state.getValue(CakeBlock.BITES);
        Level level = maid.level;
        if (bites < CakeBlock.MAX_BITES) {
            level.setBlock(pos, state.setValue(CakeBlock.BITES, Math.min(bites + 1, CakeBlock.MAX_BITES)),
                    Block.UPDATE_ALL);
        } else {
            level.removeBlock(pos, false);
        }
        // 上游用 spawnItemParticles(new ItemStack(Items.CAKE), 8)：
        // 嘴里掉蛋糕渣，比「凭空加好感」直观得多。
        maid.spawnItemParticles(new ItemStack(Items.CAKE), 8);
        maid.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
        return true;
    }

    @Override
    public boolean canPlaceAsFood(EntityMaid maid, ItemStack stack, int slotIndex) {
        return stack.is(Items.CAKE);
    }
}


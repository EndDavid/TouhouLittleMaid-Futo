package com.github.tartaricacid.touhoulittlemaid.compat.farmersdelight;

import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import com.nhoryzon.mc.farmersdelight.block.FeastBlock;
import com.nhoryzon.mc.farmersdelight.block.PieBlock;

import static com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock.belowIsSnackStand;
import static com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock.cloneItemOf;
import static com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock.isBlockItemOf;

/**
 * 农夫乐事「方块类食物」的实现，移植自上游 TLMO Fabric 1.20.1 的同名类。
 *
 * <h2>它覆盖两类方块</h2>
 * <ul>
 *     <li>{@link PieBlock} —— 派。状态里有 {@code BITES}（0..maxBites），
 *         吃到最后一格才消失；</li>
 *     <li>{@link FeastBlock} —— 盛宴（烤鸡、火腿等）。状态里有 {@code servings}，
 *         从满递减到 0 再消失。</li>
 * </ul>
 *
 * <p>两者的状态机不同（一个递增、一个递减），所以 {@link #consume} 里分两支写。</p>
 *
 * <h2>为什么用 {@code instanceof} 而不是注册表查</h2>
 * <p>农夫乐事没有给这些方块打「可食用」标签，唯一的判别方式就是方块的类。
 * 上游也是这么做的 —— 而且这个判断在 {@code shouldMoveTo} 里每帧都可能被调用，
 * {@code instanceof} 是几条字节码，比查标签便宜。</p>
 */
public class FarmersDelightEdible implements IMaidEdibleBlock {
    @Override
    public boolean shouldMoveTo(EntityMaid maid, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof PieBlock || block instanceof FeastBlock) {
            return belowIsSnackStand(maid, pos);
        }
        return false;
    }

    @Override
    public int getFavorabilityPoints(EntityMaid maid, BlockPos pos, BlockState state) {
        return 1;
    }

    @Override
    public boolean consume(EntityMaid maid, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        Level level = maid.level;
        // 粒子用的物品栈：在改状态<b>之前</b>取，否则吃到最后一格时方块已经没了、
        // getCloneItemStack 会返回空气，粒子就变成「什么都不掉」。
        ItemStack stack = cloneItemOf(state, level, pos);

        if (block instanceof PieBlock pieBlock) {
            // 派：BITES 0 -> maxBites-1，最后一格之后整块消失
            int bites = state.getValue(PieBlock.BITES);
            int maxBites = PieBlock.MAX_BITES - 1;
            if (bites < maxBites) {
                level.setBlock(pos, state.setValue(PieBlock.BITES, Math.min(bites + 1, maxBites)), Block.UPDATE_ALL);
            } else {
                maid.destroyBlock(pos);
            }
        } else if (block instanceof FeastBlock feastBlock) {
            // 盛宴：servings 递减，归零时整块消失
            IntegerProperty property = feastBlock.getServingsProperty();
            int servings = state.getValue(property);
            if (servings > 0) {
                level.setBlock(pos, state.setValue(property, servings - 1), Block.UPDATE_ALL);
            } else {
                maid.destroyBlock(pos);
            }
        }
        maid.spawnItemParticles(stack, 8);
        maid.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
        return true;
    }

    @Override
    public boolean canPlaceAsFood(EntityMaid maid, ItemStack stack, int slotIndex) {
        return isBlockItemOf(stack, block -> block instanceof PieBlock || block instanceof FeastBlock);
    }
}


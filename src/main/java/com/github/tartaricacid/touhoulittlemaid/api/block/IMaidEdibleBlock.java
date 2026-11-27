package com.github.tartaricacid.touhoulittlemaid.api.block;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.ApiStatus;

/**
 * 「方块类食物」接口，移植自上游 1.20.1 的 {@code api/block/IMaidEdibleBlock}。
 *
 * <p>上游 1.18.2 <b>没有</b>这套东西（它是 1.4.7 才加的），所以这里的形状完全按
 * TLMO Fabric 1.20.1 的版本抄。</p>
 *
 * <h2>它解决什么</h2>
 * <p>女仆在工作时段会去找「能吃一口的方块」—— 蛋糕、南瓜派、农夫乐事的派与盛宴。
 * 这些方块没法用一个 {@code Item} 表达「吃了几口」，所以上游把「值不值得去」、
 * 「吃一口要改什么状态」、「背包里的东西能不能反过来摆成方块食物」抽成三个问题，
 * 每种食物实现一份。</p>
 *
 * <h2>与前缀 {@code belowIsSnackStand} 的关系</h2>
 * <p>上游要求食物必须放在<b>零食架</b>（{@code maid_snack_stand_block} 标签）上才吃 ——
 * 否则女仆会把玩家精心摆在地上的蛋糕啃掉。判断条件写在
 * {@link #MAID_SNACK_STAND_BLOCK} 这个方块标签上，标签内容在
 * {@code data/touhou_little_maid/tags/blocks/maid_snack_stand_block.json}。</p>
 */
@ApiStatus.AvailableSince("1.0-futo")
public interface IMaidEdibleBlock {
    /**
     * 「零食架」方块标签。只有放在这些方块<b>上面</b>的食物方块女仆才会去吃。
     *
     * <p>上游用 datagen 生成，这里直接写 JSON（本移植版没有 datagen 那一条链）。</p>
     */
    TagKey<Block> MAID_SNACK_STAND_BLOCK = TagKey.create(Registry.BLOCK_REGISTRY,
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "maid_snack_stand_block"));

    /**
     * 工具方法：判断指定方块下方是不是零食架。
     */
    static boolean belowIsSnackStand(EntityMaid maid, BlockPos pos) {
        return maid.level.getBlockState(pos.below()).is(MAID_SNACK_STAND_BLOCK);
    }

    /**
     * 女仆是否应该走过去吃这个方块。
     *
     * <p><b>性能提示：</b>这个方法在搜索方块时会被反复调用。</p>
     */
    boolean shouldMoveTo(EntityMaid maid, BlockPos pos, BlockState state);

    /**
     * 吃一口给多少好感度。上游建议 1~4（每个游戏日最多尝试 6 次）。
     */
    int getFavorabilityPoints(EntityMaid maid, BlockPos pos, BlockState state);

    /**
     * 真正「吃一口」：实现方负责改方块状态（减少层数 / 删掉方块）并放音效粒子。
     *
     * @return 是不是真吃到了。{@code true} 时女仆会挥手臂并加好感度
     */
    boolean consume(EntityMaid maid, BlockPos pos, BlockState state);

    /**
     * 这个物品能不能被女仆摆成方块食物（用来过滤背包）。
     */
    default boolean canPlaceAsFood(EntityMaid maid, ItemStack stack, int slotIndex) {
        return false;
    }

    /**
     * 女仆是否应该把物品摆到这个位置。
     *
     * <p>默认实现就是上游那三条：不能摆在脚下、目标位置可替换、<b>必须是零食架上方</b>。</p>
     */
    default boolean shouldPlaceTo(EntityMaid maid, BlockPos pos, BlockState state, ItemStack stack) {
        if (pos.equals(maid.blockPosition())) {
            return false;
        }
        // 1.18.2 没有无参的 canBeReplaced()（那是 1.20 才加的），
        // 等价物是 Material#isReplaceable()。
        if (!state.getMaterial().isReplaceable()) {
            return false;
        }
        return belowIsSnackStand(maid, pos);
    }

    /**
     * 真正摆放：扣掉一个物品并放成方块。
     *
     * <p>上游用 {@code maid.placeItemBlock(pos, stackExtra)}，本移植版同样有这个方法
     * （见 {@code EntityMaid#placeItemBlock}）。</p>
     */
    default boolean placeAsFood(EntityMaid maid, BlockPos pos, ItemStack stack, int slotIndex) {
        CombinedInvWrapper availableInv = maid.getAvailableInv(true);
        ItemStack stackExtra = availableInv.extractItem(slotIndex, 1, false);
        if (stackExtra.isEmpty()) {
            return false;
        }
        return maid.placeItemBlock(pos, stackExtra);
    }

    /**
     * 方块物品的便捷判定：{@code stack} 是不是某个方块，且那个方块满足 {@code predicate}。
     *
     * <p>上游三份实现（蛋糕 / 农夫乐事 / 森罗物语）都写了几乎一样的
     * {@code item instanceof BlockItem} 判断，这里抽出来。</p>
     */
    static boolean isBlockItemOf(ItemStack stack, java.util.function.Predicate<Block> predicate) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return predicate.test(blockItem.getBlock());
    }

    /**
     * 上游的 {@code block.getCloneItemStack(level, pos, state)} 在 1.18.2 的签名不同
     * （没有 {@code BlockHitResult}），这里包一层，供实现方取「这个方块对应哪个物品」。
     */
    static ItemStack cloneItemOf(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
        return state.getBlock().getCloneItemStack(level, pos, state);
    }
}

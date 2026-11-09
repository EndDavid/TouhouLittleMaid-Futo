package com.github.tartaricacid.touhoulittlemaid.compat.patchouli;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import vazkii.patchouli.api.IStateMatcher;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * 注册手册里「祭坛」多方块的叠加预览，逐字移植自上游同名类。
 *
 * <p>模板里的字符含义：{@code O} = 任意原木（{@code BlockTags.LOGS}）、
 * {@code R} = 红羊毛、{@code ' '} = 任意方块、{@code 0} = 结构中心（祭坛基座的位置）。
 * Patchouli 用 {@code 0} 来定位「玩家点击时对齐的那个方块」，所以这里不能随便改成空格。</p>
 */
public final class MultiblockRegistry {
    private static final ResourceLocation ID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "altar");
    private static final String[][] TEMPLATE = new String[][]{
            {"        ", "       R", "       R", "       R", "       R", "       R", "       R", "        ",},
            {"        ", "        ", "       R", "        ", "        ", "       R", "        ", "        ",},
            {"        ", "       R", "       R", "       R", "       R", "       R", "       R", "        ",},
            {"  O  O  ", "        ", "O      R", "        ", "        ", "O      R", "        ", "  O  O  ",},
            {"  O  O  ", "        ", "O      R", "        ", "        ", "O      R", "        ", "  O  O  ",},
            {"  O  O  ", "        ", "O      R", "        ", "       0", "O      R", "        ", "  O  O  ",}
    };

    private MultiblockRegistry() {
    }

    public static void init() {
        PatchouliAPI.IPatchouliAPI api = PatchouliAPI.get();
        IStateMatcher logMatcher = api.predicateMatcher(Blocks.OAK_LOG.defaultBlockState(),
                state -> state.is(BlockTags.LOGS));
        IStateMatcher redWoolMatcher = api.predicateMatcher(Blocks.RED_WOOL.defaultBlockState(),
                state -> state.is(Blocks.RED_WOOL));
        api.registerMultiblock(ID,
                api.makeMultiblock(TEMPLATE, 'O', logMatcher, 'R', redWoolMatcher, ' ', api.anyMatcher()));
    }
}


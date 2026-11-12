package com.github.tartaricacid.touhoulittlemaid.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 把原版私有的 {@code StructureTemplate#palettes} 暴露出来。
 *
 * <p><b>为什么需要它：</b>多方块结构（{@code MultiBlockAltar}）要按模板逐个方块
 * 比对世界里的方块状态，而 {@code StructureTemplate} 在 1.18.2 里没有任何
 * 「遍历全部方块」的公开 API：</p>
 * <ul>
 *     <li>{@code filterBlocks(BlockPos, StructurePlaceSettings, Block)} 会<b>按方块过滤</b>，
 *         只返回匹配的那一种方块，拿不到「整份模板」；</li>
 *     <li>{@code processBlockInfos(...)} 需要调用方先提供原始的
 *         {@code List<StructureBlockInfo>} —— 又绕回了这张私有字段。</li>
 * </ul>
 *
 * <p>上游（1.20.1）里这个字段同样是私有的，但 Forge 的访问转换器（AT）把
 * {@code StructureTemplate#palettes} 开成了 public，所以上游直接
 * {@code template.palettes.get(0)}。Fabric 没有 AT，这里用一个 {@code @Accessor} 代替：
 * 不改变任何行为，只是绕开访问修饰符。</p>
 */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor("palettes")
    List<StructureTemplate.Palette> getPalettes();
}


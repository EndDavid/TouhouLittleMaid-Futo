package com.github.tartaricacid.touhoulittlemaid.compat.patchouli;

import net.minecraft.world.item.ItemStack;
import vazkii.patchouli.common.item.ItemModBook;

import java.util.List;

/**
 * 把《记忆中的幻想乡》放进本模组的创造模式物品栏，移植自 TLMO 1.20.1 的
 * {@code InitCreativeTabs} 里那一行 {@code output.accept(ItemModBook.forBook(...))}。
 *
 * <p><b>为什么 1.18.2 需要单独一个类：</b>物品栏内容在 1.18.2 是由
 * {@code Item.Properties#tab} 决定的，帕秋莉的 {@code guide_book} 属于帕秋莉自己，
 * 无法改它的 tab。上游 Forge 1.18.2 是让帕秋莉读 {@code book.json} 里的
 * {@code creative_tab} 字段、再走 Forge 的 {@code Item#fillItemCategory} 钩子补进去的；
 * Fabric 1.18.2 上没有那个钩子，对应的官方入口是
 * {@code FabricItemGroupBuilder#appendItems}（见 {@code InitItems.MAIN_TAB}）。</p>
 *
 * <p><b>为什么必须独立成类：</b>本类引用了 {@code vazkii.patchouli.*}，
 * 只有确认帕秋莉已安装后才会被调用（见 {@code InitItems.MAIN_TAB} 里的判断），
 * 未安装时本类根本不会被加载，也就不会 {@code NoClassDefFoundError}。
 * 这与 {@link OpenDefaultBook} / {@link MultiblockRegistry} 的处理方式一致。</p>
 */
public final class CreativeTabCompat {
    private CreativeTabCompat() {
    }

    /**
     * 往物品栏列表里补一本手册。
     *
     * <p>用 {@link ItemModBook#forBook(net.minecraft.resources.ResourceLocation)} 而不是
     * 自己拼 NBT：标签键与取值都由帕秋莉自己决定，将来它改了键名这边也不会失效。</p>
     */
    public static void appendBook(List<ItemStack> items) {
        items.add(ItemModBook.forBook(PatchouliCompat.BOOK_ID));
    }
}


package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 「物品 → 饰品行为」登记表，移植自上游 {@code item/bauble/BaubleManager}。
 *
 * <p>这张表同时承担两个职责：</p>
 * <ol>
 *     <li>决定<b>什么物品能放进饰品栏</b>（{@code BaubleItemHandler#isItemValid} 查这里）；</li>
 *     <li>决定<b>这件物品的行为是什么</b>（{@code onContentsChanged} 时查这里并缓存）。</li>
 * </ol>
 *
 * <p><b>与上游的差异：</b>上游用 {@code RegistryObject<Item>} 作键 ——
 * 那是 Forge 延迟注册带来的间接层，为此它得在 {@code getBauble(ItemStack)} 里
 * 现造一个 {@code RegistryObject}（{@code RegistryObject.create(...)}），既绕又慢。
 * 本移植版的物品是<b>立即注册</b>的（静态字段即注册），直接用 {@link Item} 作键即可，
 * 语义完全相同而少一层间接。</p>
 */
public final class BaubleManager {
    private static Map<Item, IMaidBauble> baubles;

    private BaubleManager() {
    }

    public static void init() {
        BaubleManager manager = new BaubleManager();
        manager.bind(InitItems.DROWN_PROTECT_BAUBLE, new DrownProtectBauble());
        manager.bind(InitItems.EXPLOSION_PROTECT_BAUBLE, new ExplosionProtectBauble());
        manager.bind(InitItems.ULTRAMARINE_ORB_ELIXIR, new ExtraLifeBauble());
        manager.bind(InitItems.FALL_PROTECT_BAUBLE, new FallProtectBauble());
        manager.bind(InitItems.FIRE_PROTECT_BAUBLE, new FireProtectBauble());
        manager.bind(InitItems.ITEM_MAGNET_BAUBLE, new ItemMagnetBauble());
        manager.bind(InitItems.MAGIC_PROTECT_BAUBLE, new MagicProtectBauble());
        manager.bind(InitItems.NIMBLE_FABRIC, new NimbleFabricBauble());
        manager.bind(InitItems.PROJECTILE_PROTECT_BAUBLE, new ProjectileProtectBauble());
        manager.bind(InitItems.MUTE_BAUBLE, new MuteBauble());
        manager.bind(Items.TOTEM_OF_UNDYING, new UndyingTotemBauble());
        baubles = Map.copyOf(baubles);
    }

    @Nullable
    public static IMaidBauble getBauble(Item item) {
        return baubles == null ? null : baubles.get(item);
    }

    @Nullable
    public static IMaidBauble getBauble(ItemStack stack) {
        return getBauble(stack.getItem());
    }

    public void bind(Item item, IMaidBauble bauble) {
        if (baubles == null) {
            baubles = new HashMap<>();
        }
        baubles.put(item, bauble);
    }
}

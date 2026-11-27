package com.github.tartaricacid.touhoulittlemaid.compat.trinkets;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
/**
 * Trinkets 联动入口，对应上游 Forge 版的 {@code compat/curios/CuriosCompat}。
 *
 * <h2>为什么是 Trinkets 而不是 Curios</h2>
 * <p>Curios 长期只有 Forge 版；Fabric 上的等价物是 <b>Trinkets</b> —— 同样是「给实体挂一组
 * 具名饰品槽」的体系，同样由数据文件描述槽位，同样在实体身上挂一个组件。
 * 上游 TLMO 的 Fabric 分支（1.20.1）用的是 <b>Accessories</b>（另一套），
 * 而这里按需求对接 Trinkets。</p>
 *
 * <h2>为什么这个类<b>一个 Trinkets 类型都不引用</b></h2>
 * <p>没装 Trinkets 时，任何「签名里出现 Trinkets 类型」的类都不该被加载。所以这里只保留
 * 「装没装的判定」「日志锚点」与三个「参数和返回值都是原版 / 本仓库类型」的转发方法；
 * 真正的实现全在 {@link TrinketsHandler}，只有 {@link #isLoaded()} 为真时才会被链接。</p>
 */
public final class TrinketsCompat {
    /**
     * Trinkets 的 mod id。
     */
    public static final String MOD_ID = "trinkets";
    /**
     * 用 {@code Boolean} 而不是 {@code boolean}：允许在 {@code init()} 之前被问到
     * （界面初始化与渲染线程都可能先查它），访问顺序不该成为约束。
     */
    private static Boolean loaded;
    private TrinketsCompat() {
    }
    public static boolean isLoaded() {
        Boolean value = loaded;
        if (value == null) {
            value = FabricLoader.getInstance().isModLoaded(MOD_ID);
            loaded = value;
        }
        return value;
    }
    /**
     * 客户端与服务端各调用一次，只为了留一条可断言的日志锚点。
     *
     * <p>槽位是数据文件（{@code data/trinkets/entities/maid.json}），不需要这里注册。</p>
     */
    public static void init() {
        TouhouLittleMaid.LOGGER.info("[Futo] Trinkets present: {}", isLoaded());
    }
    /**
     * 造一个「饰品页」菜单。没装 Trinkets 时返回 {@code null}，调用方回退到主界面。
     */
    public static AbstractContainerMenu createMenu(int syncId, Inventory inventory, int entityId) {
        if (!isLoaded()) {
            return null;
        }
        return TrinketsHandler.createMenu(syncId, inventory, entityId);
    }
    /**
     * 死亡时把女仆身上的饰品倒进墓碑，对应上游 {@code CuriosEvent#onMaidTombstone}。
     *
     * <p>上游用 Forge 的 {@code MaidTombstoneEvent} 挂这件事；本移植版没有那个事件，
     * 墓碑是在 {@code EntityMaid#dropEquipment} 里现造的，所以直接在那里调一次。</p>
     */
    public static void drainToTombstone(EntityMaid maid, EntityTombstone tombstone) {
        if (!isLoaded()) {
            return;
        }
        TrinketsHandler.drainToTombstone(maid, tombstone);
    }
    /**
     * 女仆身上一共有多少个饰品槽（界面用来算分页）。没装时返回 0。
     */
    public static int slotCount(EntityMaid maid) {
        if (!isLoaded()) {
            return 0;
        }
        return TrinketsHandler.slotCount(maid);
    }
}

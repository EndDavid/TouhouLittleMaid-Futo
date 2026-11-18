package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

/**
 * 隙间的搬运算法本体，从 {@code WirelessIOBauble} 里抽出来。
 *
 * <p><b>为什么单独成一个类：</b>上游把这两段循环写在饰品类里，于是它们<b>只能在游戏里验证</b> ——
 * 而「女仆把东西搬进箱子」这条路径恰恰是最难复现的（要驯好的女仆、饰品栏、绑定的箱子、
 * 女仆所在区块还得真的在 tick）。抽出来之后它不含任何实体 / 世界 / 注册表依赖，
 * 只吃两个 {@link IItemHandler}，可以被 {@code port/verify-wireless-io} 在游戏外用
 * 内存物品栏跑遍所有分支。</p>
 *
 * <h2>两个方向的差别（照抄上游，别「统一」掉）</h2>
 * <ul>
 *     <li><b>女仆 → 箱子</b>：按槽位配置跳过<b>女仆</b>的槽位；往箱子里塞时不看槽位配置
 *         —— 槽位配置描述的是女仆的物品栏，与箱子无关。</li>
 *     <li><b>箱子 → 女仆</b>：箱子侧不看配置；塞进女仆物品栏时<b>遵守</b>槽位配置。</li>
 * </ul>
 * <p>换句话说：槽位配置永远是「女仆侧」的规则，只是生效点一个在取出、一个在放入。
 * 两个方向的过滤清单语义则完全一致（白名单 = 只搬清单里的；黑名单 = 清单里的不搬）。</p>
 */
public final class WirelessIOTransfer {
    private WirelessIOTransfer() {
    }

    /**
     * 配置表的格数：36 个背包槽 + 2 只手。
     */
    public static final int SLOT_NUM = 38;

    /**
     * 把 {@code source} 的物品搬到 {@code dest}。
     *
     * @param source         来源物品栏
     * @param dest           目标物品栏
     * @param isBlacklist    过滤模式：{@code true} = 黑名单
     * @param filterList     9 格过滤清单（槽位上限 1 的物品栏）
     * @param slotConfig     「冻结」标记；{@code true} 表示该槽位不参与传输
     * @param skipSourceSlots 是否按 {@code slotConfig} 跳过来源槽位（女仆 → 箱子时为 {@code true}）
     * @param useDestSlots    是否按 {@code slotConfig} 跳过目标槽位（箱子 → 女仆时为 {@code true}）
     * @return 实际移动的物品件数
     */
    public static int move(@Nonnull IItemHandler source, @Nonnull IItemHandler dest,
                           boolean isBlacklist, @Nonnull IItemHandler filterList,
                           @Nonnull boolean[] slotConfig,
                           boolean skipSourceSlots, boolean useDestSlots) {
        int moved = 0;
        for (int i = 0; i < source.getSlots(); i++) {
            if (skipSourceSlots && i < slotConfig.length && slotConfig[i]) {
                continue;
            }
            ItemStack sourceStack = source.getStackInSlot(i);
            if (sourceStack.isEmpty() || !allowMove(sourceStack, filterList, isBlacklist)) {
                continue;
            }
            int beforeCount = sourceStack.getCount();
            // 注意：目标侧是否遵守槽位配置，由 useDestSlots 决定；
            // 不遵守时传 null，与上游「往箱子里塞不看配置」等价。
            ItemStack after = insertItemStacked(dest, sourceStack.copy(), false,
                    useDestSlots ? slotConfig : null);
            int afterCount = after.getCount();
            if (beforeCount != afterCount) {
                source.extractItem(i, beforeCount - afterCount, false);
                moved += beforeCount - afterCount;
            }
        }
        return moved;
    }

    /**
     * 过滤清单判定。
     *
     * <p>白名单 + 空清单 = 什么都不搬（「只搬清单里的」而清单是空的）；
     * 黑名单 + 空清单 = 全都搬。这与玩家的直觉一致，也是上游的行为。</p>
     */
    public static boolean allowMove(@Nonnull ItemStack stack, @Nonnull IItemHandler filterList,
                                    boolean isBlacklist) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean allow = isBlacklist;
        for (int j = 0; j < filterList.getSlots(); j++) {
            ItemStack filterItem = filterList.getStackInSlot(j);
            // 用 sameItemStackIgnoreDurability：受损的剑与满耐久的剑算同一种，
            // 否则「过滤清单里放一把剑」拦不住任何一把用过的剑。
            if (stack.sameItemStackIgnoreDurability(filterItem)) {
                allow = !isBlacklist;
                break;
            }
        }
        return allow;
    }

    /**
     * 往物品栏里塞物品，<b>遵守槽位配置</b>，返回没塞进去的剩余部分。
     *
     * <p>与 Forge 的 {@code ItemHandlerHelper#insertItemStacked} 同语义，
     * 只是多了一个「跳过被冻结的槽位」的过滤条件 —— 上游因此自己写了一份。</p>
     *
     * @param slotConfig {@code null} 表示不做槽位过滤
     */
    @Nonnull
    public static ItemStack insertItemStacked(@Nonnull IItemHandler inventory, @Nonnull ItemStack stack,
                                              boolean simulate, @Nullable boolean[] slotConfig) {
        if (stack.isEmpty()) {
            return stack;
        }
        if (!stack.isStackable()) {
            return insertItem(inventory, stack, simulate, slotConfig);
        }
        int sizeInventory = inventory.getSlots();
        // 第一轮：先叠到已有的同类堆叠上
        for (int i = 0; i < sizeInventory; i++) {
            if (isFrozen(slotConfig, i)) {
                continue;
            }
            if (canItemStacksStackRelaxed(inventory.getStackInSlot(i), stack)) {
                stack = inventory.insertItem(i, stack, simulate);
                if (stack.isEmpty()) {
                    break;
                }
            }
        }
        // 第二轮：再找空槽位
        if (!stack.isEmpty()) {
            for (int i = 0; i < sizeInventory; i++) {
                if (isFrozen(slotConfig, i)) {
                    continue;
                }
                if (inventory.getStackInSlot(i).isEmpty()) {
                    stack = inventory.insertItem(i, stack, simulate);
                    if (stack.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return stack;
    }

    /**
     * 按槽位顺序硬塞（不合并堆叠），跳过被冻结的槽位。
     */
    @Nonnull
    public static ItemStack insertItem(@Nonnull IItemHandler dest, @Nonnull ItemStack stack,
                                       boolean simulate, @Nullable boolean[] slotConfig) {
        if (stack.isEmpty()) {
            return stack;
        }
        for (int i = 0; i < dest.getSlots(); i++) {
            if (isFrozen(slotConfig, i)) {
                continue;
            }
            stack = dest.insertItem(i, stack, simulate);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /**
     * 把「配置表尾部的两只手」搬到「当前女仆物品栏尾部」。
     *
     * <p>配置表固定 38 项：36 个背包槽 + 2 只手。而女仆的「可用物品栏」是
     * {@code 背包槽 + 双手} 的拼接，所以双手在其中的下标是
     * {@code maidSlots - 2} 与 {@code maidSlots - 1} —— 小背包时也就跟着前移。
     * 这就是为什么要搬一次：玩家在界面上点的是「第 36/37 格」，而实际生效的位置
     * 取决于她背的是哪个包。</p>
     *
     * <p>存进来的数组可能短于 38 项（手改 NBT / 旧存档直接读），先补齐再搬，
     * 否则 {@code tmp[37]} 会在女仆 tick 里抛 {@code ArrayIndexOutOfBoundsException}。</p>
     */
    public static boolean[] slotConfigForMaid(@Nullable byte[] stored, int maidSlots) {
        byte[] tmp = null;
        if (stored != null) {
            tmp = Arrays.copyOf(stored, Math.max(stored.length, SLOT_NUM));
            tmp[maidSlots - 2] = tmp[SLOT_NUM - 2];
            tmp[maidSlots - 1] = tmp[SLOT_NUM - 1];
        }
        boolean[] out = new boolean[SLOT_NUM];
        for (int i = 0; i < SLOT_NUM; i++) {
            out[i] = tmp != null && i < tmp.length && tmp[i] != 0;
        }
        return out;
    }

    private static boolean isFrozen(@Nullable boolean[] slotConfig, int slot) {
        return slotConfig != null && slot < slotConfig.length && slotConfig[slot];
    }

    /**
     * 「宽松」的可堆叠判断：同物品、可堆叠、NBT 相同即可。
     *
     * <p>与上游的差别只有一处：去掉了结尾的 {@code areCapsCompatible} ——
     * 那是 Forge 的物品能力（Capability）兼容判断，本兼容层没有能力系统。
     * 剩下的判据与上游逐字相同。</p>
     */
    public static boolean canItemStacksStackRelaxed(@Nonnull ItemStack a, @Nonnull ItemStack b) {
        if (a.isEmpty() || b.isEmpty() || a.getItem() != b.getItem()) {
            return false;
        }
        if (!a.isStackable()) {
            return false;
        }
        if (a.hasTag() != b.hasTag()) {
            return false;
        }
        return !a.hasTag() || Objects.equals(a.getTag(), Objects.requireNonNull(b.getTag()));
    }
}


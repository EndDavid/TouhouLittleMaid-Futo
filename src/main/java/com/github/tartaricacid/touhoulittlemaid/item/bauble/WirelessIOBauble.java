package com.github.tartaricacid.touhoulittlemaid.item.bauble;
import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidWirelessIOEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import javax.annotation.Nullable;
/**
 * 隙间饰品：让女仆与一个绑定的箱子之间自动搬运物品，移植自上游
 * {@code item/bauble/WirelessIOBauble}。
 *
 * <h2>节奏</h2>
 * <p>每 100 tick（5 秒）检查一次，且满足以下全部条件才动手：
 * 绑定了坐标、箱子在女仆的活动半径内、箱子没有被玩家打开、坐标处确实有方块实体、
 * 且女仆的界面没开着。这些都是上游的判据，一条都不能省：</p>
 * <ul>
 *     <li><b>半径</b>：用的是女仆的「工作范围」({@code getRestrictRadius()})，
 *         也就是玩家用罗盘设的那圈红线 —— 隙间不是无线的，它只是免去了走路。</li>
 *     <li><b>没被玩家打开</b>：原版 {@code ChestBlockEntity#getOpenCount} 与
 *         {@code ChestBlock#getContainer} 都依赖开箱计数，玩家开着时插入的物品
 *         在关闭界面时可能被原版覆盖回去，所以干脆不搬。</li>
 *     <li><b>界面没开着</b>：玩家正在看女仆背包时不要动她的东西。</li>
 * </ul>
 *
 * <h2>槽位配置（{@code SlotConfigData}）</h2>
 * <p>38 个开关，界面上一排排画出来。语义是「<b>置位 = 该槽位冻结</b>」。
 * 前 36 项按女仆背包槽位号索引，<b>最后两项（36/37）代表她的两只手</b>。
 * 而女仆的「可用物品栏」是 {@code 背包槽 + 双手} 的拼接，
 * 所以双手在其中的下标是 {@code getSlots() - 2} 与 {@code getSlots() - 1}
 * —— 这就是 {@link #buildSlotConfig} 里那句搬运的来历：
 * 把「配置表尾部的两个开关」搬到「当前女仆物品栏的尾部」。</p>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li><b>箱子物品栏的来源。</b>上游走 Forge Capability
 *         {@code te.getCapability(ITEM_HANDLER_CAPABILITY)}；本移植版用
 *         {@link IChestType#getContainer} 拿到原版 {@link Container}，
 *         再用 {@link InvWrapper} 包成 {@code IItemHandler}。
 *         {@code InvWrapper} 每次真写入后都会 {@code setChanged()}，
 *         否则塞进箱子的物品不会标脏区块、下线后可能丢失。</li>
 *     <li><b>搬运算法被抽到 {@link WirelessIOTransfer}。</b>它只依赖
 *         {@code IItemHandler}，因此可以在游戏外用内存物品栏验证
 *         （见 {@code port/verify-wireless-io}）。语义逐条保持上游原样。</li>
 *     <li><b>{@code SlotConfigData} 长度兜底。</b>上游直接
 *         {@code slotConfigTmp[SLOT_NUM - 2]} 取值，若存档里写了短于 38 的数组
 *         （手改 NBT 或旧版本存档）会抛 {@code ArrayIndexOutOfBoundsException}，
 *         而这个异常发生在女仆 tick 里，会让服务端每 5 秒刷一次堆栈。
 *         这里先补齐到 38 位再搬运。</li>
 * </ol>
 */
public class WirelessIOBauble implements IMaidBauble {
    /**
     * 配置表的格数：36 个背包槽 + 2 只手。
     */
    private static final int SLOT_NUM = 38;
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.tickCount % 100 != 0 || maid.isGuiOpening()) {
            return;
        }
        if (maid.level.isClientSide) {
            return;
        }
        BlockPos bindingPos = ItemWirelessIO.getBindingPos(baubleItem);
        if (bindingPos == null) {
            return;
        }
        float maxDistance = maid.getRestrictRadius();
        if (maid.distanceToSqr(bindingPos.getX(), bindingPos.getY(), bindingPos.getZ())
                > (maxDistance * maxDistance)) {
            return;
        }
        BlockEntity te = maid.level.getBlockEntity(bindingPos);
        if (te == null) {
            return;
        }
        for (IChestType type : ChestManager.getAllChestTypes()) {
            if (!type.isChest(te)) {
                continue;
            }
            if (type.getOpenCount(maid.level, bindingPos, te) > 0) {
                // 玩家正开着这个箱子：此时插入可能被原版的开箱计数逻辑覆盖回去
                return;
            }
            Container container = type.getContainer(maid.level, bindingPos, te);
            if (container == null) {
                return;
            }
            IItemHandler chestInv = new InvWrapper(container);
            IItemHandler maidInv = maid.getAvailableInv(false);
            boolean isMaidToChest = ItemWirelessIO.isMaidToChest(baubleItem);
            boolean isBlacklist = ItemWirelessIO.isBlacklist(baubleItem);
            boolean[] slotConfigData = buildSlotConfig(ItemWirelessIO.getSlotConfig(baubleItem),
                    maidInv.getSlots());
            IItemHandler filterList = ItemWirelessIO.getFilterList(baubleItem);
            if (isMaidToChest) {
                MaidWirelessIOEvent.MaidToChest event = new MaidWirelessIOEvent.MaidToChest(
                        maid, maidInv, chestInv, filterList, isBlacklist, slotConfigData);
                if (!MinecraftForge.EVENT_BUS.post(event).isCanceled()) {
                    WirelessIOTransfer.move(maidInv, chestInv, isBlacklist, filterList, slotConfigData,
                            true, false);
                }
            } else {
                MaidWirelessIOEvent.ChestToMaid event = new MaidWirelessIOEvent.ChestToMaid(
                        maid, maidInv, chestInv, filterList, isBlacklist, slotConfigData);
                if (!MinecraftForge.EVENT_BUS.post(event).isCanceled()) {
                    WirelessIOTransfer.move(chestInv, maidInv, isBlacklist, filterList, slotConfigData,
                            false, true);
                }
            }
            if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.USE_WIRELESS_IO);
            }
            return;
        }
    }
    /**
     * 见 {@link WirelessIOTransfer#slotConfigForMaid}。
     */
    static boolean[] buildSlotConfig(@Nullable byte[] slotConfig, int maidInvSlots) {
        return WirelessIOTransfer.slotConfigForMaid(slotConfig, maidInvSlots);
    }
}

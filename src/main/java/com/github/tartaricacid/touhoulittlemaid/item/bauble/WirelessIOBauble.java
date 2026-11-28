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

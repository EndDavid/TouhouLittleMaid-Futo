package com.github.tartaricacid.touhoulittlemaid.api.bauble;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * 「什么样的方块算箱子」，移植自上游 {@code api/bauble/IChestType}。
 *
 * <p>隙间（无线 I/O）饰品要往容器里塞物品，服务端必须先能回答三个问题：
 * <b>这是不是箱子</b>、<b>玩家有没有权限碰它</b>、<b>它现在被别人开着吗</b>。
 * 上游把这三个问题抽成接口，于是「铁箱子」之类的模组兼容只要再注册一个实现即可。</p>
 *
 * <p><b>本移植版唯一的扩展：{@link #getContainer}。</b>
 * 上游是拿 {@code te.getCapability(ITEM_HANDLER_CAPABILITY).ifPresent(...)} 取箱子物品栏的 ——
 * 那是 Forge 的 Capability 体系。Fabric 1.18.2 没有 1.18.2 版的能力系统能给出
 * {@code IItemHandler}（fabric-transfer 的 {@code Storage<ItemVariant>} 语义不同，
 * 且明确只允许服务端查询、按「面」暴露，拿不到上游需要的「按槽位号读写」语义），
 * 所以这里改为让每种箱子自己交出一个原版 {@link Container}，
 * 再由 {@code net.minecraftforge.items.wrapper.InvWrapper} 包成 {@code IItemHandler}。
 * 默认实现覆盖了「方块实体自己就是 {@code Container}」这一大类（桶、潜影盒、
 * 绝大多数 Fabric 模组容器都是用 {@code Inventory} 实现的），与原版行为一致。</p>
 */
public interface IChestType {
    int ALLOW_COUNT = 0;
    int DENY_COUNT = Integer.MAX_VALUE;

    /**
     * 该方块实体是不是可识别的箱子。
     */
    boolean isChest(BlockEntity chest);

    /**
     * 箱子能否被该玩家打开 —— 用于阻止把上锁的箱子绑定成隙间的目标。
     */
    boolean canOpenByPlayer(BlockEntity chest, Player player);

    /**
     * 箱子当前的开启计数，原版用这个值防止多个玩家同时操作同一个箱子。
     *
     * @return {@code <= 0} 代表可以操作，其他情况表示不能
     */
    int getOpenCount(BlockGetter level, BlockPos pos, BlockEntity chest);

    /**
     * 取箱子背后的原版物品栏，取不到返回 {@code null}。
     *
     * <p>注意<b>大箱子</b>：{@code ChestBlockEntity} 自身只有 27 格，
     * 两格拼起来的那 54 格是 {@code ChestBlock#getContainer} 现拼的
     * {@code CompoundContainer}。所以 {@code VanillaChestType} 必须覆写本方法 ——
     * 这也正是 Forge 侧箱子 Capability 的实现方式。</p>
     */
    @Nullable
    default Container getContainer(Level level, BlockPos pos, BlockEntity chest) {
        return chest instanceof Container container ? container : null;
    }
}


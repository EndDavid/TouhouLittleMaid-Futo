package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 零食柜的方块实体，移植自上游 1.20.1 的 {@code tileentity/TileEntitySnackCabinet}。
 *
 * <p>27 格（与单个原版箱子同容量），所以界面直接复用原版
 * {@link ChestMenu#threeRows} + {@link net.minecraft.client.gui.screens.inventory.ChestScreen} ——
 * 上游也是这么做的（反编译可见 {@code createMenu} 返回
 * {@code ChestMenu.method_19245(id, inventory, this)}，而 {@code method_19245} 就是
 * {@code threeRows}（它给 {@code ChestMenu} 构造器传的行数是 3，菜单类型 {@code GENERIC_9x3}）。</p>
 *
 * <p>继承 {@link RandomizableContainerBlockEntity} 而不是 {@code BaseContainerBlockEntity}：
 * 前者自带 {@code LootTable} 支持，柜子因此可以被战利品表填充（上游同样）。</p>
 *
 * <p><b>开关盖的音效</b>：{@link ContainerOpenersCounter} 负责统计「现在有几个玩家开着它」，
 * 从 0 变 1 时播放开盖音、从 1 变 0 时播放关盖音。这套是原版箱子的做法，
 * 用它可以避免多个玩家同时开同一个柜子时音效乱掉。</p>
 */
public class TileEntitySnackCabinet extends RandomizableContainerBlockEntity {
    private static final int SLOT_COUNT = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
            TileEntitySnackCabinet.playSound(level, pos, state, SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
            TileEntitySnackCabinet.playSound(level, pos, state, SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                          int count, int openCount) {
            // 不需要额外的方块更新：柜子的开合状态不影响外观
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            if (player.containerMenu instanceof ChestMenu menu) {
                Container container = menu.getContainer();
                return container == TileEntitySnackCabinet.this;
            }
            return false;
        }
    };

    public TileEntitySnackCabinet(BlockPos pos, BlockState state) {
        super(InitBlocks.SNACK_CABINET_TE, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items);
        }
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return new TranslatableComponent("block.touhou_little_maid.snack_cabinet");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        // 27 格 = 原版「三行」箱子（3 行柜子 + 3 行玩家背包，共 6 行）。
        //
        // 【这里必须是 threeRows，不能是 sixRows】—— 上一版写成了 sixRows，
        // 于是每次右键都抛
        //     IllegalArgumentException: Container size 27 is smaller than expected 54
        // 因为 ChestMenu 的构造器里有 checkContainerSize(container, rows * 9)：
        // sixRows 要 6*9 = 54 格（双箱），threeRows 才是 27 格（单箱）。
        // 异常抛在 ServerPlayer#openMenu 里，客户端什么都收不到 —— 表现就是「点不开」。
        // 上游同样是 27 格 + 三行（反编译：getContainerSize 返回 bipush 27，
        // createMenu 调 ChestMenu.method_19245，而 method_19245 传的行数是 iconst_3）。
        return ChestMenu.threeRows(id, playerInventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (!this.remove && !player.isSpectator() && this.level != null) {
            // 1.18.2 的 incrementOpeners 只有 4 个参数；第 5 个（viewDistance）是 1.19 才加的
            this.openersCounter.incrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!this.remove && !player.isSpectator() && this.level != null) {
            this.openersCounter.decrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
        }
    }

    /** 方块状态变化（例如相邻柜子被挖掉）后重算开合计数，与上游 {@code recheckOpen} 一致。 */
    public void recheckOpen() {
        if (!this.remove && this.level != null) {
            this.openersCounter.recheckOpeners(this.level, this.worldPosition, this.getBlockState());
        }
    }

    private static void playSound(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                  SoundEvent sound) {
        // 与上游一致：音源点在方块中心，音量 0.5，音调随方块位置微扰（同一个柜子音调稳定）
        double x = (double) pos.getX() + 0.5D;
        double y = (double) pos.getY() + 0.5D;
        double z = (double) pos.getZ() + 0.5D;
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F,
                level.random.nextFloat() * 0.1F + 0.8F);
    }
}

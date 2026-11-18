package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline check for the gap (wireless I/O) transfer rules.
 *
 * <p>Lives in the mod's own package so it can reach the package-private
 * {@code WirelessIOBauble#buildSlotConfig} without widening any visibility, and so
 * that the class it exercises is <b>the real one</b> that ships.</p>
 *
 * <p>Why offline at all: the "maid -> chest" half cannot be observed reliably on a
 * dedicated dev server. With no player online the chunk stops ticking after a few
 * seconds, so the maid simply stops running and nothing can be concluded. The
 * transfer itself is pure {@code IItemHandler} arithmetic, so it can be driven here
 * against in-memory inventories instead.</p>
 *
 * <p>Run through {@code port/verify-wireless-io/run-transfer-check.ps1}.</p>
 */
public final class TransferCheck {
    private static int failures = 0;

    public static void main(String[] args) {
        // ItemStack's static initialiser touches the item registry, so the game's
        // bootstrap has to run first even though no world is ever created.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();


        // 1. chest -> maid, blacklist with an empty filter: everything moves
        Inv chest = new Inv(27);
        chest.set(0, new ItemStack(Items.STONE, 5));
        chest.set(3, new ItemStack(Items.DIRT, 2));
        Inv maid = new Inv(38);
        int moved = WirelessIOTransfer.move(chest, maid, true, new Inv(9), new boolean[38], false, true);
        check("chest->maid moves everything", moved == 7);
        check("chest now empty", chest.count(Items.STONE) == 0 && chest.count(Items.DIRT) == 0);
        check("maid got stone", maid.count(Items.STONE) == 5);
        check("maid got dirt", maid.count(Items.DIRT) == 2);

        // 2. maid -> chest, blacklist with an empty filter
        int moved2 = WirelessIOTransfer.move(maid, chest, true, new Inv(9), new boolean[38], true, false);
        check("maid->chest moves everything", moved2 == 7);
        check("maid empty again", maid.count(Items.STONE) == 0 && maid.count(Items.DIRT) == 0);
        check("chest has it back", chest.count(Items.STONE) == 5);

        // 3. whitelist with an EMPTY filter moves nothing (both directions)
        Inv filterEmpty = new Inv(9);
        check("whitelist empty: chest->maid blocked",
                WirelessIOTransfer.move(chest, maid, false, filterEmpty, new boolean[38], false, true) == 0);
        check("whitelist empty: maid->chest blocked",
                WirelessIOTransfer.move(maid, chest, false, filterEmpty, new boolean[38], true, false) == 0);

        // 4. whitelist with stone listed: only stone moves
        Inv filterStone = new Inv(9);
        filterStone.set(0, new ItemStack(Items.STONE));
        int moved4 = WirelessIOTransfer.move(chest, maid, false, filterStone, new boolean[38], false, true);
        check("whitelist stone: only stone moves", moved4 == 5);
        check("whitelist stone: dirt stayed", chest.count(Items.DIRT) == 2);
        check("whitelist stone: maid has stone", maid.count(Items.STONE) == 5);

        // 5. blacklist with stone listed: stone stays, dirt moves
        Inv chest2 = new Inv(27);
        chest2.set(0, new ItemStack(Items.STONE, 5));
        chest2.set(3, new ItemStack(Items.DIRT, 2));
        Inv maid2 = new Inv(38);
        int moved5 = WirelessIOTransfer.move(chest2, maid2, true, filterStone, new boolean[38], false, true);
        check("blacklist stone: only dirt moves", moved5 == 2);
        check("blacklist stone: stone stayed", chest2.count(Items.STONE) == 5);
        check("blacklist stone: maid has dirt", maid2.count(Items.DIRT) == 2);

        // 6. frozen destination slot is skipped (chest -> maid honours slotConfig)
        boolean[] frozen0 = new boolean[38];
        frozen0[0] = true;
        Inv chest3 = new Inv(27);
        chest3.set(0, new ItemStack(Items.STONE, 4));
        Inv maid3 = new Inv(38);
        WirelessIOTransfer.move(chest3, maid3, true, new Inv(9), frozen0, false, true);
        check("frozen slot 0: stone lands in slot 1", maid3.get(0).isEmpty()
                && maid3.get(1).getItem() == Items.STONE);

        // 7. frozen source slot is skipped (maid -> chest honours slotConfig)
        boolean[] frozen0b = new boolean[38];
        frozen0b[0] = true;
        Inv chest4 = new Inv(27);
        int moved7 = WirelessIOTransfer.move(maid3, chest4, true, new Inv(9), frozen0b, true, false);
        // maid3 has stone in slot 1 only, so it must still move
        check("frozen slot 0 does not block slot 1", moved7 == 4 && chest4.count(Items.STONE) == 4);

        // 8. chest -> maid does NOT look at slotConfig on the chest side, and
        //    maid -> chest does NOT look at it on the chest side either.
        boolean[] allFrozen = new boolean[38];
        for (int i = 0; i < allFrozen.length; i++) {
            allFrozen[i] = true;
        }
        Inv chest5 = new Inv(27);
        chest5.set(0, new ItemStack(Items.STONE, 3));
        Inv maid5 = new Inv(38);
        check("all frozen: chest->maid blocked",
                WirelessIOTransfer.move(chest5, maid5, true, new Inv(9), allFrozen, false, true) == 0);

        // 9. stacking into an existing partial stack, not a fresh slot
        Inv chest6 = new Inv(27);
        chest6.set(0, new ItemStack(Items.STONE, 60));
        Inv maid6 = new Inv(38);
        maid6.set(4, new ItemStack(Items.STONE, 8));
        WirelessIOTransfer.move(chest6, maid6, true, new Inv(9), new boolean[38], false, true);
        check("stacks onto the partial stack", maid6.get(4).getCount() == 64);
        check("overflow goes to a new slot", maid6.count(Items.STONE) == 68);

        // 10. non-stackable items still move (insertItem path, not the stacked path)
        ItemStack swords = new ItemStack(Items.IRON_SWORD, 1);
        Inv chest7 = new Inv(27);
        chest7.set(0, swords);
        Inv maid7 = new Inv(38);
        check("non-stackable moves", WirelessIOTransfer.move(chest7, maid7, true, new Inv(9),
                new boolean[38], false, true) == 1
                && maid7.get(0).getItem() == Items.IRON_SWORD);

        // 11. damaged tools are still matched by the filter (ignore durability)
        Inv filterSword = new Inv(9);
        filterSword.set(0, new ItemStack(Items.IRON_SWORD));
        ItemStack damaged = new ItemStack(Items.IRON_SWORD, 1);
        damaged.setDamageValue(100);
        check("filter ignores durability", WirelessIOTransfer.allowMove(damaged, filterSword, false));

        // 12. buildSlotConfig: the two hand switches are moved to the inventory tail
        byte[] config = new byte[38];
        config[0] = 1;    // backpack slot 0 frozen
        config[36] = 1;   // "left hand" frozen
        config[37] = 1;   // "right hand" frozen
        boolean[] built = WirelessIOTransfer.slotConfigForMaid(config, 38);
        check("built config keeps slot 0", built[0]);
        check("built config keeps hands", built[36] && built[37]);

        // short inventory (small backpack: 6 slots + 2 hands = 8)
        boolean[] builtSmall = WirelessIOTransfer.slotConfigForMaid(config, 8);
        check("small backpack: hands land on 6/7", builtSmall[6] && builtSmall[7]);
        check("small backpack: slot 0 still frozen", builtSmall[0]);
        check("small backpack: middle slots untouched", !builtSmall[3]);

        // a short stored array must not throw (old / hand-edited NBT)
        byte[] shortConfig = new byte[]{1, 0, 0};
        boolean[] builtShort = WirelessIOTransfer.slotConfigForMaid(shortConfig, 38);
        check("short stored array is padded", builtShort.length == 38 && builtShort[0]);

        // null config means "nothing frozen"
        boolean[] builtNull = WirelessIOTransfer.slotConfigForMaid(null, 38);
        check("null config freezes nothing", builtNull.length == 38 && !builtNull[0]);

        System.out.println(failures == 0 ? "ALL OK" : (failures + " FAILURE(S)"));
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) {
            failures++;
        }
    }

    /**
     * Minimal in-memory {@link IItemHandler}: one stack per slot, 64 per slot,
     * no slot restrictions. Deliberately dumb so that a failure points at the code
     * under test and not at the test double.
     */
    private static final class Inv implements IItemHandler {
        private final List<ItemStack> slots;

        private Inv(int size) {
            this.slots = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                this.slots.add(ItemStack.EMPTY);
            }
        }

        private ItemStack get(int slot) {
            return this.slots.get(slot);
        }

        private void set(int slot, ItemStack stack) {
            this.slots.set(slot, stack);
        }

        private int count(net.minecraft.world.item.Item item) {
            int total = 0;
            for (ItemStack stack : this.slots) {
                if (!stack.isEmpty() && stack.getItem() == item) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        @Override
        public int getSlots() {
            return this.slots.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.slots.get(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = this.slots.get(slot);
            int limit = 64;
            if (!existing.isEmpty()) {
                if (existing.getItem() != stack.getItem()) {
                    return stack;
                }
                limit -= existing.getCount();
            }
            if (limit <= 0) {
                return stack;
            }
            boolean reachedLimit = stack.getCount() > limit;
            if (!simulate) {
                if (existing.isEmpty()) {
                    ItemStack copy = stack.copy();
                    copy.setCount(reachedLimit ? limit : stack.getCount());
                    this.slots.set(slot, copy);
                } else {
                    existing.grow(reachedLimit ? limit : stack.getCount());
                }
            }
            ItemStack left = stack.copy();
            left.setCount(reachedLimit ? stack.getCount() - limit : 0);
            return left;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack existing = this.slots.get(slot);
            if (existing.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, existing.getCount());
            if (!simulate) {
                ItemStack left = existing.copy();
                left.setCount(existing.getCount() - taken);
                this.slots.set(slot, left.getCount() <= 0 ? ItemStack.EMPTY : left);
            }
            ItemStack out = existing.copy();
            out.setCount(taken);
            return out;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            this.slots.set(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}

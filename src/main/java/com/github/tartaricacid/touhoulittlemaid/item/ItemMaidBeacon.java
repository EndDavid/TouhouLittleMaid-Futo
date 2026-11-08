package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.List;

/**
 * 神社庭灯物品，移植自上游 {@code item/ItemMaidBeacon}。
 *
 * <p>本体是两格高方块，所以继承原版的 {@link DoubleHighBlockItem}：它只做一件事 ——
 * 放置前把上方一格清成空气 / 水，真正的两格结构由 {@code BlockMaidBeacon#setPlacedBy} 拼。</p>
 *
 * <p>它同时也是一把武器：主手 +3 攻击力、-3.2 攻速（上游同值）。</p>
 *
 * <h2>数据随物品走</h2>
 *
 * <p>方块实体被拆掉时会调用 {@link #tileEntityToItemStack}，把存储的 P 点写进
 * {@code StorageData.ForgeData.*}；重新放下时 {@link #itemStackToTileEntity} 再读回来。
 * 「ForgeData」这个中间层是上游（Forge {@code getPersistentData()}）的历史包袱，
 * 这里保留是为了让上游存档里的庭灯物品还能读出存储量，见
 * {@code TileEntityMaidBeacon#saveToItemTag()}。</p>
 */
public class ItemMaidBeacon extends DoubleHighBlockItem {
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");
    private static final String STORAGE_DATA_TAG = "StorageData";

    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public ItemMaidBeacon() {
        super(InitBlocks.MAID_BEACON, new FabricItemSettings().stacksTo(1).tab(InitItems.MAIN_TAB));
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE_UUID, "Tool modifier", 3.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED_UUID, "Tool modifier", -3.2D, AttributeModifier.Operation.ADDITION));
        this.defaultModifiers = builder.build();
    }

    /**
     * 方块实体 → 物品：把存储数据塞进物品 NBT。
     */
    public static ItemStack tileEntityToItemStack(TileEntityMaidBeacon beacon) {
        ItemStack stack = InitItems.MAID_BEACON.getDefaultInstance();
        CompoundTag stackTag = stack.getOrCreateTag();
        stackTag.put(STORAGE_DATA_TAG, beacon.saveToItemTag());
        return stack;
    }

    /**
     * 物品 → 方块实体：把存储数据读回方块实体。
     */
    public static void itemStackToTileEntity(ItemStack stack, TileEntityMaidBeacon beacon) {
        CompoundTag tag = stack.getOrCreateTagElement(STORAGE_DATA_TAG);
        if (tag.contains(TileEntityMaidBeacon.FORGE_DATA_TAG, Tag.TAG_COMPOUND)) {
            beacon.loadData(tag.getCompound(TileEntityMaidBeacon.FORGE_DATA_TAG));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> tooltip, TooltipFlag flagIn) {
        float numPower = 0F;
        CompoundTag tag = stack.getOrCreateTagElement(STORAGE_DATA_TAG);
        if (tag.contains(TileEntityMaidBeacon.FORGE_DATA_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag forgeTag = tag.getCompound(TileEntityMaidBeacon.FORGE_DATA_TAG);
            if (forgeTag.contains(TileEntityMaidBeacon.STORAGE_POWER_TAG, Tag.TAG_FLOAT)) {
                numPower = forgeTag.getFloat(TileEntityMaidBeacon.STORAGE_POWER_TAG);
            }
        }
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.maid_beacon.desc",
                DECIMAL_FORMAT.format(numPower)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.defaultModifiers : super.getDefaultAttributeModifiers(slot);
    }
}

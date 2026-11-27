package com.github.tartaricacid.touhoulittlemaid.compat.jade.provider;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemMaidBeacon;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 神社灯（女仆信标方块）的 Jade 提示行：已存电力 + 当前效果每秒消耗。
 *
 * <p>文案键 {@code tooltips.touhou_little_maid.maid_beacon.desc} 与
 * {@code gui.touhou_little_maid.maid_beacon.cost_power} 各 lang json 里本来就有，
 * {@code DECIMAL_FORMAT} 与物品栏里显示的数字格式一致（两位小数）。</p>
 *
 * <p>数据来源是方块实体的同步字段，客户端可直接读，与上游逐字相同；
 * {@code potionIndex == -1} 表示没装药水效果，此时消耗按 0 显示。</p>
 */
public enum ShrineLampProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = TouhouLittleMaid.rl("shrine_lamp");

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        if (blockAccessor.getBlockEntity() instanceof TileEntityMaidBeacon lamp) {
            float storagePower = lamp.getStoragePower();
            float costPower = lamp.getEffectCost() * 900;
            iTooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.maid_beacon.desc",
                    ItemMaidBeacon.DECIMAL_FORMAT.format(storagePower)));
            if (lamp.getPotionIndex() == -1) {
                iTooltip.add(new TranslatableComponent("gui.touhou_little_maid.maid_beacon.cost_power",
                        ItemMaidBeacon.DECIMAL_FORMAT.format(0)));
            } else {
                iTooltip.add(new TranslatableComponent("gui.touhou_little_maid.maid_beacon.cost_power",
                        ItemMaidBeacon.DECIMAL_FORMAT.format(costPower)));
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}


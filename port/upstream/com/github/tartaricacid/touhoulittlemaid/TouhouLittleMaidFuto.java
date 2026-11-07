package com.github.tartaricacid.touhoulittlemaid;

import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.fabricmc.api.ModInitializer;
import net.minecraftforge.fml.FutoEventBuses;

/**
 * Touhou Little Maid: Futo —— Fabric 1.18.2 移植版主入口。
 */
public final class TouhouLittleMaidFuto implements ModInitializer {
    @Override
    public void onInitialize() {
        // 占位：注册流程将在内容移植完成后接上
        InitEntities.ENTITY_TYPES.register(FutoEventBuses.mod());
        InitEntities.MEMORY_MODULE_TYPES.register(FutoEventBuses.mod());
        InitEntities.SENSOR_TYPES.register(FutoEventBuses.mod());
        InitEntities.SCHEDULES.register(FutoEventBuses.mod());
        InitEntities.DATA_SERIALIZERS.register(FutoEventBuses.mod());
        InitEntities.ACTIVITIES.register(FutoEventBuses.mod());
    }
}

package net.minecraftforge.fml;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.impl.SimpleEventBus;

public final class FutoEventBuses {
    public static final SimpleEventBus MOD_BUS = new SimpleEventBus("mod");

    private FutoEventBuses() {
    }

    public static IEventBus mod() {
        return MOD_BUS;
    }
}


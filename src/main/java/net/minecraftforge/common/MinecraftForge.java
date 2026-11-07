package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.impl.SimpleEventBus;

public final class MinecraftForge {
    public static final SimpleEventBus EVENT_BUS = new SimpleEventBus("forge");

    private MinecraftForge() {
    }

    public static IEventBus bus() {
        return EVENT_BUS;
    }
}


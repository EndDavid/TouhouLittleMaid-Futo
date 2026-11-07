package net.minecraftforge.eventbus.api;

import java.util.function.Consumer;

public interface IEventBus {
    void register(Object target);

    <T> void addListener(EventPriority priority, Class<T> eventType, Consumer<T> consumer);

    <T> void addListener(Class<T> eventType, Consumer<T> consumer);

    void unregister(Object target);

    <T extends Event> T post(T event);

    default boolean hasGenericListener(String name) {
        return false;
    }
}


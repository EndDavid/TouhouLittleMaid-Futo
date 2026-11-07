package net.minecraftforge.eventbus.impl;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 极简事件总线：支持 {@code @SubscribeEvent} 反射注册、优先级排序与 cancel 过滤。
 */
public class SimpleEventBus implements IEventBus {
    private static final Logger LOGGER = LogManager.getLogger("touhou_little_maid/futo-eventbus");

    private final String name;
    private final Map<Class<?>, List<Listener>> listeners = new ConcurrentHashMap<>();

    public SimpleEventBus(String name) {
        this.name = name;
    }

    private static final class Listener {
        final Object target;
        final Method method;
        final EventPriority priority;
        final boolean receiveCanceled;
        final Consumer<Object> consumer;

        Listener(Object target, Method method, EventPriority priority, boolean receiveCanceled, Consumer<Object> consumer) {
            this.target = target;
            this.method = method;
            this.priority = priority;
            this.receiveCanceled = receiveCanceled;
            this.consumer = consumer;
        }

        void call(Event event) throws Exception {
            if (consumer != null) {
                consumer.accept(event);
            } else {
                method.invoke(target, event);
            }
        }
    }

    @Override
    public void register(Object target) {
        Class<?> clazz = target.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);
            if (annotation == null || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> eventType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(eventType)) {
                continue;
            }
            method.setAccessible(true);
            Object owner = Modifier.isStatic(method.getModifiers()) ? null : target;
            add(eventType, new Listener(owner, method, annotation.priority(), annotation.receiveCanceled(), null));
        }
    }

    private void add(Class<?> eventType, Listener listener) {
        List<Listener> list = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        list.sort(Comparator.comparingInt(l -> l.priority.ordinal()));
    }

    @Override
    public <T> void addListener(EventPriority priority, Class<T> eventType, Consumer<T> consumer) {
        add(eventType, new Listener(null, null, priority, false, e -> consumer.accept(eventType.cast(e))));
    }

    @Override
    public <T> void addListener(Class<T> eventType, Consumer<T> consumer) {
        addListener(EventPriority.NORMAL, eventType, consumer);
    }

    @Override
    public void unregister(Object target) {
        for (List<Listener> list : listeners.values()) {
            list.removeIf(l -> l.target == target);
        }
    }

    @Override
    public <T extends Event> T post(T event) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> current = event.getClass(); current != null && Event.class.isAssignableFrom(current); current = current.getSuperclass()) {
            chain.add(current);
        }
        for (Class<?> current : chain) {
            List<Listener> list = listeners.get(current);
            if (list == null) {
                continue;
            }
            for (Listener listener : new ArrayList<>(list)) {
                if (event.isCanceled() && !listener.receiveCanceled) {
                    continue;
                }
                try {
                    listener.call(event);
                } catch (InvocationTargetException e) {
                    LOGGER.error("[{}] error while handling {}", name, listener.method, e.getCause());
                } catch (Throwable e) {
                    LOGGER.error("[{}] error while handling {}", name, listener.method, e);
                }
            }
        }
        return event;
    }
}

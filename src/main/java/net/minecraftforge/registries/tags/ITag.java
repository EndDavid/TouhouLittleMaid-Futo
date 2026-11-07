package net.minecraftforge.registries.tags;

public interface ITag<T> extends Iterable<T> {
    boolean isEmpty();

    boolean contains(T value);

    java.util.stream.Stream<T> stream();
}


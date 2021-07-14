package me.qoomon.gitversioning.commons;


import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public final class Lazy<T> implements Supplier<T> {

    private volatile Callable<T> initializer;
    private volatile T value;

    public Lazy(Callable<T> initializer) {
        this.initializer = requireNonNull(initializer);
    }

    public T get() {
        if (initializer != null) {
            synchronized (this) {
                if (initializer != null) {
                    try {
                        value = initializer.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    initializer = null;
                }
            }
        }
        return value;
    }

    public static <T> Lazy<T> of(T value) {
        return new Lazy<>(() -> value);
    }

    public static <T> Lazy<T> by(Callable<T> supplier) {
        return new Lazy<>(supplier);
    }

    public static <V> V get(Lazy<V> value) {
        if (value != null) {
            return value.get();
        }
        return null;
    }
}
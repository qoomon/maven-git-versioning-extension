package me.qoomon.gitversioning.commons;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LazyTest {

    @Test
    void getValueBySupplier() {
        // GIVEN
        Lazy<String> lazyValue = Lazy.by(() -> "foo");

        // WHEN
        String value = lazyValue.get();

        // THEN
        assertThat(value).isEqualTo("foo");
    }

    @Test
    void getValueBySupplierOnlyCallOnce() {
        // GIVEN
        AtomicInteger count = new AtomicInteger();
        Lazy<Integer> lazyValue = Lazy.by(count::incrementAndGet);

        // WHEN
        lazyValue.get();
        lazyValue.get();
        Integer value = lazyValue.get();

        // THEN
        assertThat(value).isEqualTo(1);
        assertThat(count.get()).isEqualTo(1);
    }
}
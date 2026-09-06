package com.uxplima.uxmessentials.survival.adapter.outbound;

import java.util.concurrent.ThreadLocalRandom;

import com.uxplima.uxmessentials.survival.application.port.RandomSource;
import org.jspecify.annotations.NullMarked;

/**
 * The production {@link RandomSource}: a bounded draw from {@link ThreadLocalRandom}. Keeping randomness behind the
 * port leaves the head-drop roll ({@link com.uxplima.uxmessentials.survival.domain.DropChance}) deterministic under
 * test, where a fixed draw is injected, while production picks an unbiased index per call. Entity deaths dispatch on
 * their own region thread, so a thread-local generator avoids the contention a single shared {@code Random} would add.
 */
@NullMarked
public final class ThreadLocalRandomSource implements RandomSource {

    @Override
    public int nextBounded(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return ThreadLocalRandom.current().nextInt(bound);
    }
}

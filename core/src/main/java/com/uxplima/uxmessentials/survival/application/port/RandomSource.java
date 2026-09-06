package com.uxplima.uxmessentials.survival.application.port;

/**
 * The bounded random source the head-drop roll draws through. Keeping randomness behind a port leaves the drop
 * decision (the pure {@link com.uxplima.uxmessentials.survival.domain.DropChance}) deterministic under test: a test
 * injects a fixed draw to assert the drop / no-drop boundary, while the adapter binds a
 * {@code java.util.random.RandomGenerator}. The single method matches the shape the chance expects, a value in
 * {@code [0, bound)} for a positive {@code bound}.
 */
@FunctionalInterface
public interface RandomSource {

    /** A pseudo-random {@code int} in {@code [0, bound)}; {@code bound} must be positive. */
    int nextBounded(int bound);
}
